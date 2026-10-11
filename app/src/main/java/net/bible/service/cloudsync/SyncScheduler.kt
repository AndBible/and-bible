package net.bible.service.cloudsync

import kotlinx.coroutines.Deferred

/** Starts the OS sync host; true once it ran, false if the OS refused. */
fun interface SyncScheduler { fun startDatabaseSync(): Deferred<Boolean> }
