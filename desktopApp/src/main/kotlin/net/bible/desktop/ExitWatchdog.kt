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

import java.io.File

/**
 * Last-resort exit on Linux. The JVM's exit can deadlock in `_dl_fini`: libGLX (loaded by skiko's GL probe) closes the
 * X display and waits for the Xlib lock, which AWT-XAWT holds while frozen at the exit safepoint. Once the exit has
 * begun every Java thread is stopped at that safepoint, and `Runtime.halt` runs `_dl_fini` too, so neither a watchdog
 * thread nor halt can help. The watchdog is therefore a separate `sh` process: after [delaySeconds] it kills this
 * process with SIGKILL, but only if a process with this pid AND this start time (`/proc/<pid>/stat` field 22) still
 * exists, so a reused pid is never hit. On a clean exit it finds nothing and does nothing.
 *
 * [arm] spawns it at most once, and only after [markPersisted]: never before the workspace save and the settings flush.
 */
class ExitWatchdog(
    private val pid: Long = ProcessHandle.current().pid(),
    private val startTime: () -> String? = { processStartTime(pid) },
    private val isLinux: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("linux"),
    private val delaySeconds: Int = 5,
    private val spawn: (List<String>) -> Unit = { command ->
        ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.INHERIT).start()
    },
) {
    @Volatile private var persisted = false
    @Volatile private var armed = false

    /** The workspace save, the settings flush and the CEF shutdown have finished. */
    fun markPersisted() {
        persisted = true
    }

    /** Spawns the watchdog process; false (and nothing spawned) off Linux, before [markPersisted], or a second time. */
    @Synchronized
    fun arm(): Boolean {
        if (!isLinux || !persisted || armed) return false
        val start = startTime() ?: return false
        armed = true
        return runCatching { spawn(command(start)) }.isSuccess
    }

    internal fun command(start: String): List<String> {
        val script = "sleep $delaySeconds; " +
            "now=\$(sed 's/.*) //' /proc/$pid/stat 2>/dev/null | cut -d' ' -f20); " +
            "if [ \"\$now\" = \"$start\" ]; then " +
            "echo 'AndBible desktop: the JVM did not exit within $delaySeconds s (X display lock); killing process $pid' >&2; " +
            "kill -KILL $pid; fi"
        return listOf("/bin/sh", "-c", script)
    }

    companion object {
        /** Field 22 (start time in clock ticks) of `/proc/<pid>/stat`, null when it cannot be read. */
        fun processStartTime(pid: Long): String? = runCatching {
            // The command name (field 2) is in parentheses and may contain spaces: count from the last ") ".
            File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')[19]
        }.getOrNull()
    }
}
