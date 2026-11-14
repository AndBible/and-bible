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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WorkspacePersistence
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.common.ReadingProgressSettingsStore
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.settings.store.SettingsStore
import org.koin.mp.KoinPlatform

private const val TAG = "DbContainer"

/**
 * Bound for the Main hop of [DatabaseHostSupport.persistLiveWorkspace]. A Main that cannot run the save in this time is
 * blocked (possibly on the very replace that asked, e.g. a `runBlocking` caller on Main): the save is skipped, the
 * replace goes on. Main busy this long would be an ANR anyway.
 */
private const val PERSIST_LIVE_WORKSPACE_MAIN_WAIT_MS = 2_000L

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
     * [DatabaseHost.persistLiveWorkspace]: saves the held, loaded window repository on Main (its state is Main-confined)
     * and waits for the write. Without a Koin context or a loaded repository there is nothing to save. A failed save is
     * logged, not thrown: it must not stop the replace that asked for it. The Main hop is bounded by
     * [PERSIST_LIVE_WORKSPACE_MAIN_WAIT_MS], so a blocked Main skips the save instead of deadlocking the replace (a
     * `runBlocking` caller on Main whose replace runs on IO); a caller already on Main saves inline.
     */
    suspend fun persistLiveWorkspace() {
        val windowControl = runCatching { KoinPlatform.getKoinOrNull()?.getOrNull<WindowControl>() }.getOrNull() ?: return
        // No hop to Main when there is nothing to save: a replace must not wait on a Main that cannot run (a test's
        // unadvanced dispatcher, a host that has not started reading).
        if (!windowControl.holdsLoadedRepository) return
        // Not withContext(Main): a cancelled withContext still waits for its block to be dispatched on Main, so a
        // timeout around it cannot free the replace from a blocked Main. A launched save is joined with a timeout and,
        // on expiry, cancelled before it starts.
        val logFailure = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Saving the live workspace before a database replace failed", e) }
        val save = CoroutineScope(Dispatchers.Main.immediate + logFailure).launch { windowControl.saveLoadedRepository() }
        if (withTimeoutOrNull(PERSIST_LIVE_WORKSPACE_MAIN_WAIT_MS) { save.join() } == null) {
            save.cancel()
            Log.w(TAG, "Main did not run the live workspace save within $PERSIST_LIVE_WORKSPACE_MAIN_WAIT_MS ms; replacing without it")
            return
        }
        awaitWorkspacePersisted()
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) { Log.e(TAG, "Settings flush failed", e) }
    }
}
