package net.bible.android.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.DownloadTimeoutException
import net.bible.sharedcore.http.HttpHeaders
import net.bible.sharedcore.http.HttpStatusException
import org.crosswire.common.util.HttpBody
import org.crosswire.common.util.HttpHead
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.KUri
import org.crosswire.common.util.TransportCancelledException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bridges JSword's blocking download port to suspend HTTP until the JSword suspend port (K5b).
 * The owner scope lives for the app lifetime; each body owns and cancels its own producer.
 * Metadata starts the request but does not consume body bytes. Reading uses a bounded queue,
 * so a module's size never determines memory use. Timeouts come from DownloadHttp's provider;
 * the port's timeoutMs arguments always equal that provider's WebResource timeout.
 */
class BlockingHttpTransport(
    private val http: DownloadHttp,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HttpTransport {
    override fun head(uri: KUri, timeoutMs: Int): HttpHead = blocking {
        val h = http.head(uri.toString())
        if (h.status == 200) HttpHead(h.status, null, h.contentLength.toIntLength(), h.lastModified)
        else HttpHead(h.status, h.reason, 0, 0)
    }

    override fun get(uri: KUri, timeoutMs: Int): HttpBody = StreamingBody(uri.toString())

    private inner class StreamingBody(url: String) : HttpBody {
        private val headers = CompletableDeferred<HttpHeaders>()
        private val readRequested = CompletableDeferred<Unit>()
        private val chunks = Channel<ByteArray>(capacity = 16)
        @Volatile private var closed = false
        private var pending: ByteArray? = null
        private var pendingOffset = 0
        private val producer = scope.launch(start = CoroutineStart.LAZY) {
            try {
                http.open(url) { response ->
                    headers.complete(response.headers)
                    readRequested.await()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = response.read(buf)
                        if (n < 0) break
                        if (n > 0) chunks.send(buf.copyOf(n))
                    }
                }
                chunks.close()
            } catch (error: Throwable) {
                headers.completeExceptionally(error)
                chunks.close(error)
                if (error is CancellationException) throw error
            }
        }.also { job ->
            // A lazy job cancelled before its first execution never enters the try/finally above.
            job.invokeOnCompletion { cause ->
                if (cause != null) {
                    headers.completeExceptionally(cause)
                    chunks.close(cause)
                }
            }
        }

        private fun <T> bodyBlocking(block: suspend () -> T): T = try {
            checkOpen()
            blocking(block)
        } catch (error: TransportCancelledException) {
            close()
            throw error
        }

        private fun checkOpen() {
            if (closed) throw TransportCancelledException(CancellationException("HTTP body closed"))
        }

        private fun meta(): HttpHeaders = bodyBlocking {
            producer.start()
            headers.await()
        }

        override val status: Int get() = meta().status
        override val contentLength: Int get() = meta().contentLength.toIntLength()
        override val lastModified: Long get() = meta().lastModified

        override fun prepareRead() {
            val h = meta()
            if (h.status >= 400) {
                producer.cancel()
                throw HttpStatusException(h.status, h.reason)
            }
            readRequested.complete(Unit)
        }

        override fun read(buf: ByteArray): Int {
            checkOpen()
            if (buf.isEmpty()) return 0
            prepareRead()
            val chunk = pending ?: bodyBlocking {
                val result = chunks.receiveCatching()
                result.exceptionOrNull()?.let { throw it }
                result.getOrNull()
            } ?: return -1
            val n = minOf(buf.size, chunk.size - pendingOffset)
            chunk.copyInto(buf, 0, pendingOffset, pendingOffset + n)
            pendingOffset += n
            if (pendingOffset == chunk.size) {
                pending = null
                pendingOffset = 0
            } else {
                pending = chunk
            }
            return n
        }

        override fun close() {
            closed = true
            producer.cancel()
            chunks.cancel()
        }
    }

    private fun <T> blocking(block: suspend () -> T): T = try {
        runBlocking { block() }
    } catch (error: InterruptedException) {
        throw TransportCancelledException(error)
    } catch (error: DownloadTimeoutException) {
        throw TransportCancelledException(error)
    } catch (error: CancellationException) {
        throw TransportCancelledException(error)
    }
}

/** URLConnection.getContentLength returns -1 for an absent or overflowing length. */
private fun Long.toIntLength(): Int = if (this in 0..Int.MAX_VALUE.toLong()) toInt() else -1
