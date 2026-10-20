package net.bible.sharedui.ios.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
            // Completion cleanup may still be queued: never select an already failed attempt.
            startup?.takeUnless { it.isCompleted && it.isCancelled }
                ?: scope.async(start = CoroutineStart.LAZY) { initialize() }.also { created ->
                    startup = created
                    created.invokeOnCompletion { failure ->
                        if (failure != null) scope.launch {
                            lock.withLock { if (startup === created) startup = null }
                        }
                    }
                    created.start()
                }
        }
        return attempt.await()
    }

    suspend fun openDatabases() {
        val parts = ensureStarted()
        val attempt = lock.withLock {
            opening?.takeUnless { it.isCompleted && it.isCancelled }
                ?: scope.async(start = CoroutineStart.LAZY) { open(parts) }.also { created ->
                    opening = created
                    created.invokeOnCompletion { failure ->
                        if (failure != null) scope.launch {
                            lock.withLock { if (opening === created) opening = null }
                        }
                    }
                    created.start()
                }
        }
        attempt.await()
    }
}
