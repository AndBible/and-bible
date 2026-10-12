package net.bible.android.platform

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.llm.http.KtorLlmHttpTransport
import net.bible.sharedcore.llm.http.LlmTimeouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class AndroidLlmHttpClientsTest {
    // Removing engine readTimeout makes this call wait 2s and return rather than fail promptly.
    @Test fun shortReadTimeoutReachesEngine() = withSlowServer { url ->
        val clients = AndroidLlmHttpClients()
        val profile = LlmTimeouts(1_000, 300, null)
        val client = clients.clientFor(profile)
        try {
            val start = System.nanoTime()
            val error = runCatching { runBlocking { KtorLlmHttpTransport { client }.get(url, emptyMap(), profile) } }.exceptionOrNull()
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            assertTrue("Expected timeout, got $error", error is java.net.SocketTimeoutException)
            assertFalse(error is CancellationException)
            assertTrue("Timeout took ${elapsedMs}ms", elapsedMs < 1_500)
        } finally { client.close() }
    }

    // Applying the wrong cached profile (300ms) breaks this request against the same delayed fixture.
    @Test fun longerReadTimeoutAllowsResponse() = withSlowServer { url ->
        val clients = AndroidLlmHttpClients()
        val profile = LlmTimeouts(1_000, 5_000, null)
        val client = clients.clientFor(profile)
        try {
            val response = runBlocking { KtorLlmHttpTransport { client }.get(url, emptyMap(), profile) }
            assertEquals(200, response.code)
            assertEquals("slow response", response.body)
        } finally { client.close() }
    }

    @Test fun clientsAreCachedPerProfile() {
        val clients = AndroidLlmHttpClients()
        val client = clients.clientFor(LlmTimeouts.CHAT)
        try {
            assertSame(client, clients.clientFor(LlmTimeouts(120_000, 90_000, 120_000)))
        } finally { client.close() }
    }

    private fun withSlowServer(test: (String) -> Unit) {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = executor
            createContext("/slow") { exchange ->
                try {
                    Thread.sleep(2_000)
                    val bytes = "slow response".toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (_: java.io.IOException) {
                    // Expected when the short-timeout client closes its connection.
                } finally { exchange.close() }
            }
            start()
        }
        try { test("http://127.0.0.1:${server.address.port}/slow") } finally {
            server.stop(0)
            executor.shutdownNow()
            assertTrue("Fixture executor did not stop", executor.awaitTermination(3, TimeUnit.SECONDS))
        }
    }
}
