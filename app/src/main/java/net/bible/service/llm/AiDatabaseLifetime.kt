package net.bible.service.llm

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns store operations and observer queries across close/copy/reopen of the AI database. */
class AiDatabaseLifetime {
    private val admission = Mutex()
    private val observers = mutableSetOf<Job>()
    private val generation = MutableStateFlow(0L)
    private var active: Replacement? = null

    /** Cannot be constructed by callers or reused after the replacement finishes. */
    class Replacement internal constructor(internal val owner: AiDatabaseLifetime)

    internal fun requireOwner(token: Replacement) {
        check(token.owner === this && active === token) { "Inactive AI database replacement token" }
    }

    /** Resolve the DAO inside this block, never before admission. */
    suspend fun <T> use(block: suspend () -> T): T = admission.withLock { block() }

    /** Owns invalidation observation; data queries inside the block must separately use admission. */
    suspend fun observe(block: suspend () -> Unit): Unit = coroutineScope {
        generation.collectLatest {
            val child = admission.withLock { launch { block() }.also { observers.add(it) } }
            try { child.join() }
            finally {
                withContext(NonCancellable) {
                    child.cancelAndJoin()
                    admission.withLock { observers.remove(child) }
                }
            }
        }
    }

    suspend fun <T> replace(block: suspend (Replacement) -> T): T = admission.withLock {
        val token = Replacement(this)
        active = token
        try {
            // No store mutex is held here. Cancellation joins even a non-cancellable native step.
            withContext(NonCancellable) { observers.toList().forEach { it.cancelAndJoin() } }
            block(token)
        } finally {
            active = null
            generation.value++
        }
    }

    internal suspend fun <T> replace(token: Replacement, block: suspend (Replacement) -> T): T {
        requireOwner(token)
        return block(token)
    }
}
