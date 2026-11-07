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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import net.bible.sharedcore.log.Log
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

interface ExitTaskScope {
    fun beginVisibleWork()
}

/**
 * The work that must run when the desktop app closes, after the workspace save and before the settings flush and the
 * process exit (`DesktopAppStartup.persistBeforeExit` joins [runAll]). One instance per app graph, bound in Koin by the
 * desktop platform module, so any module (`:sharedLogic` included) can register.
 *
 * Owner of the registrations: plan D3c (desktop cloud sync: the on-close sync driver registers here). Default = today's
 * behaviour: nothing is registered, so the exit path does exactly what it did before.
 */
class DesktopExitTasks {
    private class Task(val name: String, val run: suspend ExitTaskScope.() -> Unit)

    private val tasks = CopyOnWriteArrayList<Task>()
    private val lock = Any()
    private val mutableSyncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = mutableSyncing.asStateFlow()
    @Volatile private var runningJob: Job? = null
    private var skipSignal: CompletableDeferred<Unit>? = null

    fun skipRemaining() {
        synchronized(lock) {
            skipSignal?.complete(Unit)
            runningJob?.cancel()
        }
    }

    /**
     * Registers [task] under [name] (for the log); the returned function unregisters it. With [runFirst] the task runs
     * before every task registered so far (for work that must not wait behind the shared time budget).
     */
    fun register(name: String, runFirst: Boolean = false, task: suspend ExitTaskScope.() -> Unit): () -> Unit {
        val entry = Task(name, task)
        if (runFirst) tasks.add(0, entry) else tasks += entry
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
        val signal = CompletableDeferred<Unit>()
        val workerJob = Job()
        synchronized(lock) {
            check(runningJob == null) { "Exit tasks are already running" }
            runningJob = workerJob
            skipSignal = signal
        }
        val worker = CoroutineScope(workerJob + Dispatchers.IO).async {
            for (task in snapshot) {
                val scope = object : ExitTaskScope {
                    override fun beginVisibleWork() {
                        synchronized(lock) {
                            if (runningJob === workerJob && workerJob.isActive) mutableSyncing.value = true
                        }
                    }
                }
                try {
                    task.run(scope)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Exit task ${task.name} failed", e)
                } finally {
                    synchronized(lock) {
                        if (runningJob === workerJob) mutableSyncing.value = false
                    }
                }
                ensureActive()
            }
        }
        return try {
            val completed = withTimeoutOrNull(timeoutMs) {
                select<Boolean> {
                    signal.onAwait { false }
                    worker.onJoin { !signal.isCompleted && !worker.isCancelled }
                }
            }
            when (completed) {
                null -> { Log.w(TAG, "Exit tasks did not finish within $timeoutMs ms"); false }
                false -> {
                    Log.w(TAG, if (signal.isCompleted) "Exit tasks skipped by user" else "Exit task cancelled")
                    false
                }
                true -> true
            }
        } finally {
            synchronized(lock) {
                if (runningJob === workerJob) {
                    runningJob = null
                    skipSignal = null
                    mutableSyncing.value = false
                }
            }
            workerJob.cancel()
        }
    }

    private companion object {
        const val TAG = "DesktopExitTasks"
    }
}
