package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CoroutineScope
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.Foundation.NSRecursiveLock

/** UIKit execution allowance around the shared, application-owned pass. */
class IosSyncScheduler(
    scope: CoroutineScope,
    sync: suspend () -> Boolean,
    begin: (() -> Unit) -> ULong = { expire ->
        UIApplication.sharedApplication.beginBackgroundTaskWithName("sync", expire)
    },
    end: (ULong) -> Unit = { UIApplication.sharedApplication.endBackgroundTask(it) },
) : SyncScheduler {
    private val runner = IosSyncRunner(scope, sync, begin) { if (it != UIBackgroundTaskInvalid) end(it) }
    override fun startDatabaseSync() = runner.startDatabaseSync()
    /** The creator fixes interaction policy for the pass; a coalescing caller cannot change it. */
    fun startBackgroundDatabaseSync(sync: suspend () -> Boolean) = runner.startDatabaseSync(sync)
    fun stop() = runner.stop()
    /** Stops only the pass this caller joined; a completed pass cannot cancel its replacement. */
    fun stop(result: kotlinx.coroutines.Deferred<Boolean>) = runner.stop(result)
}

// Kept for existing Foundation-lock consumers (including the search indexing runner).
internal inline fun <T> NSRecursiveLock.withLock(block: () -> T): T {
    lock()
    try { return block() } finally { unlock() }
}
