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
package net.bible.sharedcore.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.bible.sharedcore.llm.http.LlmTimeouts
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * The desktop counterpart of Android's `AndroidHttpClients`: ktor clients on the OkHttp engine for `DownloadHttp` and
 * `LlmHttpTransport`.
 *
 * Like iOS, no `User-Agent` naming AndBible is installed ([downloadClientDefaults] gets `userAgent = null`). The engine
 * does not follow redirects, as on Android; ktor's redirect plugin does, so the https-only check in `KtorDownloadHttp`
 * sees every hop. LLM clients are cached per [LlmTimeouts] and use OkHttp's connect, read and write timeouts.
 */
class DesktopHttpClients {
    /** Downloads: ktor [downloadClientDefaults] with no user agent. */
    val downloadClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine { config { followRedirects(false); followSslRedirects(false) } }
            downloadClientDefaults(userAgent = null)
        }
    }

    private val clients = mutableMapOf<LlmTimeouts, HttpClient>()

    /** The cached client for [t]; equal timeouts share one client. */
    @Synchronized
    fun clientFor(t: LlmTimeouts): HttpClient = clients.getOrPut(t) {
        HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    t.connectMs?.let { connectTimeout(it, MILLISECONDS) }
                    t.readMs?.let { readTimeout(it, MILLISECONDS) }
                    t.writeMs?.let { writeTimeout(it, MILLISECONDS) }
                }
            }
        }
    }
}
