package net.bible.service.download.http

import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.DownloadTimeoutException
import net.bible.sharedcore.http.DownloadTransportException
import net.bible.sharedcore.http.InsecureUrlException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class DownloadHttpContract {
    abstract val legacy: Boolean
    abstract fun http(server: TestHttpsServer, timeoutMs: Int = 5_000): DownloadHttp
    open fun <T> withTrust(server: TestHttpsServer, block: () -> T): T = block()

    private fun <T> scenario(block: (TestHttpsServer) -> T): T = TestHttpsServer().use { withTrust(it) { block(it) } }

    @Test fun headersReportStatusLengthAndLastModifiedWithGet() = scenario { server ->
        server.route("/m") { it.reply(200, ByteArray(10), mapOf("Last-Modified" to "Sun, 13 Sep 2020 12:26:40 GMT")) }
        val h = runBlocking { http(server).headers("${server.base}/m") }
        assertEquals(200, h.status); assertEquals(10L, h.contentLength); assertEquals(1_600_000_000_000L, h.lastModified)
        assertEquals("GET", server.requests.single().method)
    }

    @Test fun headersWithoutLastModifiedAreZero() = scenario { server ->
        server.route("/m") { it.reply(200, ByteArray(10)) }
        assertEquals(0L, runBlocking { http(server).headers("${server.base}/m") }.lastModified)
    }

    @Test fun refusedConnectionThrowsTransportException() = scenario { server ->
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        val error = runCatching { runBlocking { http(server).headers("https://127.0.0.1:$closedPort/m") } }.exceptionOrNull()
        assertTrue("got $error", error is DownloadTransportException)
    }

    @Test fun textReturnsBodyOnlyFor200() = scenario { server ->
        server.route("/ok") { it.reply(200, "{\"a\":\"ä\"}".toByteArray()) }
        server.route("/no") { it.reply(404, "nope".toByteArray()) }
        val ok = runBlocking { http(server).text("${server.base}/ok") }
        val no = runBlocking { http(server).text("${server.base}/no") }
        assertEquals(200, ok.status); assertEquals("{\"a\":\"ä\"}", ok.body)
        assertEquals(404, no.status); assertNull(no.body)
    }

    /** Spec §5.2 item 3: legacy has no timeout and hangs; ktor fails within the idle timeout. */
    @Test fun stalledTextFetchTimesOut() = scenario { server ->
        val release = CountDownLatch(1)
        server.route("/stall") { ex -> ex.sendResponseHeaders(200, 10); ex.responseBody.flush(); release.await(10, TimeUnit.SECONDS) }
        var outcome: Throwable? = null
        val worker = Thread { outcome = runCatching { runBlocking { http(server, timeoutMs = 300).text("${server.base}/stall") } }.exceptionOrNull() }
        worker.start()
        try {
            worker.join(2_000)
            if (legacy) assertTrue("legacy has no timeout and is still waiting", worker.isAlive)
            else { assertTrue("must finish", !worker.isAlive); assertTrue("got $outcome", outcome is DownloadTimeoutException) }
        } finally {
            release.countDown()
            worker.join(5_000)
            assertTrue("text-fetch thread must be cleaned up", !worker.isAlive)
        }
    }

    /** Spec §5.2 item 1. */
    @Test fun plainHttpUrl() = scenario { server ->
        val error = runCatching { runBlocking { http(server).headers("http://127.0.0.1:1/m") } }.exceptionOrNull()
        if (legacy) assertTrue("legacy casts: $error", error is ClassCastException)
        else assertTrue("got $error", error is InsecureUrlException)
    }
}
