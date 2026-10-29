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
package net.bible.sharedcore.platform.desktop.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSyncSchedulerTest {
    @Test fun aSecondStartWhileRunningJoinsTheSamePass() = runTest {
        var runs = 0
        val gate = CompletableDeferred<Unit>()
        val scheduler = DesktopSyncScheduler(backgroundScope, { runs++; gate.await(); true })
        val a = scheduler.startDatabaseSync()
        val b = scheduler.startDatabaseSync()
        advanceUntilIdle()
        assertSame(a, b)
        gate.complete(Unit)
        assertTrue(a.await())
        assertEquals(1, runs)
    }

    @Test fun aFailingPassAnswersFalseAndALaterPassRunsAgain() = runTest {
        var runs = 0
        val scheduler = DesktopSyncScheduler(backgroundScope, { runs++; if (runs == 1) error("offline"); true })
        assertFalse(scheduler.startDatabaseSync().await())
        assertTrue(scheduler.startDatabaseSync().await())
        assertEquals(2, runs)
    }

    @Test fun backgroundPassUsesItsOwnActionAndStopCancelsOnlyThatPass() = runTest {
        val gate = CompletableDeferred<Unit>()
        val scheduler = DesktopSyncScheduler(backgroundScope, { true })
        val pass = scheduler.startBackgroundDatabaseSync { gate.await(); true }
        advanceUntilIdle()
        scheduler.stop(pass)
        assertFalse(pass.await())
    }

    @Test fun backgroundRefreshIsANoOp() {
        val refresh = DesktopBackgroundRefresh()
        refresh.register { true }; refresh.schedule(60); refresh.cancel()
    }
}
