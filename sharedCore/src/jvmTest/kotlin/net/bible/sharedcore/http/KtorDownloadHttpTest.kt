package net.bible.sharedcore.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders as KtorHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KtorDownloadHttpTest {
    private val methods = mutableListOf<HttpMethod>()
    private val timeouts = mutableListOf<Triple<Long?, Long?, Long?>>()
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun closeClients() {
        clients.forEach(HttpClient::close)
    }

    private fun http(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): KtorDownloadHttp {
        val engine = MockEngine { request ->
            methods += request.method
            request.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeouts += Triple(it.connectTimeoutMillis, it.socketTimeoutMillis, it.requestTimeoutMillis) }
            handler(request)
        }
        val client = HttpClient(engine) { downloadClientDefaults(userAgent = null) }
        clients += client
        return KtorDownloadHttp(client) { 5_000 }
    }

    @Test fun headersUseGetAndParseLastModifiedAndLength() = runBlocking {
        val h = http { respond(ByteArray(10), HttpStatusCode.OK, headersOf(KtorHeaders.LastModified to listOf("Sun, 13 Sep 2020 12:26:40 GMT"), KtorHeaders.ContentLength to listOf("10"))) }
            .headers("https://example.org/a")
        assertEquals(HttpHeaders(200, 10, 1_600_000_000_000L, null), h)
        assertEquals(listOf(HttpMethod.Get), methods)
    }

    @Test fun missingOrBadHeadersFollowUrlConnectionDefaults() = runBlocking {
        val h = http { respond(ByteReadChannel(ByteArray(3)), HttpStatusCode.OK, headersOf(KtorHeaders.LastModified, "garbage")) }
            .headers("https://example.org/a")
        assertEquals(-1L, h.contentLength)
        assertEquals(0L, h.lastModified)
    }

    @Test fun headIsARealHeadAndCarriesTheReasonForNon200() = runBlocking {
        val h = http { respond(ByteArray(0), HttpStatusCode.NotFound) }.head("https://example.org/a")
        assertEquals(404, h.status)
        assertEquals("Not Found", h.reason)
        assertEquals(listOf(HttpMethod.Head), methods)
    }

    @Test fun idleTimeoutsArePerRequestWithoutATotalTimeout() = runBlocking {
        http { respond(ByteArray(0)) }.headers("https://example.org/a")
        assertEquals(listOf(Triple<Long?, Long?, Long?>(5_000L, 5_000L, null)), timeouts)
    }

    @Test fun openStreamsTheBody() = runBlocking {
        val payload = ByteArray(20_000) { it.toByte() }
        val read = http { respond(payload) }.open("https://example.org/a") { r ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        assertContentEquals(payload, read)
    }

    @Test fun textReturnsBodyOnlyFor200() = runBlocking {
        assertEquals(HttpTextResult(200, "ä"), http { respond("ä".encodeToByteArray()) }.text("https://example.org/a"))
        assertEquals(HttpTextResult(500, null), http { respond("x", HttpStatusCode.InternalServerError) }.text("https://example.org/a"))
    }

    @Test fun nonHttpsIsRejectedBeforeAnyRequest() = runBlocking {
        assertFailsWith<InsecureUrlException> { http { respond("") }.headers("http://example.org/a") }
        assertTrue(methods.isEmpty())
    }

    @Test fun httpsToHttpRedirectIsRejected() = runBlocking {
        val error = assertFailsWith<InsecureUrlException> {
            http { respond("", HttpStatusCode.Found, headersOf(KtorHeaders.Location, "http://example.org/b")) }.headers("https://example.org/a")
        }
        assertEquals("http://example.org/b", error.url)
    }

    @Test fun socketTimeoutBecomesDownloadTimeout() { runBlocking {
        assertFailsWith<DownloadTimeoutException> { http { throw SocketTimeoutException("idle") }.headers("https://example.org/a") }
    } }

    @Test fun otherEngineFailuresBecomeTransportExceptions() = runBlocking {
        val error = assertFailsWith<DownloadTransportException> { http { throw java.net.ConnectException("refused") }.headers("https://example.org/a") }
        assertTrue(error.cause is java.net.ConnectException)
    }

    @Test fun exceptionsFromTheCallerBlockPassThroughUnchanged() { runBlocking {
        class Mine : RuntimeException()
        assertFailsWith<Mine> { http { respond("x") }.open("https://example.org/a") { throw Mine() } }
    } }

    @Test fun callerCancellationExceptionWithCausePassesThroughUnchanged() { runBlocking {
        val cause = IllegalStateException("caller failure")
        val cancellation = CancellationException("caller cancelled", cause)
        val thrown = assertFailsWith<CancellationException> {
            http { respond("x") }.open("https://example.org/a") { throw cancellation }
        }
        assertSame(cancellation, thrown)
    } }

    @Test fun causedCancellationBeforeTextHeadersRemainsCancellation() = runBlocking {
        assertCancellationBeforeHeaders { it.text("https://example.org/a") }
    }

    @Test fun causedCancellationBeforeHeadHeadersRemainsCancellation() = runBlocking {
        assertCancellationBeforeHeaders { it.head("https://example.org/a") }
    }

    @Test fun causedCancellationBeforeGetHeadersRemainsCancellation() = runBlocking {
        assertCancellationBeforeHeaders { it.headers("https://example.org/a") }
    }

    @Test fun causedCancellationBeforeOpenBlockRemainsCancellation() = runBlocking {
        var blockEntered = false
        assertCancellationBeforeHeaders {
            it.open("https://example.org/a") { blockEntered = true }
        }
        assertFalse(blockEntered)
    }

    @Test fun cancellationWithTimeoutCauseBeforeHeadersIsNotAnIdleTimeout() = runBlocking {
        assertCancellationBeforeHeaders(SocketTimeoutException("cancellation cause, not idle timeout")) {
            it.text("https://example.org/a")
        }
    }

    private suspend fun assertCancellationBeforeHeaders(
        cause: Throwable = java.io.IOException("external cancellation cause"),
        request: suspend (DownloadHttp) -> Unit
    ) = coroutineScope {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val caught = CompletableDeferred<Throwable?>()
            val transport = http {
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped.complete(Unit) }
            }
            val cancellation = CancellationException("external cancellation", cause)
            val call = launch {
                // Capture at the port boundary: await/join on a cancelled job can mask a wrongly mapped exception.
                caught.complete(try { request(transport); null } catch (e: Throwable) { e })
            }
            try {
                started.await()
                call.cancel(cancellation)
                val thrown = caught.await()
                stopped.await()
                call.join()
                assertTrue(thrown is CancellationException, "Expected cancellation inside request, got $thrown")
                assertSame(cancellation, thrown)
            } finally {
                call.cancelAndJoin()
            }
        }
    }

    @Test fun coroutineCancellationPropagatesAndReleasesRequest() { runBlocking {
        val requestStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val requestStopped = kotlinx.coroutines.CompletableDeferred<Unit>()
        val call = async {
            http {
                requestStarted.complete(Unit)
                try { awaitCancellation() } finally { requestStopped.complete(Unit) }
            }.open("https://example.org/a") { awaitCancellation() }
        }
        requestStarted.await()
        call.cancel()
        assertFailsWith<CancellationException> { call.await() }
        requestStopped.await()
    } }
}
