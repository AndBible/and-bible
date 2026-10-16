package net.bible.android.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.DownloadResponse
import net.bible.sharedcore.http.DownloadTimeoutException
import net.bible.sharedcore.http.HttpHeaders
import net.bible.sharedcore.http.HttpTextResult
import org.crosswire.common.util.HttpBody
import org.crosswire.common.util.TransportCancelledException
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class SuspendHttpTransportTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bodies = mutableListOf<HttpBody>()
    private val uri = URI("https://x/a")

    private open class FakeHttp(
        val metadata: HttpHeaders = HttpHeaders(200, 10, 5, null),
        val chunks: Int = 1,
        val hang: Boolean = false,
        val failure: Throwable? = null,
        val hangMetadata: Boolean = false,
    ) : DownloadHttp {
        val opened = AtomicInteger()
        val produced = AtomicInteger()
        val entered = CountDownLatch(1)
        val readEntered = CountDownLatch(1)
        val finished = CompletableDeferred<Unit>()
        override suspend fun head(url: String) = metadata
        override suspend fun headers(url: String) = metadata
        override suspend fun text(url: String) = HttpTextResult(200, "")
        override suspend fun <T> open(url: String, block: suspend (DownloadResponse) -> T): T {
            opened.incrementAndGet()
            entered.countDown()
            try {
                if (hangMetadata) awaitCancellation()
                return block(object : DownloadResponse {
                    override val headers = metadata
                    private var left = chunks
                    override suspend fun read(buf: ByteArray): Int {
                        readEntered.countDown()
                        if (hang) awaitCancellation()
                        if (left-- == 0) {
                            failure?.let { throw it }
                            return -1
                        }
                        val index = produced.getAndIncrement()
                        buf.fill((index % 251).toByte())
                        return buf.size
                    }
                })
            } finally { finished.complete(Unit) }
        }
    }

    private suspend fun body(http: DownloadHttp): HttpBody =
        SuspendHttpTransport(http, scope).get(uri, 5_000).also { bodies += it }

    private suspend fun finished(http: FakeHttp) = withTimeout(2_000) { http.finished.await() }

    @After fun closeResources() {
        bodies.forEach { it.close() }
        scope.cancel()
        runBlocking { withTimeout(2_000) { scope.coroutineContext[Job]!!.join() } }
    }

    @Test fun getCloseWithoutStartNeverOpensRequest() = runBlocking {
        val fake = FakeHttp()
        val body = body(fake)
        assertEquals(0, fake.opened.get())
        body.close()
        assertEquals(0, fake.opened.get())
        assertTrue(runCatching { body.status() }.exceptionOrNull() is TransportCancelledException)
    }

    @Test fun metadataIsLazyAndProbeCloseDoesNotReadBytes() = runBlocking {
        val fake = FakeHttp()
        val body = body(fake)
        assertEquals(0, fake.opened.get())
        assertEquals(10, body.contentLength())
        assertEquals(200, body.status())
        assertEquals(5L, body.lastModified())
        assertEquals(1, fake.opened.get())
        assertFalse("metadata must not start response.read", fake.readEntered.await(150, TimeUnit.MILLISECONDS))
        body.close()
        finished(fake)
        assertEquals(0, fake.produced.get())
    }

    @Test fun errorStatusFailsPrepareReadAndReleasesWithoutReading() = runBlocking {
        val fake = FakeHttp(HttpHeaders(404, 3, 0, "Not Found"))
        val body = body(fake)
        assertEquals(404, body.status())
        assertTrue(runCatching { body.prepareRead() }.exceptionOrNull() is IOException)
        finished(fake)
        assertEquals(0, fake.produced.get())
    }

    @Test fun largeBodyStreamsWithBoundedMemoryAndPartialReads() = runBlocking {
        val fake = FakeHttp(HttpHeaders(200, -1, 0, null), chunks = 1_000)
        val body = body(fake)
        body.prepareRead()
        assertTrue(fake.readEntered.await(2, TimeUnit.SECONDS))
        Thread.sleep(150)
        assertTrue("producer must stay bounded: ${fake.produced.get()}", fake.produced.get() <= 32)
        val buf = ByteArray(3001)
        var total = 0L
        while (true) {
            val n = body.read(buf)
            if (n < 0) break
            assertTrue(n > 0)
            repeat(n) { i -> assertEquals(((total + i) / 8192 % 251).toByte(), buf[i]) }
            total += n
        }
        assertEquals(8_192_000L, total)
        assertEquals(-1, body.read(buf))
        finished(fake)
    }

    @Test fun earlyCloseReleasesBackpressuredProducer() = runBlocking {
        val fake = FakeHttp(chunks = 1_000)
        val body = body(fake)
        body.prepareRead()
        assertTrue(fake.readEntered.await(2, TimeUnit.SECONDS))
        body.close()
        finished(fake)
        assertTrue(runCatching { body.read(ByteArray(10)) }.exceptionOrNull() is TransportCancelledException)
    }

    @Test fun readStartsStreamWithoutExplicitPrepareRead() = runBlocking {
        val fake = FakeHttp(chunks = 0)
        assertEquals(-1, body(fake).read(ByteArray(1)))
        finished(fake)
    }

    @Test fun emptyBufferDoesNotConsumeBytesOrReturnEof() = runBlocking {
        val fake = FakeHttp()
        val body = body(fake)
        assertEquals(0, body.read(ByteArray(0)))
        assertEquals(10, body.read(ByteArray(10)))
    }

    @Test fun streamFailureAfterBufferedBytesPreservesCause() = runBlocking {
        val failure = IOException("broken stream")
        val fake = FakeHttp(failure = failure)
        val body = body(fake)
        assertEquals(8192, body.read(ByteArray(8192)))
        assertSame(failure, runCatching { body.read(ByteArray(1)) }.exceptionOrNull())
        finished(fake)
    }

    @Test fun streamTimeoutPreservesTheErrorAndLeavesCallerActive() = runBlocking {
        val failure = DownloadTimeoutException(null)
        val fake = FakeHttp(chunks = 0, failure = failure)
        val body = body(fake)
        val error = runCatching { body.read(ByteArray(1)) }.exceptionOrNull()
        assertSame(failure, error)
        assertTrue(isActive)
        finished(fake)
        assertTrue("timeout closes the body", runCatching { body.status() }.exceptionOrNull() is TransportCancelledException)
    }

    @Test fun producerCancellationMapsToTransportCancelled() = runBlocking {
        val failure = CancellationException("cancelled")
        val fake = FakeHttp(chunks = 0, failure = failure)
        val error = runCatching { body(fake).read(ByteArray(1)) }.exceptionOrNull()
        assertTrue("got $error", error is TransportCancelledException)
        assertSame(failure, error!!.cause)
        finished(fake)
    }

    @Test fun cancelledMetadataCallerCancelsProducerBeforeClose() = cancelled(hangMetadata = true)
    @Test fun cancelledReadCallerCancelsProducerBeforeClose() = cancelled(hangMetadata = false)

    /** Cancelling the calling coroutine ends the call with a CancellationException and releases the open block, with no close(). */
    private fun cancelled(hangMetadata: Boolean) = runBlocking {
        val fake = FakeHttp(hang = true, hangMetadata = hangMetadata)
        val body = body(fake)
        if (!hangMetadata) body.prepareRead()
        val result = CompletableDeferred<Throwable?>()
        val caller = launch(Dispatchers.IO) {
            result.complete(runCatching { if (hangMetadata) body.status() else body.read(ByteArray(10)) }.exceptionOrNull())
        }
        withContext(Dispatchers.IO) {
            assertTrue(fake.entered.await(2, TimeUnit.SECONDS))
            if (!hangMetadata) assertTrue(fake.readEntered.await(2, TimeUnit.SECONDS))
        }
        caller.cancel()
        val outcome = withTimeout(1_000) { result.await() }
        assertTrue("got $outcome", outcome is CancellationException)
        assertFalse("a cancelled caller is not a transport cancellation", outcome is TransportCancelledException)
        finished(fake) // No close(): the caller's cancellation itself must release the open block.
    }

    @Test fun cancelledOwnerScopeDoesNotLeaveMetadataWaiting() = runBlocking {
        scope.cancel()
        val error = runCatching { body(FakeHttp()).status() }.exceptionOrNull()
        assertTrue("got $error", error is TransportCancelledException)
    }

    @Test fun headTimeoutPreservesTheErrorAndLeavesCallerActive() = runBlocking {
        val failure = DownloadTimeoutException(IOException("socket timeout"))
        val http = object : FakeHttp() {
            override suspend fun head(url: String): HttpHeaders = throw failure
        }
        assertSame(failure, runCatching { SuspendHttpTransport(http, scope).head(uri, 5_000) }.exceptionOrNull())
        assertTrue(isActive)
    }

    @Test fun headAndBodyLengthFollowUrlConnectionBoundaries() = runBlocking {
        for ((length, expected) in listOf(-2L to -1, -1L to -1, 0L to 0, Int.MAX_VALUE.toLong() to Int.MAX_VALUE, 2_147_483_648L to -1)) {
            val fake = FakeHttp(HttpHeaders(200, length, 123))
            val head = SuspendHttpTransport(fake, scope).head(uri, 5_000)
            assertEquals(expected, head.contentLength)
            assertEquals(123L, head.lastModified)
            assertEquals(expected, body(fake).contentLength())
        }
        val head = SuspendHttpTransport(FakeHttp(HttpHeaders(500, 15, 123, "Error")), scope).head(uri, 5_000)
        assertEquals(500, head.status)
        assertEquals("Error", head.reason)
        assertEquals(0, head.contentLength)
        assertEquals(0L, head.lastModified)
    }
}
