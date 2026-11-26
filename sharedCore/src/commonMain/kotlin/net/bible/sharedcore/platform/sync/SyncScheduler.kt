package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.Deferred

/** Runs one database sync in a host-owned context (Android: foreground service; iOS: background task). */
fun interface SyncScheduler {
    fun startDatabaseSync(): Deferred<Boolean>

    /** Host-owned work that can start without CloudSync.start(), including Android leave sync. */
    val pendingDatabaseSync: Deferred<Boolean>? get() = null

    fun startUserInitiatedDatabaseSync(): Deferred<Boolean> = startDatabaseSync()
}
