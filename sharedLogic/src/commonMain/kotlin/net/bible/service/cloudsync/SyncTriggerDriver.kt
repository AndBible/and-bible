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
package net.bible.service.cloudsync

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log

/** What asked for a [SyncTriggerDriver] pass. */
enum class SyncTrigger {
    /** The app opened or returned to the foreground. */
    Open,

    /** The interval timer while the app is in the foreground. */
    Timer,

    /** The app is being left (background / quit). */
    Background,

    /** The platform reported that the network came back. */
    NetworkRestored,
}

/**
 * An OS execution allowance around the leave-the-app pass (iOS `beginBackgroundTask`). [begin] is called synchronously
 * inside [SyncTriggerDriver.onBackground], so the platform can grant the time before the app is suspended; [onExpire]
 * runs when the platform takes the time back. The returned function ends the allowance; the driver calls it once.
 */
fun interface SyncBackgroundAllowance {
    fun begin(onExpire: () -> Unit): () -> Unit
}

/**
 * The cloud-sync trigger logic shared by Android, iOS and desktop (#4025). The platform reports lifecycle and network
 * events; the driver decides when a sync pass runs:
 *
 * | Trigger                  | Pass                                                                              |
 * |--------------------------|-----------------------------------------------------------------------------------|
 * | [onForeground]           | at once (open / resume), then the interval timer starts                           |
 * | timer                    | every `cloud_sync_interval` after the latest pass or successful sync; also pulls  |
 * | [onBackground]           | at once, inside the [backgroundAllowance]; the timer stops                         |
 * | [onNetworkAvailable]     | at once while in the foreground, unless a pass started within [networkRestoreGapMs] |
 *
 * Every pass is forced past [BackgroundSyncPolicy]'s interval check (the driver schedules by the interval itself), and
 * runs only while [BackgroundSyncPolicy.canRun] and [networkAvailable] hold, after [prepare] (Android flushes the open
 * workspace first).
 *
 * **One pass at a time.** A trigger that arrives while a pass runs joins it. Only [onBackground] queues ONE follow-up
 * pass instead, because the user may have changed something after the running pass took its snapshot; triggers that
 * arrive while the follow-up waits join the follow-up. Each pass runs in its own job in [scope], so stopping the timer
 * never cancels database work that is under way.
 *
 * @param passWrapper wraps each pass; it calls `run` only when the pass should do its work (desktop runs passes under its
 *   close lock and skips them once the exit began). [Pass.ran] is set when `run` is called.
 */
