package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import platform.Foundation.NSRecursiveLock
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/** Adapts UIKit's execution allowance; the injected action owns all sync policy. */
class IosSyncScheduler(
    private val scope: CoroutineScope,
    private val sync: suspend () -> Boolean,
    private val begin: (() -> Unit) -> ULong = { expire ->
        UIApplication.sharedApplication.beginBackgroundTaskWithName("sync", expire)
    },
    private val end: (ULong) -> Unit = { UIApplication.sharedApplication.endBackgroundTask(it) },
) : SyncScheduler {
    private val lock = NSRecursiveLock()
    private var pending: Deferred<Boolean>? = null

    override fun startDatabaseSync(): Deferred<Boolean> = lock.withLock {
        pending?.takeUnless { it.isCompleted }?.let { return@withLock it }
        var identifier: ULong? = null
        var ended = false
        fun endOnce() {
            val id = identifier ?: return
            if (ended) return
            ended = true
            if (id != UIBackgroundTaskInvalid) end(id)
        }
        val work = IosBackgroundWork(scope, sync, lock) { endOnce() }
        pending = work.result
        work.start { expire ->
            identifier = begin(expire)
            // Expiry can arrive inside begin, before UIKit returns its identifier.
            if (work.result.isCompleted) endOnce()
        }
        work.result
    }
}

/** Serializes completion against expiration, including cancellation before the coroutine starts. */
internal class IosBackgroundWork(
    private val scope: CoroutineScope,
    private val action: suspend () -> Boolean,
    private val lock: NSRecursiveLock = NSRecursiveLock(),
    private val complete: (Boolean) -> Unit,
) {
    val result = CompletableDeferred<Boolean>()
    private var finished = false

    fun start(installExpiration: (() -> Unit) -> Unit) = lock.withLock {
        val worker = scope.launch(start = CoroutineStart.LAZY) {
            val ok = try {
                action()
            } catch (_: CancellationException) {
                false
            } catch (error: Throwable) {
                Log.w(TAG, "Background work failed", error)
                false
            }
            finish(ok)
        }
        try {
            installExpiration {
                // Publish false before cancellation can race the coroutine's completion.
                finish(false)
                worker.cancel()
            }
            // Install first: an already-cancelled scope invokes this hook immediately.
            worker.invokeOnCompletion { error ->
                if (error != null && error !is CancellationException) {
                    Log.w(TAG, "Background work failed", error)
                }
                finish(false)
            }
            if (!finished) worker.start() else worker.cancel()
        } catch (error: Throwable) {
            Log.w(TAG, "Background work setup failed", error)
            finish(false)
            worker.cancel()
        }
    }

    private fun finish(ok: Boolean) = lock.withLock {
        if (!finished) {
            finished = true
            try {
                complete(ok)
            } catch (error: Throwable) {
                Log.w(TAG, "Background work completion failed", error)
            }
            result.complete(ok)
        }
    }

    private companion object { const val TAG = "IosBackgroundWork" }
}

internal inline fun <T> NSRecursiveLock.withLock(block: () -> T): T {
    lock()
    try { return block() } finally { unlock() }
}
