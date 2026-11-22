package net.bible.service.cloudsync.nextcloud

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Default wall-clock budget of [awaitOperation]. The loop returns the moment the worker is done, so a generous
 * value costs nothing on success and only delays a true hang's failure. 10 s was too tight: with 8 loopback HTTP
 * round trips, cold OkHttp/Sardine class loading and the host at load ~80, one operation took more than 10 s
 * and the cleanup then failed too (NextCloudDocumentMetaReuseTest, 2026-11-21 batch gate).
 */
internal const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 60_000L

/** Budget for the worker thread to exit after the operation ended or timed out; also scaled for a loaded host. */
internal const val DEFAULT_OPERATION_CLEANUP_MILLIS = 10_000L

/** Drains callbacks until the owned worker finishes, but cannot cancel library-owned work. */
internal fun <T> awaitOperation(
    timeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
    cleanupMillis: Long = DEFAULT_OPERATION_CLEANUP_MILLIS,
    block: suspend () -> T,
): T {
    val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "test-operation").apply { isDaemon = true } }
    val operation = executor.submit<T> { runBlocking { block() } }
    executor.shutdown()
    var failure: Throwable? = null
    try {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!operation.isDone && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            try { operation.get(10, TimeUnit.MILLISECONDS) } catch (_: TimeoutException) { }
        }
        if (!operation.isDone) throw TimeoutException("Operation timed out")
        return operation.get()
    } catch (e: ExecutionException) {
        failure = e.cause ?: e
        throw failure
    } catch (e: Throwable) {
        failure = e
        throw e
    } finally {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cleanupMillis)
        while (!executor.isTerminated && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            executor.awaitTermination(10, TimeUnit.MILLISECONDS)
        }
        if (!executor.isTerminated) {
            executor.shutdownNow()
            val cleanupFailure = IllegalStateException("Operation worker did not terminate during cleanup", failure)
            throw cleanupFailure
        }
    }
}
