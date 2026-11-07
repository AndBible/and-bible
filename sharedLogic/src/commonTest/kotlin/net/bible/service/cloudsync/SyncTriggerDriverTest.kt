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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncTriggerDriverTest {
    private val interval = 300L
    private val intervalMs = interval * 1000

    /** A fake platform: [syncs] counts started syncs; [passMs] is how long each one takes (virtual time). */
    private inner class Fixture(val test: TestScope) {
        var enabled = true
        var online = true
        var lastSync = 0L
        var passMs = 0L
        var syncs = 0
        var running = 0
        var maxRunning = 0
        var prepares = 0
        var failNext = false
        val allowanceEvents = mutableListOf<String>()
        var expire: (() -> Unit)? = null

        val policy = BackgroundSyncPolicy(
            syncEnabled = { enabled }, replacing = { false }, intervalSeconds = { interval },
            lastSynchronizedMs = { lastSync }, nowMs = { test.currentTime },
            signedIn = { true }, signIn = { true }, startSync = ::startSync,
        )

        private fun startSync(): Deferred<Boolean> {
            if (failNext) { failNext = false; error("boom") }
            syncs++; running++; maxRunning = maxOf(maxRunning, running)
            val done = CompletableDeferred<Boolean>()
            test.backgroundScope.launch {
                try { delay(passMs) } finally { running-- }
                lastSync = test.currentTime
                done.complete(true)
            }
            return done
        }

        fun driver(withAllowance: Boolean = false, gapMs: Long = SyncTriggerDriver.DEFAULT_NETWORK_RESTORE_GAP_MS) = SyncTriggerDriver(
            policy = policy,
            scope = test.backgroundScope,
            prepare = { prepares++ },
            networkAvailable = { online },
            networkRestoreGapMs = gapMs,
            backgroundAllowance = if (!withAllowance) null else SyncBackgroundAllowance { onExpire ->
                allowanceEvents += "begin"; expire = onExpire
                return@SyncBackgroundAllowance { allowanceEvents += "end" }
            },
        )
    }

    @Test fun openSyncsAtOnceEvenRightAfterASync() = runTest {
        val f = Fixture(this)
        f.lastSync = currentTime // a sync just happened: the interval check must not hold the open back
        f.driver().onForeground()
        runCurrent()
        assertEquals(1, f.syncs)
        assertEquals(1, f.prepares, "the workspace flush runs before the sync")
    }

    @Test fun timerPullsEveryIntervalWithoutLocalChanges() = runTest {
        val f = Fixture(this)
        f.driver().onForeground()
        runCurrent(); assertEquals(1, f.syncs)
        advanceTimeBy(intervalMs - 1); assertEquals(1, f.syncs, "not before the interval")
        advanceTimeBy(2); assertEquals(2, f.syncs, "the timer syncs with nothing local to send")
        advanceTimeBy(intervalMs); assertEquals(3, f.syncs)
    }

    @Test fun timerCountsTheIntervalFromTheLatestSync() = runTest {
        val f = Fixture(this)
        f.driver().onForeground()
        runCurrent(); assertEquals(1, f.syncs)
        advanceTimeBy(intervalMs / 2)
        f.lastSync = currentTime // a manual "sync now" half-way
        advanceTimeBy(intervalMs / 2 + 1); assertEquals(1, f.syncs, "the timer waits a full interval after the manual sync")
        advanceTimeBy(intervalMs / 2); assertEquals(2, f.syncs)
    }

    @Test fun backgroundStopsTheTimerAndSyncs() = runTest {
        val f = Fixture(this)
        val d = f.driver()
        d.onForeground(); runCurrent()
        d.onBackground(); runCurrent()
        assertEquals(2, f.syncs, "leaving the app syncs")
        assertFalse(d.timerRunning)
        advanceTimeBy(intervalMs * 3); assertEquals(2, f.syncs, "no timer passes in the background")
        d.onForeground(); runCurrent()
        assertEquals(3, f.syncs, "returning syncs again")
        assertTrue(d.timerRunning)
    }

    @Test fun repeatedForegroundWithoutBackgroundDoesNothing() = runTest {
        val f = Fixture(this)
        val d = f.driver()
        d.onForeground(); runCurrent()
        assertNull(d.onForeground())
        runCurrent()
        assertEquals(1, f.syncs)
    }

    @Test fun backgroundPassRunsInsideTheAllowance() = runTest {
        val f = Fixture(this); f.passMs = 5_000
        val d = f.driver(withAllowance = true)
        val job = d.onBackground()
        assertEquals(listOf("begin"), f.allowanceEvents, "begun synchronously, before the app can be suspended")
        advanceTimeBy(4_000); assertEquals(listOf("begin"), f.allowanceEvents)
        job.join()
        assertEquals(listOf("begin", "end"), f.allowanceEvents, "ended once, after the pass")
    }

    @Test fun allowanceExpiryCancelsThePassAndEndsOnce() = runTest {
        val f = Fixture(this); f.passMs = 60_000
        val d = f.driver(withAllowance = true)
        val job = d.onBackground()
        advanceTimeBy(1_000)
        f.expire!!()
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(listOf("begin", "end"), f.allowanceEvents)
    }

    @Test fun triggersDuringARunningPassJoinIt() = runTest {
        val f = Fixture(this); f.passMs = 10_000
        val d = f.driver(gapMs = 0)
        val open = d.onForeground()!!
        runCurrent()
        advanceTimeBy(5_000)
        assertSame(open, d.onNetworkAvailable(), "a network restore during the open pass joins it")
        advanceTimeBy(20_000)
        assertEquals(1, f.syncs)
        assertEquals(1, f.maxRunning)
    }

    @Test fun backgroundDuringARunningPassQueuesOneFollowUp() = runTest {
        val f = Fixture(this); f.passMs = 10_000
        val d = f.driver()
        d.onForeground(); runCurrent()
        val first = d.onBackground()
        val second = d.onBackground()
        assertSame(first, second, "a second leave joins the queued follow-up")
        advanceTimeBy(5_000); assertEquals(1, f.syncs, "the follow-up waits for the running pass")
        advanceTimeBy(20_000)
        assertEquals(2, f.syncs, "changes made during the running pass still get pushed")
        assertEquals(1, f.maxRunning, "passes never overlap")
    }

    @Test fun networkRestoreSyncsInTheForegroundOnly() = runTest {
        val f = Fixture(this)
        val d = f.driver()
        assertNull(d.onNetworkAvailable(), "ignored before the app is in the foreground")
        d.onForeground(); runCurrent()
        assertNull(d.onNetworkAvailable(), "ignored right after the open pass")
        advanceTimeBy(SyncTriggerDriver.DEFAULT_NETWORK_RESTORE_GAP_MS + 1)
        d.onNetworkAvailable(); runCurrent()
        assertEquals(2, f.syncs)
        d.onBackground(); runCurrent()
        advanceTimeBy(SyncTriggerDriver.DEFAULT_NETWORK_RESTORE_GAP_MS + 1)
        assertNull(d.onNetworkAvailable(), "ignored in the background")
    }

    @Test fun offlineOrDisabledSkipsThePassButKeepsTheTimer() = runTest {
        val f = Fixture(this)
        f.online = false
        val d = f.driver()
        d.onForeground(); runCurrent()
        assertEquals(0, f.syncs); assertEquals(0, f.prepares, "no workspace flush for a pass that cannot sync")
        f.online = true; f.enabled = false
        advanceTimeBy(intervalMs + 1); assertEquals(0, f.syncs)
        f.enabled = true
        advanceTimeBy(intervalMs); assertEquals(1, f.syncs, "the timer kept going and syncs once possible")
    }

    @Test fun aFailingPassDoesNotStopTheTimer() = runTest {
        val f = Fixture(this)
        f.failNext = true
        val d = f.driver()
        d.onForeground(); runCurrent()
        assertEquals(0, f.syncs)
        advanceTimeBy(intervalMs + 1)
        assertEquals(1, f.syncs)
        assertTrue(d.timerRunning)
    }

    @Test fun stopEndsTheTimerWithoutAPass() = runTest {
        val f = Fixture(this)
        val d = f.driver()
        d.onForeground(); runCurrent()
        d.stop()
        advanceTimeBy(intervalMs * 2)
        assertEquals(1, f.syncs)
        assertFalse(d.isForeground)
    }
}

class NetworkRestoreEdgeTest {
    @Test fun onlyAnUnusableToUsableChangeIsARestore() {
        val edge = NetworkRestoreEdge()
        assertTrue(edge.available, "no report yet: do not block passes")
        assertFalse(edge.report(true), "the monitor's first report is the current state, not a restore")
        assertFalse(edge.report(true))
        assertFalse(edge.report(false)); assertFalse(edge.available)
        assertFalse(edge.report(false))
        assertTrue(edge.report(true)); assertTrue(edge.available)
        assertFalse(edge.report(true))
    }

    @Test fun startingOfflineThenComingOnlineIsARestore() {
        val edge = NetworkRestoreEdge()
        assertFalse(edge.report(false))
        assertTrue(edge.report(true))
    }
}
