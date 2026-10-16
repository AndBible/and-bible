package net.bible.service.cloudsync.webdav

import io.ktor.client.engine.okhttp.OkHttp
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

fun X509Certificate.toCertInfo(host: String) = DavCertificateInfo(
    host = host, sha256 = sha256Hex(encoded),
    subject = subjectX500Principal.name, issuer = issuerX500Principal.name,
    notBefore = notBefore.time, notAfter = notAfter.time,
)

fun defaultTrustManager(): X509TrustManager =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers.filterIsInstance<X509TrustManager>().first()

/**
 * Trust-on-first-use for the WebDAV client only. Normal platform validation first; if that fails,
 * the leaf certificate must match the user's pin for exactly this host. Otherwise a
 * CertificateException is thrown whose cause is a [DavUntrustedCertificateException] carrying what
 * the trust dialog shows; WebDavClient digs it out of the SSLHandshakeException.
 */
class PinnedTrustManager(
    private val host: String,
    private val pin: () -> CertPin?,
    private val platform: X509TrustManager = defaultTrustManager(),
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        platform.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try {
            platform.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            val leaf = chain.first()
            val p = pin()
            if (p != null && p.host == host && p.sha256 == sha256Hex(leaf.encoded)) return
            throw CertificateException("Untrusted certificate for $host", DavUntrustedCertificateException(leaf.toCertInfo(host)))
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers
}

/**
 * Hostname check that also accepts a pinned leaf (a self-signed cert issued for another name, or a
 * server reached by IP). A rejection is remembered in [lastRejected] so the client can turn
 * OkHttp's SSLPeerUnverifiedException into a trust prompt instead of a silent transient error.
 */
class PinnedHostnameVerifier(
    private val host: String,
    private val pin: () -> CertPin?,
    private val default: HostnameVerifier = OkHostnameVerifier,
) : HostnameVerifier {
    @Volatile var lastRejected: DavCertificateInfo? = null
        private set

    override fun verify(hostname: String, session: SSLSession): Boolean {
        if (default.verify(hostname, session)) return true
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
        val p = pin()
        if (p != null && p.host == host && p.sha256 == sha256Hex(leaf.encoded)) return true
        lastRejected = leaf.toCertInfo(host)
        return false
    }
}

/** The production WebDavClient: OkHttp engine with pinned trust for [WebDavConfig.host] only. */
fun createWebDavClient(config: WebDavConfig, state: WebDavStateStore, nowMs: () -> Long = System::currentTimeMillis): WebDavClient {
    val trust = PinnedTrustManager(config.host, { state.certPin })
    val verifier = PinnedHostnameVerifier(config.host, { state.certPin })
    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    val engine = OkHttp.create {
        config {
            sslSocketFactory(ssl.socketFactory, trust)
            hostnameVerifier(verifier)
        }
    }
    return WebDavClient(
        createWebDavHttpClient(engine, config.username, config.password),
        config.serverUrl, config.username, config.password, nowMs,
        mapPlatformError = { e ->
            val all = e.causesAndSuppressed().toList()
            all.filterIsInstance<DavUntrustedCertificateException>().firstOrNull()
                ?: if (all.any { it is SSLPeerUnverifiedException }) verifier.lastRejected?.let { DavUntrustedCertificateException(it) } else null
        },
    )
}

/**
 * This throwable, its causes and its suppressed exceptions. OkHttp tries every address of a host and
 * throws the first failure with the rest suppressed, so a TLS rejection on the second address (say
 * IPv4 after an unreachable IPv6) is only visible there.
 */
private fun Throwable.causesAndSuppressed(): Sequence<Throwable> = sequence {
    val seen = HashSet<Throwable>()
    val stack = ArrayDeque<Throwable>().apply { add(this@causesAndSuppressed) }
    while (stack.isNotEmpty()) {
        val t = stack.removeLast()
        if (!seen.add(t)) continue
        yield(t)
        t.cause?.let(stack::add)
        stack.addAll(t.suppressed)
    }
}
