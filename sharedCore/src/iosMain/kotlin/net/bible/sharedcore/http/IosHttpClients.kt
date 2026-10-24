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
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import net.bible.sharedcore.llm.http.LlmTimeouts

/**
 * The iOS counterpart of Android's `AndroidHttpClients`: ktor clients on the Darwin (NSURLSession) engine for
 * `DownloadHttp` and `LlmHttpTransport` (spec C5).
 *
 * No `User-Agent` header is installed anywhere: the CFNetwork default stays and never names AndBible, so
 * [downloadClientDefaults] is called with `userAgent = null`. Redirects are not followed by the engine; ktor's
 * redirect plugin (switched on by [downloadClientDefaults], as on Android) follows them so the https-only check in
 * `KtorDownloadHttp` sees every hop. Runtime behaviour is verified in the Mac round (the engine cannot run on Linux).
 *
 * LLM clients are cached per [LlmTimeouts] and match okhttp's semantics on Android: `connectMs` is the connect timeout,
 * `readMs` the idle socket timeout, and `writeMs` is not mapped. ktor's `requestTimeoutMillis` caps the whole call, which
 * okhttp has no equivalent of (its write timeout is per write), so it stays unset: a long non-streamed completion is
 * limited only by the idle read timeout, as on Android. See [applyLlmTimeouts].
 */
class IosHttpClients {
    /** Downloads: ktor [downloadClientDefaults] with no user agent. */
    val downloadClient: HttpClient by lazy {
        HttpClient(Darwin) { downloadClientDefaults(userAgent = null) }
    }

    private val lock = kotlinx.atomicfu.locks.SynchronizedObject()
    private val clients = mutableMapOf<LlmTimeouts, HttpClient>()

    /** The cached client for [t]; equal timeouts share one client. */
    fun clientFor(t: LlmTimeouts): HttpClient = kotlinx.atomicfu.locks.synchronized(lock) {
        clients.getOrPut(t) {
            HttpClient(Darwin) {
                install(HttpTimeout) { applyLlmTimeouts(t) }
            }
        }
    }
}

/** The one place [LlmTimeouts] become ktor timeouts: connect and idle read only, no whole-call cap (see [IosHttpClients]). */
internal fun HttpTimeoutConfig.applyLlmTimeouts(t: LlmTimeouts) {
    connectTimeoutMillis = t.connectMs
    socketTimeoutMillis = t.readMs
    requestTimeoutMillis = null
}
