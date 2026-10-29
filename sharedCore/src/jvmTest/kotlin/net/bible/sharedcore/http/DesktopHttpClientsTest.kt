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

import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.llm.http.LlmTimeouts
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopHttpClientsTest {
    private fun userAgentsAfter(path: String, call: suspend (String) -> HttpResponse): List<String?> {
        val seen = ConcurrentLinkedQueue<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { ex ->
            seen += ex.requestHeaders.getFirst("User-Agent")
            val body = "hi".toByteArray(); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.createContext("/redirect") { ex -> ex.responseHeaders.add("Location", "/ok"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.start()
        try {
            val response = runBlocking { call("http://127.0.0.1:${server.address.port}$path") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("hi", runBlocking { response.bodyAsText() })
        } finally { server.stop(0) }
        return seen.toList()
    }

    @Test fun downloadClientSendsNoUserAgentNamingAndBibleAndFollowsRedirects() {
        val clients = DesktopHttpClients()
        val agents = userAgentsAfter("/redirect") { clients.downloadClient.get(it) }
        assertEquals(1, agents.size)
        assertTrue(agents.none { it?.contains("AndBible", ignoreCase = true) == true }, agents.toString())
    }

    @Test fun llmClientSendsNoUserAgentNamingAndBible() {
        val agents = userAgentsAfter("/ok") { DesktopHttpClients().clientFor(LlmTimeouts.MODELS).get(it) }
        assertTrue(agents.none { it?.contains("AndBible", ignoreCase = true) == true }, agents.toString())
    }

    @Test fun llmClientsAreCachedPerTimeouts() {
        val clients = DesktopHttpClients()
        assertSame(clients.clientFor(LlmTimeouts.CHAT), clients.clientFor(LlmTimeouts(120_000, 90_000, 120_000)))
        assertNotSame(clients.clientFor(LlmTimeouts.CHAT), clients.clientFor(LlmTimeouts.MODELS))
    }
}
