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

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
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
}
