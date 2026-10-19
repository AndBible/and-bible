package net.bible.sharedui.ios.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process ownership of startup and database opening, independent of any UI or BGTask waiter. */
internal class IosStartupOwner<T>(
    private val scope: CoroutineScope,
    private val initialize: suspend () -> T,
    private val open: suspend (T) -> Unit,
) {
    private val lock = Mutex()
    private var startup: Deferred<T>? = null
    private var opening: Deferred<Unit>? = null

    /** Test teardown drains application-owned operations before closing their databases and Koin. */
    internal suspend fun joinInFlight() {
        val attempts = lock.withLock { listOfNotNull(startup, opening) }
        attempts.forEach { it.join() }
    }

    suspend fun ensureStarted(): T {
        val attempt = lock.withLock {
            startup ?: scope.async(start = CoroutineStart.LAZY) { initialize() }.also { startup = it; it.start() }
        }
        return try { attempt.await() } catch (error: Throwable) {
            // Cancelling a waiter must not evict an application-owned operation still in progress.
            if (attempt.isCompleted && attempt.isCancelled) lock.withLock { if (startup === attempt) startup = null }
            throw error
        }
    }

    suspend fun openDatabases() {
        val parts = ensureStarted()
        val attempt = lock.withLock {
            opening ?: scope.async(start = CoroutineStart.LAZY) { open(parts) }.also { opening = it; it.start() }
        }
        try { attempt.await() } catch (error: Throwable) {
            if (attempt.isCompleted && attempt.isCancelled) lock.withLock { if (opening === attempt) opening = null }
            throw error
        }
    }
}
