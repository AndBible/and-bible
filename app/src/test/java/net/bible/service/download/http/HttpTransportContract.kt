package net.bible.service.download.http

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.crosswire.common.progress.JobManager
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.LucidException
import org.crosswire.common.util.NetUtil
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.install.DownloadException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/** WebResource/NetUtil behaviour through JSword's HttpTransport port; run on legacy and on ktor. */
abstract class HttpTransportContract {
    abstract val legacy: Boolean
    abstract fun transport(server: TestHttpsServer): HttpTransport
    open suspend fun <T> withTrust(server: TestHttpsServer, block: suspend () -> T): T = block()

    protected val body = ByteArray(100_000) { (it % 251).toByte() }
    protected val lastModifiedMillis = 1_600_000_000_000L
    protected val lastModifiedHeader: String = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(lastModifiedMillis)

    /**
     * Runs [block] against a fresh server and the transport under test. The scenarios wait on real sockets and real
     * timeouts, so the body runs on [Dispatchers.IO] inside `runTest` (a test dispatcher would skip virtual time).
     */
    protected fun scenario(timeoutMs: Int = 5_000, block: suspend CoroutineScope.(TestHttpsServer, File) -> Unit): Unit = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.IO) {
            val oldTransport = WebResource.transport
            val oldTimeout = WebResource.getTimeout()
            val dir = kotlin.io.path.createTempDirectory("d3").toFile()
            try {
                TestHttpsServer().use { server ->
                    WebResource.setTimeout(timeoutMs)
                    WebResource.transport = transport(server)
                    withTrust(server) { block(this@withContext, server, dir) }
                }
            } finally {
                WebResource.transport = oldTransport
                WebResource.setTimeout(oldTimeout)
                dir.deleteRecursively()
            }
        }
    }

    protected suspend fun copy(url: String, dest: File, withMeter: Boolean = true): Int? {
        val job = if (withMeter) JobManager.createJob("d3") else null
        try {
            WebResource(URI(url)).copy(dest.toURI(), job)
            return job?.totalWork
        } finally { job?.done() }
    }

    @Test fun knownLengthBodyIsCopiedAndSizesTheMeter() = scenario { server, dir ->
        server.route("/m.zip") { it.reply(200, body) }
        val dest = File(dir, "m.zip")
        assertEquals(body.size, copy("${server.base}/m.zip", dest))
        assertArrayEquals(body, dest.readBytes())
    }

    @Test fun chunkedBody() = scenario { server, dir ->
        server.route("/c.zip") { it.reply(200, body, chunked = true) }
        val dest = File(dir, "c.zip")
        val total = copy("${server.base}/c.zip", dest)
        assertArrayEquals(body, dest.readBytes())
        assertEquals(-1, total) // URLConnection.contentLength is -1 without Content-Length; WebResource only HEADs on 0
    }

    @Test fun notFoundRaisesDownloadExceptionAndKeepsDestination() = scenario { server, dir ->
        server.route("/missing.zip") { it.reply(404, "<html>nope</html>".toByteArray()) }
        val dest = File(dir, "missing.zip").apply { writeText("keep") }
        try { copy("${server.base}/missing.zip", dest); fail("expected DownloadException") }
        catch (e: DownloadException) { assertEquals(404, e.statusCode) }
        assertEquals("keep", dest.readText())
    }

    @Test fun serverErrorRaisesDownloadException() = scenario { server, dir ->
        server.route("/boom") { it.reply(500, "x".toByteArray()) }
        try { copy("${server.base}/boom", File(dir, "b")); fail("expected DownloadException") }
        catch (e: DownloadException) { assertEquals(500, e.statusCode) }
    }

    @Test fun refusedConnectionIsUnableToFind() = scenario { server, dir ->
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        try { copy("https://127.0.0.1:$closedPort/x", File(dir, "x")); fail("expected LucidException") }
        catch (e: DownloadException) { fail("refused must not carry a status: $e") }
        catch (e: DownloadCancelledException) { fail("refused is not a cancel: $e") }
        catch (e: LucidException) { assertTrue(e.message!!.contains("Unable to find")) }
    }

    @Test fun stalledBodyTimesOutAsCancelled() = scenario(timeoutMs = 300) { server, dir ->
        val release = CountDownLatch(1)
        server.route("/stall") { ex ->
            ex.responseHeaders.add("Content-Length", "10")
            ex.sendResponseHeaders(200, 10)
            ex.responseBody.write(ByteArray(3)); ex.responseBody.flush()
            release.await(5, TimeUnit.SECONDS)
        }
        val start = System.nanoTime()
        try { copy("${server.base}/stall", File(dir, "s")); fail("expected DownloadCancelledException") }
        catch (e: DownloadCancelledException) { /* legacy: SocketTimeoutException is an InterruptedIOException */ }
        finally { release.countDown() }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
    }

    @Test fun slowButProgressingBodyLongerThanTheTimeoutCompletes() = scenario(timeoutMs = 500) { server, dir ->
        server.route("/slow") { ex ->
            ex.sendResponseHeaders(200, 8)
            repeat(8) { ex.responseBody.write(byteArrayOf(it.toByte())); ex.responseBody.flush(); Thread.sleep(200) }
        }
        val dest = File(dir, "slow")
        copy("${server.base}/slow", dest)
        assertArrayEquals(ByteArray(8) { it.toByte() }, dest.readBytes())
    }

    /** The caller's cancellation (not a timeout) aborts a stalled read promptly, on legacy and on ktor alike. */
    @Test fun cancellingTheCallerDuringAStalledReadEndsPromptlyWithCancellationException() = scenario(timeoutMs = 10_000) { server, dir ->
        val release = CountDownLatch(1)
        val stalledReadEntered = CountDownLatch(1)
        server.route("/hang") { ex ->
            ex.sendResponseHeaders(200, 10); ex.responseBody.write(ByteArray(2)); ex.responseBody.flush()
            release.await(20, TimeUnit.SECONDS)
        }
        // Signal the read AFTER receiving the initial bytes, not merely request/server entry.
        val delegate = WebResource.transport!!
        WebResource.transport = object : HttpTransport by delegate {
            override suspend fun get(uri: URI, timeoutMs: Int): org.crosswire.common.util.HttpBody {
                val response = delegate.get(uri, timeoutMs)
                return object : org.crosswire.common.util.HttpBody by response {
                    private var receivedBytes = false
                    override suspend fun read(buf: ByteArray): Int {
                        if (receivedBytes) stalledReadEntered.countDown()
                        return response.read(buf).also { if (it > 0) receivedBytes = true }
                    }
                }
            }
        }
        val outcomeOfCopy = CompletableDeferred<Throwable?>()
        val caller = launch(Dispatchers.IO) { outcomeOfCopy.complete(runCatching { copy("${server.base}/hang", File(dir, "h")) }.exceptionOrNull()) }
        try {
            assertTrue("must reach stalled body read", stalledReadEntered.await(5, TimeUnit.SECONDS))
            val start = System.nanoTime()
            caller.cancel()
            val outcome = withTimeout(3_000) { outcomeOfCopy.await() }
            assertTrue("a cancelled caller must finish promptly", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000)
            assertTrue("expected CancellationException, got $outcome", outcome is CancellationException)
            assertFalse("a caller cancel is not a download cancel: $outcome", outcome is DownloadCancelledException)
        } finally {
            release.countDown()
        }
    }

    @Test fun gzipContentEncodingIsDecodedOnlyWhenRequested() = scenario { server, dir ->
        server.route("/g") { ex ->
            val gz = ex.requestHeaders["Accept-Encoding"].orEmpty().any { "gzip" in it }
            if (gz) ex.reply(200, gzip(body), mapOf("Content-Encoding" to "gzip")) else ex.reply(200, body)
        }
        val dest = File(dir, "g")
        copy("${server.base}/g", dest, withMeter = false)
        val requestedGzip = server.requests.single().headers["accept-encoding"].orEmpty().any { "gzip" in it }
        if (legacy) assertFalse("legacy receives the plain response without gzip negotiation", requestedGzip)
        else assertTrue("must exercise the gzip-encoded response, not the plain fallback", requestedGzip)
        assertArrayEquals(body, dest.readBytes())
    }

    @Test fun tarGzWithoutContentEncodingIsByteIdentical() = scenario { server, dir ->
        val archive = gzip(body)
        server.route("/mods.d.tar.gz") { it.reply(200, archive, mapOf("Content-Type" to "application/x-gzip")) }
        val dest = File(dir, "mods.d.tar.gz")
        assertEquals(archive.size, copy("${server.base}/mods.d.tar.gz", dest))
        assertArrayEquals(archive, dest.readBytes())
    }

    @Test fun httpsRedirectIsFollowed() = scenario { server, dir ->
        server.route("/old") { it.reply(302, ByteArray(0), mapOf("Location" to "${server.base}/new")) }
        server.route("/new") { it.reply(200, body) }
        val dest = File(dir, "r")
        copy("${server.base}/old", dest)
        assertArrayEquals(body, dest.readBytes())
    }

    /** Spec §5.2 item 2. Legacy branch characterizes today's outcome; record it in spec §10. */
    @Test fun httpsToHttpRedirectFails() = scenario { server, dir ->
        server.route("/down") { it.reply(302, "moved".toByteArray(), mapOf("Location" to "http://127.0.0.1:1/plain")) }
        val dest = File(dir, "d")
        val error = runCatching { copy("${server.base}/down", dest) }.exceptionOrNull()
        if (legacy) {
            // Expected legacy outcome: JDK HttpsURLConnection does not follow a protocol change; the 302
            // body is written as if it were the file. If the run shows otherwise, change this branch to
            // the observed outcome and record it in spec §10 (characterization, not a fix).
            assertEquals(null, error)
            assertEquals("moved", dest.readText())
        } else {
            assertTrue("expected LucidException, got $error", error is LucidException && error !is DownloadException)
            assertFalse(dest.exists() && dest.readText() == "moved")
        }
    }

    /** Spec §5.2 item 1. */
    @Test fun plainHttpUrl() = scenario { _, dir ->
        val error = runCatching { copy("http://127.0.0.1:1/x", File(dir, "x")) }.exceptionOrNull()
        if (legacy) assertTrue("legacy casts to HttpsURLConnection: $error", error is ClassCastException)
        else assertTrue("expected LucidException, got $error", error is LucidException)
    }

    @Test fun lastModifiedProbeUsesGetAndParsesTheHeader() = scenario { server, _ ->
        server.route("/lm") { it.reply(200, body, mapOf("Last-Modified" to lastModifiedHeader)) }
        assertEquals(lastModifiedMillis, NetUtil.getLastModified(URI("${server.base}/lm")))
        assertEquals("GET", server.requests.single { it.path == "/lm" }.method)
    }

    @Test fun lastModifiedProbeWithoutHeaderIsZero() = scenario { server, _ ->
        server.route("/nolm") { it.reply(200, body) }
        assertEquals(0L, NetUtil.getLastModified(URI("${server.base}/nolm")))
    }

    @Test fun sizeProbeReturnsContentLengthOrMinusOne() = scenario { server, _ ->
        server.route("/len") { it.reply(200, body) }
        server.route("/nolen") { it.reply(200, body, chunked = true) }
        assertEquals(body.size, NetUtil.getSize(URI("${server.base}/len")))
        assertEquals(-1, NetUtil.getSize(URI("${server.base}/nolen")))
    }

    @Test fun webResourceSizeUsesHead() = scenario { server, _ ->
        server.route("/h") { it.reply(200, if (it.requestMethod == "HEAD") ByteArray(0) else body, mapOf("Content-Length" to body.size.toString())) }
        assertEquals(body.size, WebResource(URI("${server.base}/h")).getSize())
        assertEquals("HEAD", server.requests.single { it.path == "/h" }.method)
    }

    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { GZIPOutputStream(it).use { gz -> gz.write(bytes) } }.toByteArray()
}
