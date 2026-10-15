package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.Deferred

/** Runs one database sync in a host-owned context (Android: foreground service; iOS: background task). */
fun interface SyncScheduler {
    fun startDatabaseSync(): Deferred<Boolean>
}
