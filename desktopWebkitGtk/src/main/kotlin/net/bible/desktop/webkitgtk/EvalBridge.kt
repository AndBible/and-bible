package net.bible.desktop.webkitgtk

import java.util.concurrent.atomic.AtomicLong

/**
 * Evaluate-callback bookkeeping: every callback fires exactly once; a JS exception, timeout or dispose gives
 * [NO_RESULT]. Thread-safe; callbacks always run through [deliver] and never under the lock.
 */
class EvalBridge(
    private val deliver: (Runnable) -> Unit,
    private val scheduleTimeout: (delayMs: Long, task: Runnable) -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val lock = Any()
    private val pending = HashMap<Long, (String) -> Unit>()
    private val nextId = AtomicLong(1)

    val pendingCount: Int get() = synchronized(lock) { pending.size }

    /** Registers [callback] and schedules its timeout; returns the id to pass to [complete] or [fail]. */
    fun register(callback: (String) -> Unit): Long {
        val id = nextId.getAndIncrement()
        synchronized(lock) { pending[id] = callback }
        scheduleTimeout(timeoutMs, Runnable { answer(id, NO_RESULT) })
        return id
    }

    /** Answers [id] with [json] (null becomes [NO_RESULT]); an unknown or already answered id is ignored. */
    fun complete(id: Long, json: String?) = answer(id, resultJson(json))

    /** A JS exception: answers [id] with [NO_RESULT]. */
    fun fail(id: Long) = answer(id, NO_RESULT)

    /** Dispose or new document: answers everything pending with [NO_RESULT]. */
    fun failAll() {
        val all = synchronized(lock) { pending.values.toList().also { pending.clear() } }
        all.forEach { dispatch(it, NO_RESULT) }
    }

    private fun answer(id: Long, json: String) {
        val callback = synchronized(lock) { pending.remove(id) } ?: return
        dispatch(callback, json)
    }

    private fun dispatch(callback: (String) -> Unit, json: String) {
        deliver(Runnable {
            try {
                callback(json)
            } catch (t: Throwable) {
                System.err.println("EvalBridge: callback failed: $t")
            }
        })
    }

    companion object {
        const val NO_RESULT = "null"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        fun resultJson(json: String?): String = json ?: NO_RESULT
    }
}
