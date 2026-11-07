package net.bible.sharedcore.platform.desktop

import net.bible.sharedcore.log.Log
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Bounds the caller's wait without interrupting native or database work. */
fun boundedBlocking(name: String, timeoutMs: Long, block: () -> Unit): Boolean {
    val task = FutureTask(block)
    Thread(task, name).apply { isDaemon = true }.start()
    return try {
        task.get(timeoutMs, TimeUnit.MILLISECONDS)
        true
    } catch (e: TimeoutException) {
        Log.w("BoundedBlocking", "$name did not finish within $timeoutMs ms")
        false
    } catch (e: ExecutionException) {
        Log.e("BoundedBlocking", "$name failed", e.cause ?: e)
        true
    }
}
