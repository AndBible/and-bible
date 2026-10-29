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
package net.bible.service.cloudsync.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents

/**
 * Runs the desktop's background sync: one pass when startup is ready, then one every [BackgroundSyncPolicy.nextEarliestSeconds],
 * all in [scope] (never on the UI thread). Passes never overlap. On the exit it stops the periodic loop and, as an exit
 * task, waits for the running pass (or runs [closePass]) for at most [closeWaitMs]; the exit-task runner's own bound is
 * 10 s, so the default 9 s lets this task finish inside it.
 *
 * Every failure (offline, sign-in, exceptions) is logged and swallowed: the app stays usable and the loop continues. When the
 * wait runs out the pass is NOT cancelled (database work cannot always be cancelled cleanly); the process exits and the
 * hoisted CloudSync recovers an interrupted merge on the next start.
 *
 * Constructing the driver starts it (it registers its exit task and lifecycle observer and launches the loop), so bind it
 * as an eager Koin single. Nothing here touches a database until [awaitReady] returns.
 *
 * @param closePass the on-close work (push local changes when signed in); null result means "nothing to do".
 * @param databasesReady false when the databases are closed or being replaced; the exit task then returns at once.
 */
class DesktopSyncDriver(
    private val policy: BackgroundSyncPolicy,
    private val closePass: suspend () -> Boolean?,
    exitTasks: DesktopExitTasks,
    events: DesktopLifecycleEvents,
    private val scope: CoroutineScope,
    awaitReady: suspend () -> Unit,
    private val closeWaitMs: Long = DEFAULT_CLOSE_WAIT_MS,
    private val databasesReady: () -> Boolean = { true },
) {
    /** Held for the whole of a pass: a second pass (periodic or on-close) waits for it instead of overlapping. */
    private val passLock = Mutex()
    @Volatile private var stopped = false
    /** The latest periodic pass job; the exit task joins it instead of starting a second pass. */
    @Volatile private var current: Job? = null
    private val loop: Job

    init {
        events.observe { if (it == DesktopLifecycleEvent.Background) stop() }
        exitTasks.register("cloud-sync") { closeSync() }
        loop = scope.launch {
            try {
                awaitReady()
                while (!stopped) {
                    launchPass().join()
                    if (stopped) break
                    delay(policy.nextEarliestSeconds() * 1000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Sync loop failed", e)
            }
        }
    }

    private fun stop() {
        stopped = true
        // Cancels only the wait/delay: a running pass is its own job and finishes (or is waited for on close).
        loop.cancel()
    }

    /** A periodic pass in its own job, so cancelling the loop never cancels database work that is under way. */
    private fun launchPass(): Job = scope.launch {
        passLock.withLock {
            if (stopped) return@launch // no new pass once the exit has begun
            try {
                if (!policy.runOnce()) Log.i(TAG, "Sync pass did not complete (sign-in or sync failed)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Sync pass failed: ${e.message}", e)
            }
        }
    }.also { current = it }

    private suspend fun closeSync() {
        stop()
        if (!databasesReady()) return
        val running = current?.takeIf { it.isActive }
        if (running != null) {
            // A pass is running: only wait for it. It is never cancelled, and no second pass starts behind it.
            waitBounded(running)
            return
        }
        if (!passLock.tryLock()) { // a pass slipped in between the check and here
            current?.let { waitBounded(it) }
            return
        }
        val began = java.util.concurrent.atomic.AtomicBoolean(false)
        val close = scope.launch(start = CoroutineStart.LAZY) {
            began.set(true)
            try {
                closePass()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Close pass failed: ${e.message}", e)
            }
        }
        close.invokeOnCompletion { passLock.unlock() }
        close.start()
        // A close pass that has not even started when the wait ends is dropped; one that is running is left alone.
        if (!waitBounded(close) && !began.get()) close.cancel()
    }

    /** True when [job] completed within the close bound. Timing out does not cancel [job]. */
    private suspend fun waitBounded(job: Job): Boolean {
        val done = withTimeoutOrNull(closeWaitMs) { job.join(); true } == true
        if (!done) Log.w(TAG, "Cloud sync did not finish within $closeWaitMs ms; leaving it to the next start")
        return done
    }

    companion object {
        const val DEFAULT_CLOSE_WAIT_MS = 9_000L
        private const val TAG = "DesktopSyncDriver"
    }
}
