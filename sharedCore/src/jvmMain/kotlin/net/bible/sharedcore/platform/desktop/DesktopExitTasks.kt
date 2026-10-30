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

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.bible.sharedcore.log.Log
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

/**
 * The work that must run when the desktop app closes, after the workspace save and before the settings flush and the
 * process exit (`DesktopAppStartup.persistBeforeExit` joins [runAll]). One instance per app graph, bound in Koin by the
 * desktop platform module, so any module (`:sharedLogic` included) can register.
 *
 * Owner of the registrations: plan D3c (desktop cloud sync: the on-close sync driver registers here). Default = today's
 * behaviour: nothing is registered, so the exit path does exactly what it did before.
 */
class DesktopExitTasks {
    private class Task(val name: String, val run: suspend () -> Unit)

    private val tasks = CopyOnWriteArrayList<Task>()

    /** Registers [task] under [name] (for the log); the returned function unregisters it. */
    fun register(name: String, task: suspend () -> Unit): () -> Unit {
        val entry = Task(name, task)
        tasks += entry
        return { tasks.remove(entry) }
    }

    /**
     * Runs the registered tasks one after another, in registration order, all of them within [timeoutMs] together. A task
     * that throws is logged and the next one runs; when the time is up the running task is cancelled and the rest are
     * skipped. Returns true when every task finished (successfully or not) in time. Never throws, except the caller's own
     * cancellation.
     */
    suspend fun runAll(timeoutMs: Long): Boolean {
        val snapshot = tasks.toList()
        if (snapshot.isEmpty()) return true
        var current: String? = null
        return try {
            withTimeout(timeoutMs) {
                for (task in snapshot) {
                    current = task.name
                    try {
                        task.run()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.e(TAG, "Exit task ${task.name} failed", e)
                    }
                }
            }
            true
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Exit tasks did not finish within $timeoutMs ms (running: $current)")
            false
        }
    }

    private companion object {
        const val TAG = "DesktopExitTasks"
    }
}
