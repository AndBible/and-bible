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
package net.bible.desktop

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExitWatchdogTest {
    private val spawned = mutableListOf<List<String>>()
    private fun watchdog(isLinux: Boolean = true) =
        ExitWatchdog(pid = 4242, startTime = { "777" }, isLinux = isLinux, spawn = { spawned += it })

    @Test fun neverArmsBeforePersistence() {
        val w = watchdog()
        assertFalse(w.arm())
        assertTrue(spawned.isEmpty())
    }

    @Test fun armsOnceAfterPersistence() {
        val w = watchdog()
        w.markPersisted()
        assertTrue(w.arm())
        assertFalse(w.arm())
        assertEquals(1, spawned.size)
        val script = spawned.single().last()
        assertTrue("sleep 5;" in script && "kill -KILL 4242" in script && "\"777\"" in script, script)
    }

    @Test fun nothingOffLinux() {
        val w = watchdog(isLinux = false)
        w.markPersisted()
        assertFalse(w.arm())
        assertTrue(spawned.isEmpty())
    }

    /** The real script against a stand-in process: killed after the delay when the start time matches, spared otherwise. */
    @Test fun scriptKillsOnlyTheSameProcessAfterTheDelay() {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return
        val victim = ProcessBuilder("sleep", "60").start()
        val bystander = ProcessBuilder("sleep", "60").start()
        try {
            val start = ExitWatchdog.processStartTime(victim.pid())!!
            fun run(pid: Long, startTime: String) = ExitWatchdog(pid, { startTime }, true, delaySeconds = 1, spawn = {
                ProcessBuilder(it).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }).apply { markPersisted(); arm() }
            run(victim.pid(), start)
            run(bystander.pid(), "1") // a different start time: a reused pid must not be killed
            assertTrue(victim.isAlive, "killed before the delay")
            assertTrue(victim.waitFor(10, TimeUnit.SECONDS), "not killed after the delay")
            assertEquals(137, victim.exitValue())
            Thread.sleep(1_500)
            assertTrue(bystander.isAlive, "a process with another start time was killed")
        } finally {
            victim.destroyForcibly()
            bystander.destroyForcibly()
        }
    }
}
