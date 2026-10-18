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

import kotlinx.coroutines.Deferred

/**
 * Default cloud sync interval in seconds (5 minutes). Same value as `ReadingAppBootstrap.DEFAULT_SYNC_INTERVAL`
 * in `:app`, which is owned by another stream; keep the two in step until that one moves here.
 */
const val DEFAULT_CLOUD_SYNC_INTERVAL_SECONDS = 300L

private const val MIN_NEXT_EARLIEST_SECONDS = 60L

/**
 * Decides whether a platform background refresh (for example iOS BGTaskScheduler) should sync now.
 *
 * Unlike the foreground loop in `ReadingAppBootstrap.synchronize`, this policy does not require local
 * changes, because a background refresh exists to pull remote changes.
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
) {
    /**
     * Runs a sync if one is due.
     *
     * @return true when nothing needed doing or the sync succeeded; false when sign-in failed or the sync failed
     */
    suspend fun runOnce(): Boolean {
        if (!syncEnabled() || replacing()) return true
        if (nowMs() - lastSynchronizedMs() < intervalSeconds() * 1000) return true
        if (!signedIn() && signIn() != true) return false
        return startSync().await()
    }

    /** Earliest delay in seconds before the next background run: the interval, floored at 60. */
    fun nextEarliestSeconds(): Long = maxOf(intervalSeconds(), MIN_NEXT_EARLIEST_SECONDS)
}
