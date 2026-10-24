package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CancellationException
import net.bible.sharedcore.log.Log

/** Platform-free orchestration; UIKit/Koin/database operations are supplied by the iOS host. */
internal suspend fun runIosBackgroundRefresh(
    protectedDataAvailable: suspend () -> Boolean,
    started: () -> Boolean,
    start: suspend () -> Unit,
    openDatabases: suspend () -> Unit,
    sync: suspend () -> Boolean,
    reschedule: () -> Unit,
): Boolean = try {
    if (!protectedDataAvailable()) true
    else {
        if (!started()) start()
        openDatabases()
        sync()
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.e("IosBackgroundSync", "background refresh failed", e)
    false
} finally {
    try { reschedule() }
    catch (e: Exception) { Log.e("IosBackgroundSync", "rescheduling background refresh failed", e) }
}

/** Explicit, process-local acceptance flag; never changes ordinary installs or persisted preferences. */
fun task7SyncNamespace(debugBinary: Boolean, arguments: List<String>): String? =
    "net.bible.android.activity.debug".takeIf { debugBinary && "-task7AndroidDebugSyncNamespace" in arguments }
