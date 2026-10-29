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

import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.http.LocalNetworkHost
import net.bible.sharedcore.platform.desktop.sync.desktopDefaultHostnameVerifier
import net.bible.sharedcore.platform.desktop.sync.desktopPinnedHttpEngine
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import java.net.URI
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.X509TrustManager

/**
 * True when [url] may be used: anything but plain http, or plain http to a local host (fixtures, LAN).
 * The same rule as the iOS settings platform; the desktop SyncSettingsPlatform calls it.
 */
fun desktopPlainHttpAllowed(url: String): Boolean {
    val uri = try { URI(url.trim()) } catch (_: Exception) { return false }
    if (!"http".equals(uri.scheme, ignoreCase = true)) return true
    return LocalNetworkHost.isLocal(uri.host ?: return false)
}

/**
 * Builds the desktop [WebDavClient]: OkHttp engine with pinned trust for [WebDavConfig.host] only (spec C5,
 * same policy as the iOS client through the shared pinDecision). [platformTrust] is the system trust store
 * unless a test injects another.
 */
class DesktopWebDavClientFactory(
    private val platformTrust: () -> X509TrustManager = ::systemTrustManager,
    private val defaultVerifier: () -> HostnameVerifier = ::desktopDefaultHostnameVerifier,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun create(config: WebDavConfig, state: WebDavStateStore): WebDavClient {
        val trust = PinningTrustManager(config.host, { state.certPin }, platformTrust())
        val verifier = PinningHostnameVerifier(config.host, { state.certPin }, defaultVerifier())
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val engine = desktopPinnedHttpEngine(ssl.socketFactory, trust, verifier)
        return WebDavClient(
            createWebDavHttpClient(engine, config.username, config.password),
            config.serverUrl, config.username, config.password, nowMs,
            mapPlatformError = { e ->
                val all = e.causesAndSuppressed().toList()
                all.filterIsInstance<DavUntrustedCertificateException>().firstOrNull()
                    ?: if (all.any { it is SSLPeerUnverifiedException }) verifier.takeRejected()?.let(::DavUntrustedCertificateException) else null
            },
        )
    }
}

/** This throwable, its causes and suppressed exceptions (OkHttp hides a TLS rejection on a later address there). */
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
