/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedlogic.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class MonitorsTest {
    private fun returnFromInside(lock: Any): Int {
        withMonitor(lock) { return 1 }
        return 2
    }

    @Test fun reentrantNestingDoesNotDeadlock() {
        val a = Any()
        assertEquals(42, withMonitor(a) { withMonitor(a) { 42 } })
    }

    @Test fun nonLocalReturnFromBlockReleasesTheLock() = runTest {
        val lock = Any()
        assertEquals(1, returnFromInside(lock))
        val got = withContext(Dispatchers.Default) {
            withTimeout(1000) { withMonitor(lock) { 7 } }
        }
        assertEquals(7, got)
    }

    @Test fun waitReleasesAndNotifyWakes() = runTest {
        val lock = Any()
        var flag = false
        var waiting = false
        val waiter = async(Dispatchers.Default) {
            withMonitor(lock) {
                waiting = true
                while (!flag) monitorWait(lock, 10_000)
                flag
            }
        }
        val setter = async(Dispatchers.Default) {
            while (!withMonitor(lock) { waiting }) { }
            withMonitor(lock) {
                flag = true
                monitorNotifyAll(lock)
            }
        }
        withContext(Dispatchers.Default) {
            withTimeout(2000) {
                setter.await()
                assertTrue(waiter.await())
            }
        }
    }

    @Test fun waitWithTimeoutReturnsAfterTimeout() = runTest {
        val lock = Any()
        val elapsedMs = withContext(Dispatchers.Default) {
            val mark = TimeSource.Monotonic.markNow()
            withMonitor(lock) { monitorWait(lock, 50) }
            mark.elapsedNow().inWholeMilliseconds
        }
        assertTrue(elapsedMs >= 40, "elapsed $elapsedMs ms")
    }
}
