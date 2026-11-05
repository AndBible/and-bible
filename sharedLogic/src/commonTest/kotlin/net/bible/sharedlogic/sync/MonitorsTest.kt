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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
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

    @Test fun unrelatedTargetsDoNotBlockEachOtherEvenWhenEqual() {
        class EqualTarget {
            override fun equals(other: Any?) = other is EqualTarget
            override fun hashCode() = error("monitor identity must not call user hashCode")
        }
        val a = EqualTarget()
        val b = EqualTarget()
        val entered = atomic(false)
        val finished = atomic(false)
        val acquired = atomic(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            withMonitor(a) {
                scope.launch {
                    entered.value = true
                    withMonitor(b) { acquired.value = true }
                    finished.value = true
                }
                val mark = TimeSource.Monotonic.markNow()
                while (!finished.value && mark.elapsedNow() < 5.seconds) { }
                assertTrue(entered.value, "second worker was not scheduled")
                assertTrue(acquired.value, "an unrelated equal target blocked behind the held monitor (#3978)")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test fun waitReleasesEveryReentrantAcquisitionAndRestoresDepth() = runTest {
        val target = Any()
        val waiting = atomic(false)
        var signalled = false
        val waiter = async(Dispatchers.Default) {
            withMonitor(target) {
                withMonitor(target) {
                    waiting.value = true
                    while (!signalled) monitorWait(target, 1000)
                    // A second wait proves the first restored the recursion depth.
                    monitorWait(target, 10)
                }
            }
        }
        val setter = async(Dispatchers.Default) {
            while (!waiting.value) { }
            withMonitor(target) {
                signalled = true
                monitorNotifyAll(target)
            }
        }
        setter.await()
        waiter.await()
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
