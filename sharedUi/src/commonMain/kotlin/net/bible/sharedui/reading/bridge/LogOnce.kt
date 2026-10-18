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
package net.bible.sharedui.reading.bridge

/** Logs each distinct key at most once through [log]; used by pending (not yet implemented) platform seams. */
class LogOnce(private val log: (String) -> Unit) {
    // Copy-on-write; a rare race between threads can at worst log a name twice, which is harmless.
    @kotlin.concurrent.Volatile
    private var seen: Set<String> = emptySet()

    /** Logs "pending: [name]" the first time [name] is seen; returns true if it logged. */
    fun pending(name: String): Boolean {
        if (name in seen) return false
        seen = seen + name
        log("pending: $name")
        return true
    }
}
