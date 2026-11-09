package net.bible.service.cloudsync.nextcloud

import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Drains callbacks until the owned worker finishes, but cannot cancel library-owned work. */
internal fun <T> awaitOperation(timeoutMillis: Long = 10_000, cleanupMillis: Long = 2_000, block: suspend () -> T): T {
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
