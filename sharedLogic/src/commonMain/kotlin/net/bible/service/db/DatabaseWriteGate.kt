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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Lets a database replace (restore, sync initial swap) wait for the install-row writes of `BookInstallWatcher` that are
 * already running, and makes later ones wait until the replace has ended. No code inside a replace block waits for a
 * watcher write (checked for every `replacingDatabases` caller), so a write held at the closed gate can never be what
 * the replace is waiting for; a write submitted during a replace therefore only suspends until [open] and cannot deadlock it.
 *
 * Closes nest: the gate reopens when every [closeAndDrain] has been matched by an [open].
 */
class DatabaseWriteGate {
    private data class State(val closeDepth: Int = 0, val inFlight: Int = 0)

    private val state = MutableStateFlow(State())

    /** Waits while the gate is closed, then runs [block] counted as in flight, so a [closeAndDrain] waits for it. */
    suspend fun <T> pass(block: suspend () -> T): T {
        while (true) {
            var entered = false
            state.update {
                entered = it.closeDepth == 0
                if (entered) it.copy(inFlight = it.inFlight + 1) else it
            }
            if (entered) break
            state.first { it.closeDepth == 0 }
        }
        try {
            return block()
        } finally {
            state.update { it.copy(inFlight = it.inFlight - 1) }
        }
    }

    /**
     * Closes the gate (nested: depth + 1) and returns once no [pass] block is in flight. When cancelled while waiting,
     * it undoes its own close, so the caller must call [open] only after this returned normally.
     */
    suspend fun closeAndDrain() {
        state.update { it.copy(closeDepth = it.closeDepth + 1) }
        try {
            state.first { it.inFlight == 0 }
        } catch (e: Throwable) {
            withContext(NonCancellable) { open() }
            throw e
        }
    }

    /** Depth - 1; at 0, waiting [pass] calls proceed. Extra calls are ignored. */
    fun open() {
        state.update { it.copy(closeDepth = (it.closeDepth - 1).coerceAtLeast(0)) }
    }

    /** Opens the gate whatever its depth and forgets in-flight counts (tests share one JVM). */
    fun resetForTest() {
        state.value = State()
    }
}
