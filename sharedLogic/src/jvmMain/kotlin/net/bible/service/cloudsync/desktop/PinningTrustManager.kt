/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.cloudsync.desktop

import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.PinDecision
import net.bible.service.cloudsync.webdav.pinDecision
import net.bible.service.cloudsync.webdav.sha256HexUpper
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** The JVM's system trust store (the default for [PinningTrustManager]). */
fun systemTrustManager(): X509TrustManager =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers.filterIsInstance<X509TrustManager>().first()

internal fun X509Certificate.toDavCertificateInfo(host: String) = DavCertificateInfo(
    host = host,
    sha256 = sha256HexUpper(encoded),
    subject = subjectX500Principal.name,
    issuer = issuerX500Principal.name,
    notBefore = notBefore.time,
    notAfter = notAfter.time,
)

/**
 * Trust-on-first-use for the WebDAV client of one [host]: system trust first, otherwise the shared
 * [pinDecision] (a pin applies only to the host it was taken on). A rejection throws a
 * [CertificateException] whose cause is a [DavUntrustedCertificateException]; the JVM wraps it in an
 * SSLHandshakeException and the client digs it out of the cause chain.
 */
class PinningTrustManager(
    private val host: String,
    private val pin: () -> CertPin?,
    private val platform: X509TrustManager = systemTrustManager(),
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        platform.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val systemTrusted = try {
            platform.checkServerTrusted(chain, authType); true
        } catch (_: CertificateException) { false }
        val leaf = chain.firstOrNull() ?: throw CertificateException("Empty certificate chain for $host")
        if (pinDecision(systemTrusted, host, sha256HexUpper(leaf.encoded), pin()) == PinDecision.REJECT) {
            throw CertificateException("Untrusted certificate for $host", DavUntrustedCertificateException(leaf.toDavCertificateInfo(host)))
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers
}

/**
 * Hostname check that also accepts a pinned leaf (a self-signed certificate issued for another name, or a
 * server reached by IP). A rejection is remembered so the client can turn the resulting
 * SSLPeerUnverifiedException into a trust prompt instead of a silent transient error.
 */
class PinningHostnameVerifier(
    private val host: String,
    private val pin: () -> CertPin?,
    private val default: HostnameVerifier,
) : HostnameVerifier {
    @Volatile var lastRejected: DavCertificateInfo? = null
        private set

    fun takeRejected(): DavCertificateInfo? = lastRejected.also { lastRejected = null }

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (default.verify(hostname, session)) return true
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
        if (pinDecision(false, host, sha256HexUpper(leaf.encoded), pin()) == PinDecision.TRUST_PINNED) return true
        lastRejected = leaf.toDavCertificateInfo(host)
        return false
    }
}
