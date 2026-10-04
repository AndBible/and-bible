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
package net.bible.sharedcore.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plain view-data for one dictionary row. [name] is the dictionary key (display + filter). */
data class DictRow(val keyId: String, val name: String)

/**
 * Framework-free controller for the dictionary word chooser. The host loads the (slow) global
 * key list off-main and pushes it via [setAllRows]; the controller filters it by [query].
 * The per-row OSIS snippet is loaded lazily in the host/screen, never here.
 */
class ChooseDictionaryWordController(
    private val onSelect: (String) -> Unit,
) {
    private var allRows: List<DictRow> = emptyList()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _rows = MutableStateFlow<List<DictRow>>(emptyList())
    val rows: StateFlow<List<DictRow>> = _rows.asStateFlow()

    private val _error = MutableStateFlow<ChooserError?>(null)
    val error: StateFlow<ChooserError?> = _error.asStateFlow()

    fun setAllRows(rows: List<DictRow>) {
        allRows = rows
        _loading.value = false
        applyFilter()
    }

    fun setQuery(q: String) {
        _query.value = q
        applyFilter()
    }

    private fun applyFilter() {
        val q = _query.value
        _rows.value = if (q.isEmpty()) allRows
        else allRows.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun select(keyId: String) = onSelect(keyId)

    fun showError() { _error.value = ChooserError.FAILED }
    fun dismissError() { _error.value = null }
}
