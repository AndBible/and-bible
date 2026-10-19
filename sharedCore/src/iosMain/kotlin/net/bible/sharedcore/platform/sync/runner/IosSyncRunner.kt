package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** Adapts UIKit's execution allowance; the injected action owns all sync policy. */
internal class IosSyncRunner(
    private val scope: CoroutineScope,
    private val sync: suspend () -> Boolean,
    private val begin: (() -> Unit) -> ULong,
    private val end: (ULong) -> Unit,
) : SyncScheduler {
    private val lock = SynchronizedObject()
    private var pending: IosBackgroundWork? = null

    fun stop() = lock.withLock { pending?.stop() }

    /** An expired join owns only its pass, never a newer replacement selected in the meantime. */
    fun stop(result: Deferred<Boolean>) = lock.withLock {
        pending?.takeIf { it.result === result }?.stop()
    }

    override fun startDatabaseSync(): Deferred<Boolean> = startDatabaseSync(sync)

    fun startDatabaseSync(action: suspend () -> Boolean): Deferred<Boolean> = lock.withLock {
        pending?.takeUnless { it.isStopped }?.let { return@withLock it.result }
        var identifier: ULong? = null
        var ended = false
        fun endOnce() {
            val id = identifier ?: return
            if (ended) return
            ended = true
            end(id)
        }
        val work = IosBackgroundWork(scope, action, lock) { endOnce() }
        pending = work
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
    private val lock: SynchronizedObject = SynchronizedObject(),
    private val complete: (Boolean) -> Unit,
) {
    val result = CompletableDeferred<Boolean>()
    private var finished = false
    private var stopped = false
    /** Completion waits for this: BGTask's expiration handler must be installed before the task completes. */
    private var expirationInstalled = false
    val isStopped: Boolean get() = lock.withLock { stopped }

    private var worker: kotlinx.coroutines.Job? = null

    /** Cancellation belongs to the pass; its shared result resolves normally after cleanup. */
    fun stop() = lock.withLock { worker?.cancel() }

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
        this.worker = worker
        result.invokeOnCompletion { error ->
            if (error is CancellationException) worker.cancel()
        }
        // Install before setup too: a throwing begin must still release worker ownership.
        worker.invokeOnCompletion { error ->
            if (error != null && error !is CancellationException) {
                Log.w(TAG, "Background work failed", error)
            }
            // A worker launched into an already-cancelled scope completes during registration, before
            // installExpiration has run; start() then publishes the result itself.
            lock.withLock { stopped = true; if (expirationInstalled) finish(false) }
        }
        try {
            installExpiration {
                // Publish false before cancellation can race the coroutine's completion.
                finish(false)
                worker.cancel()
            }
            expirationInstalled = true
            if (worker.isCompleted) finish(false)
            else if (!finished) worker.start() else worker.cancel()
        } catch (error: Throwable) {
            Log.w(TAG, "Background work setup failed", error)
            expirationInstalled = true
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

internal inline fun <T> SynchronizedObject.withLock(block: () -> T): T = synchronized(this, block)
