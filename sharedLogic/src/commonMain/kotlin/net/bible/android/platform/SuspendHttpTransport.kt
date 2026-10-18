package net.bible.android.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.DownloadTimeoutException
import net.bible.sharedcore.http.HttpHeaders
import net.bible.sharedcore.http.HttpStatusException
import net.bible.sharedlogic.util.isThreadInterrupt
import org.crosswire.common.util.HttpBody
import org.crosswire.common.util.HttpHead
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.KUri
import org.crosswire.common.util.TransportCancelledException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Maps DownloadHttp to JSword's suspend download port. The calls delegate directly: the caller's coroutine
 * suspends on them, so cancelling it cancels the transfer and surfaces as a CancellationException.
 * DownloadHttp streams a body only inside a callback, so a pull-style [HttpBody] needs a producer: the owner
 * scope lives for the app lifetime and each body owns and cancels its own producer.
 * Metadata starts the request but does not consume body bytes. Reading uses a bounded queue,
 * so a module's size never determines memory use. Timeouts come from DownloadHttp's provider;
 * the port's timeoutMs arguments always equal that provider's WebResource timeout.
 */
class SuspendHttpTransport(
    private val http: DownloadHttp,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : HttpTransport {
    override suspend fun head(uri: KUri, timeoutMs: Int): HttpHead = mapped {
        val h = http.head(uri.toString())
        if (h.status == 200) HttpHead(h.status, null, h.contentLength.toIntLength(), h.lastModified)
        else HttpHead(h.status, h.reason, 0, 0)
    }

    override suspend fun get(uri: KUri, timeoutMs: Int): HttpBody = StreamingBody(uri.toString())

    private inner class StreamingBody(url: String) : HttpBody {
        private val headers = CompletableDeferred<HttpHeaders>()
        private val readRequested = CompletableDeferred<Unit>()
        private val chunks = Channel<ByteArray>(capacity = 16)
        @kotlin.concurrent.Volatile private var closed = false
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

        /**
         * A timeout closes the body but remains a download error, not user cancellation. A cancelled producer
         * maps to [TransportCancelledException]; the calling coroutine's own cancellation passes through.
         */
        private suspend fun <T> bodyCall(block: suspend () -> T): T = try {
            checkOpen()
            mapped(block)
        } catch (error: DownloadTimeoutException) {
            close()
            throw error
        } catch (error: TransportCancelledException) {
            close()
            throw error
        } catch (error: CancellationException) {
            close()
            throw error
        }

        private fun checkOpen() {
            if (closed) throw TransportCancelledException(CancellationException("HTTP body closed"))
        }

        private suspend fun meta(): HttpHeaders = bodyCall {
            producer.start()
            headers.await()
        }

        override suspend fun status(): Int = meta().status
        override suspend fun contentLength(): Int = meta().contentLength.toIntLength()
        override suspend fun lastModified(): Long = meta().lastModified

        override suspend fun prepareRead() {
            val h = meta()
            if (h.status >= 400) {
                producer.cancel()
                throw HttpStatusException(h.status, h.reason)
            }
            readRequested.complete(Unit)
        }

        override suspend fun read(buf: ByteArray): Int {
            checkOpen()
            if (buf.isEmpty()) return 0
            prepareRead()
            val chunk = pending ?: bodyCall {
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

    /**
     * Maps interrupts and producer cancellation to [TransportCancelledException], but leaves timeout errors intact.
     * A CancellationException is the caller's own cancellation when its coroutine is no longer active and passes
     * through; otherwise a producer was cancelled underneath an active caller.
     */
    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw TransportCancelledException(error)
    } catch (error: Throwable) {
        if (error.isThreadInterrupt()) throw TransportCancelledException(error)
        throw error
    }
}

/** URLConnection.getContentLength returns -1 for an absent or overflowing length. */
private fun Long.toIntLength(): Int = if (this in 0..Int.MAX_VALUE.toLong()) toInt() else -1
