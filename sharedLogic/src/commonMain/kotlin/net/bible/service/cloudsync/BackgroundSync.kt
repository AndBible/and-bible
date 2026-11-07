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
import kotlinx.coroutines.Deferred

/** Default cloud sync interval in seconds (5 minutes): the `cloud_sync_interval` setting's default on every platform. */
const val DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS = 300L

private const val MIN_NEXT_EARLIEST_SECONDS = 60L

/**
 * Runs one sync when it is due: sync enabled, no database replacement under way, and (unless forced) the interval since
 * the last successful sync has passed. Used by the iOS BGTask refresh and by every [SyncTriggerDriver] pass.
 *
 * It never requires local changes: a pass also exists to pull remote changes.
 */
class BackgroundSyncPolicy(
    private val syncEnabled: () -> Boolean,
    private val replacing: () -> Boolean,
    private val intervalSeconds: () -> Long,
    private val lastSynchronizedMs: () -> Long,
    private val nowMs: () -> Long,
    private val signedIn: () -> Boolean,
    private val signIn: suspend () -> Boolean?,
    private val startSync: () -> Deferred<Boolean>,
    private val stopSync: ((Deferred<Boolean>) -> Unit)? = null,
) {
    /**
     * Runs a sync if one is due; [force] skips only the interval check.
     *
     * Cancelling the caller (for example a BGTask expiry) also cancels the started sync.
     *
     * @return true when nothing needed doing or the sync succeeded; false when sign-in failed or the sync failed
     */
    suspend fun runOnce(force: Boolean = false): Boolean {
        if (!canRun()) return true
        if (!force && nowMs() - lastSynchronizedMs() < intervalSeconds() * 1000) return true
        if (!signedIn() && signIn() != true) return false
        val sync = startSync()
        return try {
            sync.await()
        } catch (e: CancellationException) {
            // The scheduler's Deferred is shared: cancelling only our wait would leave the sync running
            // after the platform expired the background task.
            if (stopSync != null) stopSync.invoke(sync) else sync.cancel()
            throw e
        }
    }

    /** Whether sync is enabled and the databases are not being replaced: a pass has anything to do at all. */
    fun canRun(): Boolean = syncEnabled() && !replacing()

    /** The `cloud_sync_interval` setting in milliseconds. */
    fun intervalMs(): Long = intervalSeconds() * 1000

    /** The time of the last successful sync (`globalLastSynchronized`), in epoch milliseconds. */
    fun lastSyncMs(): Long = lastSynchronizedMs()

    /** The policy's clock, in epoch milliseconds. */
    fun now(): Long = nowMs()

    /** Earliest delay in seconds before the next background run: the interval, floored at 60. */
    fun nextEarliestSeconds(): Long = maxOf(intervalSeconds(), MIN_NEXT_EARLIEST_SECONDS)
}
