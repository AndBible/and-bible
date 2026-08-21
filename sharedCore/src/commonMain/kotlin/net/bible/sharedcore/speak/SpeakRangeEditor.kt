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

package net.bible.sharedcore.speak

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Draft state for the Speak repeat-passage verse range: the two endpoints the user is choosing,
 * before anything is written.
 *
 * Pure by construction. The ordering rule compares [PickedVerse.ordinal], which the host supplies
 * when it maps a grid pick, precisely so this rule is testable without JSword. The comparison is
 * strictly greater-than, matching the classic flow's `endVerse.ordinal > startVerse.ordinal`: a
 * single-verse "range" is rejected, as it always was.
 */
class SpeakRangeEditor {
    private val _start = MutableStateFlow<PickedVerse?>(null)
    val start: StateFlow<PickedVerse?> = _start.asStateFlow()

    private val _end = MutableStateFlow<PickedVerse?>(null)
    val end: StateFlow<PickedVerse?> = _end.asStateFlow()

    private val _canCommit = MutableStateFlow(false)
    val canCommit: StateFlow<Boolean> = _canCommit.asStateFlow()

    /** True only once BOTH endpoints are set and the order is wrong — an incomplete draft is not
     *  an error, it is unfinished, and telling the user off mid-way would be noise. */
    private val _showOrderError = MutableStateFlow(false)
    val showOrderError: StateFlow<Boolean> = _showOrderError.asStateFlow()

    fun seed(start: PickedVerse?, end: PickedVerse?) {
        _start.value = start
        _end.value = end
        recompute()
    }

    fun set(end: Boolean, verse: PickedVerse) {
        if (end) _end.value = verse else _start.value = verse
        recompute()
    }

    fun clearDraft() = seed(null, null)

    private fun recompute() {
        val s = _start.value
        val e = _end.value
        val both = s != null && e != null
        val ordered = both && e!!.ordinal > s!!.ordinal
        _canCommit.value = ordered
        _showOrderError.value = both && !ordered
    }
}
