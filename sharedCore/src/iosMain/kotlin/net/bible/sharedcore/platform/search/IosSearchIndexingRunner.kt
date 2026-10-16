package net.bible.sharedcore.platform.search

import net.bible.sharedcore.platform.sync.withLock
import platform.Foundation.NSRecursiveLock
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * Keeps a search index build alive for the time UIKit grants a background task, and cancels it when that time runs out.
 *
 * :sharedCore does not depend on JSword, so the build and its cancellation are injected by the iOS app, e.g.
 * ```
 * runner.run(
 *     cancelBuild = { JobManager.findJob(Progress.CREATE_INDEX.replace("%s", book.initials))?.cancel() },
 * ) { withContext(Dispatchers.Default) { IndexManagerFactory.getIndexManager().scheduleIndexCreation(book) } }
 * ```
 * `scheduleIndexCreation` blocks until the build ends and reports failures through the Reporter instead of throwing.
 * Cancelling the `CREATE_INDEX-<initials>` job makes the build end with status UNDONE and removes its `.creating`
 * file, so an expired allowance never leaves a half-written index.
 *
 * The background task is ended exactly once, whichever comes first: the build returning or throwing, or UIKit's
 * expiration handler. The expiration handler must end the task before it returns (UIKit terminates the app
 * otherwise), so it cancels the build and ends the task without waiting for the build to wind down.
 */
class IosSearchIndexingRunner(
    private val begin: (() -> Unit) -> ULong = { expire ->
        UIApplication.sharedApplication.beginBackgroundTaskWithName("search-index", expire)
    },
    private val end: (ULong) -> Unit = { UIApplication.sharedApplication.endBackgroundTask(it) },
) {
    /** Runs [build] under a background task; [cancelBuild] is called (at most once) when UIKit expires the task. */
    suspend fun <T> run(cancelBuild: () -> Unit, build: suspend () -> T): T {
        val lock = NSRecursiveLock()
        var identifier: ULong? = null
        var expired = false
        var ended = false
        var cancelled = false

        fun endOnce() = lock.withLock {
            val id = identifier ?: return@withLock
            if (ended) return@withLock
            ended = true
            if (id != UIBackgroundTaskInvalid) end(id)
        }

        fun cancelOnce() {
            val first = lock.withLock { (!cancelled).also { cancelled = true } }
            if (first) cancelBuild()
        }

        val id = begin {
            lock.withLock { expired = true }
            try {
                cancelOnce()
            } finally {
                endOnce()
            }
        }
        lock.withLock { identifier = id }
        // Expiry can arrive inside begin, before UIKit returns its identifier: act on it now.
        if (lock.withLock { expired }) {
            try {
                cancelOnce()
            } finally {
                endOnce()
            }
        }
        try {
            return build()
        } finally {
            endOnce()
        }
    }
}
