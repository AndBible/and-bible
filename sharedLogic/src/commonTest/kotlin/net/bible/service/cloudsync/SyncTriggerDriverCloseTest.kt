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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression for the :app suite's sync-timer leak: the timer loop's first action is a blocking settings read on
 * `Dispatchers.IO`. `stop()`/`close()` only cancel, so a read already under way outlives it (and, in tests, outlives the
 * Application it reads from). [SyncTriggerDriver.stopAndJoin] must not return before such a read has finished.
 */
class SyncTriggerDriverCloseTest {
    @Test fun stopAndJoinWaitsForATimerReadThatIsAlreadyRunning() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = atomic(false)
        val readFinished = atomic(false)
        val policy = BackgroundSyncPolicy(
            syncEnabled = { false }, replacing = { false }, intervalSeconds = { 300 },
            lastSynchronizedMs = {
                entered.complete(Unit)
                while (!release.value) { /* a blocking settings read: cancellation cannot interrupt it */ }
                readFinished.value = true
                0L
            },
            nowMs = { 1L }, signedIn = { true }, signIn = { true },
            startSync = { error("no sync expected") },
        )
        withContext(Dispatchers.IO) {
            val driver = SyncTriggerDriver(policy, CoroutineScope(SupervisorJob() + Dispatchers.IO))
            driver.onForeground()
            entered.await()
            launch { delay(200); release.value = true }
            driver.stopAndJoin()
            assertTrue(readFinished.value, "stopAndJoin returned while the timer's settings read was still running")
            assertFalse(driver.timerRunning)
            release.value = true
            driver.onForeground()
            assertTrue(driver.timerRunning, "the driver must be reusable after stopAndJoin (Koin single outlives tests)")
            driver.close()
        }
    }
}
