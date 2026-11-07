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
import java.util.concurrent.atomic.AtomicBoolean
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.service.cloudsync.SyncTriggerDriver
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.ExitTaskScope
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents

/**
 * The desktop's cloud-sync driver: the shared [SyncTriggerDriver] (#4025) for the passes while the app runs (one when
 * startup is ready, then one every `cloud_sync_interval`, also pulling remote changes), plus the desktop's own exit pass.
 * Everything runs in [scope] (never on the UI thread). Passes never overlap. On the exit it stops the timer and, as an exit
 * task, waits for the pass that is not yet completed (or runs [closePass], retrying the lock 3 times over 2 s) for at
 * most [closeWaitMs]; the default 30 s fits inside the exit-task runner's 31 s budget.
 *
 * Window focus is not "leaving the app" on the desktop: [DesktopLifecycleEvent.Background] is emitted only on exit, and
 * the exit pass here is the leave-the-app trigger (it pushes only unsent changes, so a quick quit stays quick).
 *
 * Every failure (offline, sign-in, exceptions) is logged and swallowed: the app stays usable and the timer continues. When
 * the wait runs out the pass is NOT cancelled (database work cannot always be cancelled cleanly); the process exits and the
 * hoisted CloudSync recovers an interrupted merge on the next start.
 *
 * Constructing the driver starts it (it registers its exit task and lifecycle observer and launches the start-up wait), so
 * bind it as an eager Koin single. Nothing here touches a database until [awaitReady] returns.
 *
 * @param closePass the on-close work (push local changes when signed in); null result means "nothing to do".
 * @param databasesReady false when the databases are closed or being replaced; the exit task then returns at once.
 * @param externalPass a database pass started outside the driver (for example from the settings screen) that is still
 *   running; the exit task waits for it within the same bound.
 */
class DesktopSyncDriver(
    policy: BackgroundSyncPolicy,
    private val closePass: suspend (onVisibleWork: () -> Unit) -> Boolean?,
    exitTasks: DesktopExitTasks,
    events: DesktopLifecycleEvents,
    private val scope: CoroutineScope,
    awaitReady: suspend () -> Unit,
    private val closeWaitMs: Long = DEFAULT_CLOSE_WAIT_MS,
    private val databasesReady: () -> Boolean = { true },
    private val externalPass: () -> Job? = { null },
) {
    /** Held for the whole of a pass: a second pass (periodic or on-close) waits for it instead of overlapping. */
    internal val passLock = Mutex()
    @Volatile private var stopped = false

    /** The shared trigger logic; each of its passes runs under [passLock] and is skipped once the exit has begun. */
    private val triggers = SyncTriggerDriver(policy, scope, passWrapper = { _, run ->
        passLock.withLock { if (!stopped) run() }
    })

    @Volatile private var closeJob: Job? = null
    private val closeBegan = AtomicBoolean(false)
    private val startup: Job

    /** Test seam: runs between publishing a pass and starting its job, the window where the pass is still New. */
    internal var beforePassStart: () -> Unit
        get() = triggers.beforePassStart
        set(value) { triggers.beforePassStart = value }

    /** The latest pass; the exit task joins it instead of starting a second pass. */
    private val current: SyncTriggerDriver.Pass? get() = triggers.latestPass

    /** Test seam: the job of the latest pass. */
    internal val currentPassJob: Job? get() = current?.job

    init {
        events.observe { if (it == DesktopLifecycleEvent.Background) stop() }
        exitTasks.register("cloud-sync") { closeSync(this) }
        startup = scope.launch {
            try {
                awaitReady()
                if (!stopped) triggers.onForeground()
                if (stopped) triggers.stop() // an exit that raced the start must not leave the timer running
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Sync start failed", e)
            }
        }
    }

    private fun stop() {
        stopped = true
        // Cancels only the start-up wait and the timer: a running pass is its own job and finishes (or is waited for on close).
        startup.cancel()
        triggers.stop()
    }

    internal suspend fun closeSync(task: ExitTaskScope) {
        stop()
        if (!databasesReady()) return
        val finished = withTimeoutOrNull(closeWaitMs) {
            // A pass that is running is only waited for: never cancelled, and no second pass starts behind it.
            val pass = current
            if (pass != null && !pass.job.isCompleted) { // also a pass that is still Starting or being cancelled
                task.beginVisibleWork()
                pass.job.join()
                if (pass.ran) { externalPass()?.takeUnless { it.isCompleted }?.join(); return@withTimeoutOrNull true }
                // It saw the stop before it began: it did nothing, so the close pass is still owed.
            }
            externalPass()?.takeUnless { it.isCompleted }?.let {
                task.beginVisibleWork()
                it.join()
            }
            runClosePass(task::beginVisibleWork)
            true
        }
        if (finished == null) {
            Log.w(TAG, "Cloud sync did not finish within $closeWaitMs ms; leaving it to the next start")
            // A close pass that has not even started when the wait ends is dropped; one that is running is left alone.
            if (!closeBegan.get()) closeJob?.cancel()
        }
    }

    /**
     * The on-close pass in its own job under [passLock]. A pass that is exiting on the stop may still hold the lock for a
     * moment: retry the lock (3 tries over 2 s) instead of dropping the final push. A holder that really ran a pass is
     * only waited for.
     */
    private suspend fun runClosePass(onVisibleWork: () -> Unit) {
        var attempt = 1
        while (!passLock.tryLock()) {
            val pass = current
            val joined = pass != null && !pass.job.isCompleted
            if (joined) pass!!.job.join()
            if (pass?.ran == true) return // it did the sync work; nothing more is owed
            if (attempt++ >= CLOSE_LOCK_TRIES) {
                Log.w(TAG, "Close pass could not get the sync lock; leaving the push to the next start")
                return
            }
            if (!joined) delay(CLOSE_LOCK_RETRY_MS) // after a join the holder is gone: try the lock at once
        }
        val close = scope.launch(start = CoroutineStart.LAZY) {
            closeBegan.set(true)
            try {
                closePass(onVisibleWork)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Close pass failed: ${e.message}", e)
            }
        }
        closeJob = close
        close.invokeOnCompletion { passLock.unlock() }
        close.start()
        close.join()
    }

    companion object {
        const val DEFAULT_CLOSE_WAIT_MS = 30_000L
        private const val CLOSE_LOCK_TRIES = 3
        private const val CLOSE_LOCK_RETRY_MS = 1_000L
        private const val TAG = "DesktopSyncDriver"
    }
}
