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
package net.bible.sharedcore.platform.desktop

import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopExitTasksTest {
    private fun <T> bounded(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test fun noTasksFinishAtOnce() = bounded {
        assertTrue(DesktopExitTasks().runAll(timeoutMs = 1))
    }

    @Test fun tasksRunInOrderAndAFailureDoesNotStopTheRest() = bounded {
        val tasks = DesktopExitTasks()
        val ran = mutableListOf<String>()
        tasks.register("a") { ran += "a" }
        tasks.register("b") { error("b failed") }
        tasks.register("c") { ran += "c" }
        assertTrue(tasks.runAll(timeoutMs = 5_000))
        assertEquals(listOf("a", "c"), ran)
    }

    @Test fun aRunFirstTaskRunsBeforeEarlierRegisteredOnes() = bounded {
        val tasks = DesktopExitTasks()
        val ran = mutableListOf<String>()
        tasks.register("slow") { ran += "slow" }
        tasks.register("first", runFirst = true) { ran += "first" }
        tasks.register("last") { ran += "last" }
        assertTrue(tasks.runAll(timeoutMs = 5_000))
        assertEquals(listOf("first", "slow", "last"), ran)
    }

    @Test fun aHangingTaskIsCutOffAtTheBoundAndTheRestSkipped() = bounded {
        val tasks = DesktopExitTasks()
        var after = false
        tasks.register("hang") { awaitCancellation() }
        tasks.register("after") { after = true }
        val start = System.nanoTime()
        assertFalse(tasks.runAll(timeoutMs = 200))
        val tookMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(tookMs < 5_000, "runAll took $tookMs ms")
        assertFalse(after)
    }

    @Test fun anUnregisteredTaskDoesNotRun() = bounded {
        val tasks = DesktopExitTasks()
        var ran = false
        val unregister = tasks.register("x") { ran = true }
        unregister()
        tasks.runAll(timeoutMs = 1_000)
        assertFalse(ran)
    }
    @Test fun syncingTurnsOnOnlyAfterBeginVisibleWork() = bounded {
        val tasks = DesktopExitTasks()
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        tasks.register("visible") { entered.complete(Unit); proceed.await(); beginVisibleWork(); awaitCancellation() }
        val run = CoroutineScope(Dispatchers.Default).async { tasks.runAll(5000) }
        entered.await()
        assertTrue(tasks.running.value, "Invisible work must still accept a pending skip")
        assertFalse(tasks.syncing.value)
        proceed.complete(Unit)
        withTimeout(2000) { tasks.syncing.first { it } }
        tasks.skipRemaining()
        assertFalse(run.await())
        assertFalse(tasks.syncing.value)
        assertFalse(tasks.running.value)
    }

    @Test fun syncingTurnsOffWhenTaskEndsFailsOrTimesOut() = bounded {
        for (kind in 0..2) {
            val tasks = DesktopExitTasks()
            tasks.register("visible") {
                beginVisibleWork()
                assertTrue(tasks.syncing.value)
                when (kind) { 1 -> error("failure"); 2 -> awaitCancellation() }
            }
            // Only the hanging branch tests the short deadline; logging a failure can exceed 200 ms under load.
            assertEquals(kind != 2, tasks.runAll(if (kind == 2) 200 else 5_000))
            assertFalse(tasks.syncing.value)
            assertFalse(tasks.running.value)
        }
    }

    @Test fun skipRemainingCancelsRunningTaskSkipsRestAndReturnsFalse() = bounded {
        val tasks = DesktopExitTasks()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var after = false
        tasks.register("blocking") { beginVisibleWork(); started.countDown(); release.await() }
        tasks.register("after") { after = true }
        val run = CoroutineScope(Dispatchers.Default).async { tasks.runAll(5000) }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            tasks.skipRemaining()
            assertFalse(withTimeout(1000) { run.await() })
            assertFalse(after)
            assertFalse(tasks.syncing.value)
        } finally { release.countDown() }
    }

    @Test fun skipRemainingWhenIdleIsNoOp() = bounded {
        val tasks = DesktopExitTasks()
        var ran = false
        tasks.register("later") { ran = true }
        tasks.skipRemaining()
        assertFalse(tasks.running.value)
        assertTrue(tasks.runAll(1000))
        assertTrue(ran, "An idle skip must not poison the next registered run")
    }

    @Test fun runAllAfterSkipRunsNormally() = bounded {
        val tasks = DesktopExitTasks()
        val started = CompletableDeferred<Unit>()
        var first = true
        tasks.register("task") { if (first) { first = false; started.complete(Unit); awaitCancellation() } }
        val run = CoroutineScope(Dispatchers.Default).async { tasks.runAll(5000) }
        started.await(); tasks.skipRemaining()
        assertFalse(run.await())
        assertTrue(tasks.runAll(1000))
    }

    @Test fun skippedWorkerCallbacksAndCompletionCannotChangeTheNextRun() = bounded {
        val tasks = DesktopExitTasks()
        val firstEntered = CompletableDeferred<() -> Unit>()
        val releaseFirst = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)
        val unregister = tasks.register("old blocked worker") {
            currentCoroutineContext().job.invokeOnCompletion { firstFinished.countDown() }
            firstEntered.complete(::beginVisibleWork)
            releaseFirst.await()
        }
        val first = CoroutineScope(Dispatchers.Default).async { tasks.runAll(5_000) }
        val oldVisible = firstEntered.await()
        tasks.skipRemaining()
        assertFalse(first.await())
        unregister()
        val secondEntered = CompletableDeferred<() -> Unit>()
        tasks.register("new worker") { secondEntered.complete(::beginVisibleWork); awaitCancellation() }
        val second = CoroutineScope(Dispatchers.Default).async { tasks.runAll(5_000) }
        try {
            val newVisible = secondEntered.await()
            oldVisible()
            assertFalse(tasks.syncing.value, "A stale marker cannot make the new run visible")
            newVisible()
            releaseFirst.countDown()
            assertTrue(firstFinished.await(2, TimeUnit.SECONDS))
            assertTrue(tasks.syncing.value, "The old worker cannot clear the new run's visibility")
            assertTrue(tasks.running.value, "Only releasing the current runner clears its lifecycle")
            tasks.skipRemaining()
            assertFalse(second.await())
            assertFalse(tasks.running.value)
        } finally {
            releaseFirst.countDown()
            tasks.skipRemaining()
            second.await()
        }
    }

    @Test fun cancelledTaskDoesNotReportSuccessfulCompletion() = bounded {
        val tasks = DesktopExitTasks()
        var after = false
        tasks.register("cancel") { throw CancellationException("task cancelled") }
        tasks.register("after") { after = true }
        assertFalse(tasks.runAll(1000))
        assertFalse(after)
    }

    @Test fun callerCancellationPropagates() = bounded {
        val tasks = DesktopExitTasks()
        val started = CompletableDeferred<Unit>()
        tasks.register("task") { started.complete(Unit); awaitCancellation() }
        val run = CoroutineScope(Dispatchers.Default).async { tasks.runAll(5000) }
        started.await(); run.cancel()
        assertFailsWith<CancellationException> { run.await() }
        Unit
    }
}