class SyncTriggerDriver(
    private val policy: BackgroundSyncPolicy,
    private val scope: CoroutineScope,
    private val prepare: suspend () -> Unit = {},
    private val networkAvailable: () -> Boolean = { true },
    private val backgroundAllowance: SyncBackgroundAllowance? = null,
    private val networkRestoreGapMs: Long = DEFAULT_NETWORK_RESTORE_GAP_MS,
    private val passWrapper: suspend (pass: Pass, run: suspend () -> Unit) -> Unit = { _, run -> run() },
) {
    /** One sync pass: its job, what started it, and whether it actually did its work. */
    class Pass internal constructor(val trigger: SyncTrigger) {
        lateinit var job: Job
            internal set
        private val ranFlag = atomic(false)
        val ran: Boolean get() = ranFlag.value
        internal fun markRan() { ranFlag.value = true }
    }

    private val lock = SynchronizedObject()
    private var running: Pass? = null
    private var queued: Pass? = null
    private var timer: Job? = null
    private var foreground = false

    /** Policy time when the latest pass started or ended; the timer counts the interval from it. */
    private val lastPassMarkMs = atomic(0L)
    /** Policy time when the latest pass started; [NEVER] before the first. The network-restore gap counts from it. */
    private val lastPassStartMs = atomic(NEVER)

    /** Test seam: runs after a pass is published and before its job starts. */
    internal var beforePassStart: () -> Unit = {}

    /** Whether the app is in the foreground as far as the driver knows (between [onForeground] and [onBackground]/[stop]). */
    val isForeground: Boolean get() = synchronized(lock) { foreground }

    /** Whether the interval timer is running. */
    val timerRunning: Boolean get() = synchronized(lock) { timer?.isActive == true }

    /** The latest pass (queued, running or finished), for a caller that must wait for it (the desktop exit). */
    val latestPass: Pass? get() = synchronized(lock) { queued ?: running }

    /**
     * The app opened or returned to the foreground: a pass at once, and the interval timer. A repeated call without an
     * [onBackground] in between does nothing (iOS posts "became active" also after a notification-centre pull-down).
     *
     * @return the pass that serves this trigger, or null when the call did nothing
     */
    fun onForeground(): Job? {
        synchronized(lock) {
            if (foreground) return null
            foreground = true
            timer?.cancel()
            timer = scope.launch { timerLoop() }
        }
        return trigger(SyncTrigger.Open).job
    }

    /**
     * The app is being left: the timer stops and a pass runs inside the [backgroundAllowance], started synchronously
     * here. The allowance's expiry cancels the pass.
     *
     * @return the pass's job; its completion ends the allowance
     */
    fun onBackground(): Job {
        stopTimer()
        val pass = trigger(SyncTrigger.Background)
        val allowance = backgroundAllowance ?: return pass.job
        val ended = atomic(false)
        var end: (() -> Unit)? = null
        fun endOnce() {
            val e = end ?: return
            if (ended.compareAndSet(false, true)) runCatching { e() }.onFailure { Log.w(TAG, "Ending the background allowance failed", it) }
        }
        var expiredEarly = false
        end = try {
            allowance.begin {
                if (end == null) expiredEarly = true
                pass.job.cancel()
                endOnce()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Background allowance failed to begin", e)
            null
        }
        if (expiredEarly) endOnce()
        pass.job.invokeOnCompletion { endOnce() }
        return pass.job
    }

    /**
     * The platform reported that the network is back: a pass while in the foreground, unless one started within
     * [networkRestoreGapMs] (opening the app and the first network callback arrive together).
     *
     * @return the pass's job, or null when the trigger was ignored
     */
    fun onNetworkAvailable(): Job? {
        if (!isForeground) return null
        val last = lastPassStartMs.value
        if (last != NEVER && policy.now() - last < networkRestoreGapMs) return null
        return trigger(SyncTrigger.NetworkRestored).job
    }

    /** Stops the timer without a pass (the host is destroyed, or the desktop exit owns its own close pass). */
    fun stop() = stopTimer()

    private fun stopTimer() {
        synchronized(lock) {
            foreground = false
            timer?.cancel()
            timer = null
        }
    }

    private suspend fun timerLoop() {
        while (true) {
            val due = maxOf(policy.lastSyncMs(), lastPassMarkMs.value) + policy.intervalMs()
            val wait = (due - policy.now()).coerceAtMost(policy.intervalMs())
            if (wait > 0) {
                delay(wait.coerceAtLeast(MIN_TIMER_WAIT_MS))
                continue
            }
            trigger(SyncTrigger.Timer).job.join()
        }
    }

    /** Joins the queued or running pass, queues a follow-up for [SyncTrigger.Background], or starts a new pass. */
    private fun trigger(kind: SyncTrigger): Pass {
        val pass = synchronized(lock) {
            queued?.takeUnless { it.job.isCompleted }?.let { return it }
            val current = running?.takeUnless { it.job.isCompleted }
            if (current != null && kind != SyncTrigger.Background) return current
            Pass(kind).also { pass ->
                pass.job = scope.launch(start = CoroutineStart.LAZY) {
                    current?.job?.join()
                    synchronized(lock) {
                        running = pass
                        if (queued === pass) queued = null
                    }
                    runPass(pass)
                }
                if (current != null) queued = pass else running = pass
            }
        }
        beforePassStart()
        pass.job.start()
        return pass
    }

    private suspend fun runPass(pass: Pass) {
        try {
            passWrapper(pass) {
                pass.markRan()
                val start = policy.now()
                lastPassStartMs.value = start
                lastPassMarkMs.value = start
                try {
                    if (!policy.canRun() || !networkAvailable()) return@passWrapper
                    prepare()
                    if (!policy.runOnce(force = true)) Log.i(TAG, "${pass.trigger} sync did not complete (sign-in or sync failed)")
                } finally {
                    lastPassMarkMs.value = policy.now()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "${pass.trigger} sync failed: ${e.message}", e)
        }
    }

    companion object {
        const val DEFAULT_NETWORK_RESTORE_GAP_MS = 30_000L
        private const val MIN_TIMER_WAIT_MS = 1_000L
        private const val NEVER = Long.MIN_VALUE
        private const val TAG = "SyncTriggerDriver"
    }
}

/**
 * Turns a platform's stream of "is the network usable" reports into "the network came back" edges for
 * [SyncTriggerDriver.onNetworkAvailable]. The first report only records the state (a monitor reports the current path
 * when it starts, which is not a restore); afterwards only an unusable-to-usable change counts.
 */
class NetworkRestoreEdge {
    private val lock = SynchronizedObject()
    private var known: Boolean? = null

    /** The latest report; true until the first one, so a platform without a report never blocks a pass. */
    val available: Boolean get() = synchronized(lock) { known != false }

    /** Records [usable]; returns true when this report is a restore. */
    fun report(usable: Boolean): Boolean = synchronized(lock) {
        val restored = known == false && usable
        known = usable
        restored
    }
}
