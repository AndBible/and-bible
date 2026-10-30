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

import java.nio.file.Paths
import kotlin.system.exitProcess

/** Exit code of the probe when it acquired the lock. */
const val PROBE_EXIT_ACQUIRED = 0

/** Exit code of the probe when the lock is held by another process. */
const val PROBE_EXIT_ALREADY_RUNNING = 2

/** Exit code of the probe when the data directory is unwritable (or any other failure). */
const val PROBE_EXIT_OTHER_FAILURE = 3

/**
 * Child-JVM entry point for [InstanceLockTest.secondLockAttemptFails]: `args[0]` is the data directory. It tries
 * [InstanceLock.acquire], prints the outcome on stdout and exits with one of the `PROBE_EXIT_*` codes. It holds the
 * lock only for the instant of the attempt, so it must run while the parent holds the same lock.
 */
fun main(args: Array<String>) {
    val result = InstanceLock.acquire(Paths.get(args[0]))
    val code = result.fold(
        onSuccess = { lock -> lock.close(); PROBE_EXIT_ACQUIRED },
        onFailure = { e -> if (e is AlreadyRunningException) PROBE_EXIT_ALREADY_RUNNING else PROBE_EXIT_OTHER_FAILURE },
    )
    println(result.fold(onSuccess = { "acquired" }, onFailure = { "${it::class.simpleName}: ${it.message}" }))
    exitProcess(code)
}
