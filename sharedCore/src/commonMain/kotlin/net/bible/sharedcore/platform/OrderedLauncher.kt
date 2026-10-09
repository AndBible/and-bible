package net.bible.sharedcore.platform

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log

/**
 * Launches blocks in [scope] so that blocks sharing a key run one at a time, in submission order
 * (spec L1a §4: fire-and-forget JS calls serialized per window). Different keys run concurrently.
 * The previous block's Job is chained, so order is the order of [launch] calls, not of lock races.
 */
class OrderedLauncher(private val scope: CoroutineScope) {
    private val lock = SynchronizedObject()
    private val tails = mutableMapOf<Any, Job>()

    fun launch(key: Any, block: suspend () -> Unit): Job = synchronized(lock) {
        val previous = tails[key]
        val job = scope.launch {
            previous?.join()
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Ordered block for $key failed", e)
            }
        }
        tails[key] = job
        job.invokeOnCompletion { synchronized(lock) { if (tails[key] === job) tails.remove(key) } }
        job
    }

    private companion object {
        const val TAG = "OrderedLauncher"
    }
}
