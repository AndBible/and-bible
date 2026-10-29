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
package net.bible.sharedcore.platform.desktop.sync

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.internal.tls.OkHostnameVerifier
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * The desktop OkHttp engine with caller-supplied TLS trust. It lives in :sharedCore because only that module
 * has the OkHttp engine on its compile classpath; the trust policy itself stays in :sharedLogic.
 */
fun desktopPinnedHttpEngine(
    sslSocketFactory: SSLSocketFactory,
    trustManager: X509TrustManager,
    hostnameVerifier: HostnameVerifier,
): HttpClientEngine = OkHttp.create {
    config {
        sslSocketFactory(sslSocketFactory, trustManager)
        hostnameVerifier(hostnameVerifier)
    }
}

/** OkHttp's standard certificate-name check (SAN/CN, wildcard, IP literals). */
fun desktopDefaultHostnameVerifier(): HostnameVerifier = OkHostnameVerifier
