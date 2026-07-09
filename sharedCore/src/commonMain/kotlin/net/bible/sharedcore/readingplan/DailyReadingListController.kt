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
package net.bible.sharedcore.readingplan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plain view-data for one day row. [primary]/[secondary] are already-resolved lines. */
data class DayEntry(val day: Int, val primary: String, val secondary: String)

/** Framework-free controller for the day chooser. [loadDays] supplies rows; [onSelect] finishes with the day. */
class DailyReadingListController(
    private val loadDays: () -> List<DayEntry>,
    private val onSelect: (Int) -> Unit,
) {
    private val _days = MutableStateFlow<List<DayEntry>>(emptyList())
    val days: StateFlow<List<DayEntry>> = _days.asStateFlow()

    private val _error = MutableStateFlow<ReadingPlanError?>(null)
    val error: StateFlow<ReadingPlanError?> = _error.asStateFlow()

    init { load() }

    fun load() {
        try { _days.value = loadDays() } catch (e: Exception) { _error.value = ReadingPlanError.FAILED }
    }

    fun select(day: Int) = onSelect(day)
    fun dismissError() { _error.value = null }
}
