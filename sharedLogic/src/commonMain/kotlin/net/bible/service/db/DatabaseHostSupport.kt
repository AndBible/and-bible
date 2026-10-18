/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.db

import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.control.page.window.WorkspacePersistence
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.common.ReadingProgressSettingsStore
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.settings.store.SettingsStore
import org.koin.mp.KoinPlatform

private const val TAG = "DbContainer"

/** Bound for the ordered store write queues when the caller gave no timeout. */
private const val ORDERED_WRITES_EXIT_WAIT_MS = 5_000L

/**
 * Shared bodies of the [DatabaseHost] members that the Android and iOS hosts implement identically. Public only
 * because the Android host lives in `:app`; not an API for anything else.
 */
object DatabaseHostSupport {
    /** Waits for queued workspace writes to reach the database. Lookup failures are swallowed. */
    suspend fun awaitWorkspacePersisted() {
        val persistence = runCatching { KoinPlatform.getKoinOrNull()?.getOrNull<WorkspacePersistence>() }.getOrNull()
        persistence?.awaitPersisted()
    }

    /**
     * Flushes [store] (when given) and the ordered write queues, each bounded by [timeoutMs] (the queues by
     * [ORDERED_WRITES_EXIT_WAIT_MS] when null). Must run inside a blocking edge. Never throws.
     */
    suspend fun flushOrderedWrites(store: SettingsStore?, timeoutMs: Long?) {
        try {
            if (store != null) {
                if (timeoutMs == null) store.flush()
                else if (withTimeoutOrNull(timeoutMs) { store.flush() } == null) Log.w(TAG, "Settings flush timed out")
            }
            // The ordered write-through keys (workspace, global text display, reading progress) queue their
            // writes off the caller's thread; a few ms before exit they would otherwise die with the process.
            val launcher = runCatching { KoinPlatform.getKoinOrNull()?.getOrNull<OrderedLauncher>() }.getOrNull() ?: return
            val keys = listOf(WorkspacePersistence.KEY, GlobalTextDisplaySettingsStore.KEY, ReadingProgressSettingsStore.KEY)
            if (withTimeoutOrNull(timeoutMs ?: ORDERED_WRITES_EXIT_WAIT_MS) { keys.forEach { launcher.join(it) } } == null)
                Log.w(TAG, "Ordered write queues did not drain before exit")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) { Log.e(TAG, "Settings flush failed", e) }
    }
}
