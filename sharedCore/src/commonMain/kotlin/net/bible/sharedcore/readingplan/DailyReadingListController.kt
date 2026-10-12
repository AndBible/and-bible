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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plain view-data for one day row. [primary]/[secondary] are already-resolved lines. */
data class DayEntry(val day: Int, val primary: String, val secondary: String)

/** Framework-free controller for the day chooser. [loadDays] supplies rows; [onSelect] finishes with the day. */
class DailyReadingListController(
    private val scope: CoroutineScope,
    private val loadDays: suspend () -> List<DayEntry>,
    private val onSelect: (Int) -> Unit,
) {
    private val _days = MutableStateFlow<List<DayEntry>>(emptyList())
    val days: StateFlow<List<DayEntry>> = _days.asStateFlow()

    private val _error = MutableStateFlow<ReadingPlanError?>(null)
    val error: StateFlow<ReadingPlanError?> = _error.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        _error.value = null
        loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val days = loadDays()
                if (generation == loadGeneration && isActive) _days.value = days
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == loadGeneration && isActive) _error.value = ReadingPlanError.FAILED
            }
        }
    }

    fun select(day: Int) = onSelect(day)
    fun dismissError() { _error.value = null }
}
