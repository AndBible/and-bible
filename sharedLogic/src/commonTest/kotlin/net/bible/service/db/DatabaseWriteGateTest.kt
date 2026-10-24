/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DatabaseWriteGateTest {
    @Test
    fun passRunsAtOnceWhenOpen() = runTest {
        val gate = DatabaseWriteGate()
        assertEquals(42, withTimeout(1_000) { gate.pass { 42 } })
    }

    @Test
    fun closeAndDrainWaitsForAnInFlightPass() = runTest {
        val gate = DatabaseWriteGate()
        val hold = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        launch { gate.pass { entered.complete(Unit); hold.await() } }
        entered.await()
        var drained = false
        launch { gate.closeAndDrain(); drained = true }
        runCurrent()
        assertFalse(drained, "closeAndDrain must wait for the in-flight pass")
        hold.complete(Unit)
        runCurrent()
        assertTrue(drained)
    }

    @Test
    fun passSubmittedWhileClosedRunsAfterOpen() = runTest {
        val gate = DatabaseWriteGate()
        gate.closeAndDrain()
        var ran = false
        val job = launch { gate.pass { ran = true } }
        runCurrent()
        assertFalse(ran, "a pass submitted while closed must wait")
        gate.open()
        runCurrent()
        assertTrue(ran)
        job.join()
    }

    @Test
    fun nestedCloseOpensOnlyAtDepthZero() = runTest {
        val gate = DatabaseWriteGate()
        gate.closeAndDrain()
        gate.closeAndDrain()
        var ran = false
        launch { gate.pass { ran = true } }
        gate.open()
        runCurrent()
        assertFalse(ran, "one open of two closes must keep the gate closed")
        gate.open()
        runCurrent()
        assertTrue(ran)
    }

    @Test
    fun cancelledDrainLeavesTheGateOpen() = runTest {
        val gate = DatabaseWriteGate()
        val hold = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        launch { gate.pass { entered.complete(Unit); hold.await() } }
        entered.await()
        val drain = async(start = CoroutineStart.UNDISPATCHED) { gate.closeAndDrain() }
        runCurrent()
        drain.cancel()
        runCurrent()
        hold.complete(Unit)
        runCurrent()
        assertEquals(1, withTimeout(1_000) { gate.pass { 1 } }, "a cancelled drain must not leave the gate closed")
    }

    @Test
    fun resetForTestOpensAClosedGate() = runTest {
        val gate = DatabaseWriteGate()
        gate.closeAndDrain()
        gate.resetForTest()
        assertEquals(1, withTimeout(1_000) { gate.pass { 1 } })
    }

    @Test
    fun resetWhileAPassIsHeldDoesNotBreakLaterDrains() = runTest {
        val gate = DatabaseWriteGate()
        val hold = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        launch { gate.pass { entered.complete(Unit); hold.await() } }
        entered.await()
        gate.resetForTest()
        hold.complete(Unit)
        runCurrent()
        withTimeout(1_000) { gate.closeAndDrain() }
        gate.open()
        assertEquals(1, withTimeout(1_000) { gate.pass { 1 } })
    }
}
