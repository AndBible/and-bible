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
package net.bible.sharedcore.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plain view-data for one history row (already-formatted strings; [id] is the opaque list index). */
data class HistoryEntry(val id: Int, val title: String, val timestamp: String)

enum class HistoryError { REVERT_FAILED }

/**
 * Framework-free controller for the History screen. [loadEntries] supplies the already-formatted
 * rows (the host resolves JSword + date formatting); [onRevert] navigates back to the selected item
 * (the host resolves id -> HistoryItem.revertTo() and finishes). No Android/JSword types here.
 */
class HistoryController(
    private val loadEntries: () -> List<HistoryEntry>,
    private val onRevert: (Int) -> Unit,
) {
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private val _error = MutableStateFlow<HistoryError?>(null)
    val error: StateFlow<HistoryError?> = _error.asStateFlow()

    init {
        _entries.value = loadEntries()
    }

    fun onSelect(id: Int) {
        try {
            onRevert(id)
        } catch (e: Exception) {
            _error.value = HistoryError.REVERT_FAILED
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
