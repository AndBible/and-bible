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

/** Plain view-data for one key-chooser row. [keyId] is the opaque host handle (the list index). */
data class KeyRow(val keyId: String, val name: String)

/** Shared error marker for the navigation-chooser screens (host resolves the cause). */
enum class ChooserError { FAILED }

/**
 * Framework-free controller for the general-book / EPUB key chooser. [loadRows] supplies the
 * already-resolved rows (host handles the EPUB TOC vs global-key-list choice); [currentRow] is
 * the id to highlight/scroll to; [onSelect] is the host seam (build the result Intent + finish).
 */
class ChooseGeneralBookKeyController(
    private val loadRows: () -> List<KeyRow>,
    private val currentRow: () -> String?,
    private val onSelect: (String) -> Unit,
) {
    private val _rows = MutableStateFlow<List<KeyRow>>(emptyList())
    val rows: StateFlow<List<KeyRow>> = _rows.asStateFlow()

    private val _currentKeyId = MutableStateFlow<String?>(null)
    val currentKeyId: StateFlow<String?> = _currentKeyId.asStateFlow()

    private val _error = MutableStateFlow<ChooserError?>(null)
    val error: StateFlow<ChooserError?> = _error.asStateFlow()

    init { load() }

    fun load() {
        _error.value = null
        try {
            _rows.value = loadRows()
            _currentKeyId.value = currentRow()
        } catch (e: Exception) {
            _error.value = ChooserError.FAILED
        }
    }

    fun select(keyId: String) = onSelect(keyId)

    fun dismissError() { _error.value = null }
}
