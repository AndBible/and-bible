/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */


package net.bible.service.cloudsync

import kotlinx.coroutines.CancellationException
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppSettings

private const val JOB_TAG = "DatabaseSyncJob"

/**
 * Runs one database sync with the running-flag bookkeeping shared by all platforms.
 *
 * [notifyRunning] is set to true first. On success [waitUntilFinished] has returned, then
 * [recordFinished] runs and the running flag is released by that call (the caller's record step
 * owns it), so the job itself never emits `false` on success. On failure or cancellation the flag is
 * cleared here; [CancellationException] is rethrown after clearing it.
 *
 * @return true when the sync completed, false when it threw (the exception is logged)
 */
suspend fun runDatabaseSyncJob(
    notifyRunning: (Boolean) -> Unit,
    synchronize: suspend () -> Unit,
    waitUntilFinished: suspend () -> Unit,
    recordFinished: () -> Unit,
): Boolean {
    try {
        notifyRunning(true)
        synchronize()
        waitUntilFinished()
    } catch (e: CancellationException) {
        notifyRunning(false)
        throw e
    } catch (e: Exception) {
        Log.e(JOB_TAG, "Synchronization failed", e)
        notifyRunning(false)
        return false
    }
    recordFinished()
    return true
}

/** Convenience overload wiring [runDatabaseSyncJob] to [cloudSync] and [recordSyncFinished]. */
suspend fun runDatabaseSyncJob(cloudSync: CloudSync, settings: AppSettings): Boolean =
    runDatabaseSyncJob(
        notifyRunning = { cloudSync.notifySyncRunning(it) },
        synchronize = { cloudSync.synchronize() },
        waitUntilFinished = { cloudSync.waitUntilFinished(true) },
        recordFinished = { recordSyncFinished(settings, cloudSync) },
    )
