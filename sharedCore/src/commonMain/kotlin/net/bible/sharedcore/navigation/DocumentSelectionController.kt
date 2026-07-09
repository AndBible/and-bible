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

/**
 * Framework-free controller ported from DocumentSelectionBase's filter/sort/multi-select surface.
 * The host loads the Book list off-main, flattens to DocRow, and pushes via [setDocuments]; the
 * host also owns the Room FTS search (calls [setSearchResults] with matching osisIds). All JSword
 * side effects (open/delete/about/unlock) happen behind the injected seams.
 */
class DocumentSelectionController(
    private val langComparator: Comparator<LangOption>,
    private val onSelect: (String) -> Unit,
    private val onDelete: (Set<String>) -> Unit,
    private val onDeleteIndex: (Set<String>) -> Unit,
    private val onAbout: (String) -> Unit,
    private val onUnlock: (String) -> Unit,
    private val onStickyLanguage: (LangOption?) -> Unit,
) {
    private var all: List<DocRow> = emptyList()
    private var searchIds: Set<String>? = null

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _documents = MutableStateFlow<List<DocRow>>(emptyList())
    val documents: StateFlow<List<DocRow>> = _documents.asStateFlow()
    private val _displayed = MutableStateFlow<List<DocRow>>(emptyList())
    val displayed: StateFlow<List<DocRow>> = _displayed.asStateFlow()
    private val _languages = MutableStateFlow<List<LangOption>>(emptyList())
    val languages: StateFlow<List<LangOption>> = _languages.asStateFlow()
    private val _selectedLanguage = MutableStateFlow<LangOption?>(null)
    val selectedLanguage: StateFlow<LangOption?> = _selectedLanguage.asStateFlow()
    private val _selectedTypeFilter = MutableStateFlow(DocTypeFilter.ALL)
    val selectedTypeFilter: StateFlow<DocTypeFilter> = _selectedTypeFilter.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _resultCount = MutableStateFlow(0)
    val resultCount: StateFlow<Int> = _resultCount.asStateFlow()
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()
    private val _error = MutableStateFlow<ChooserError?>(null)
    val error: StateFlow<ChooserError?> = _error.asStateFlow()

    fun setDocuments(all: List<DocRow>, searchIds: Set<String>?) {
        this.all = all
        this.searchIds = searchIds
        _documents.value = all
        _loading.value = false
        // dedupe languages by groupingKey (representative = first seen), sort by host comparator
        _languages.value = all.map { it.language }
            .associateBy { it.groupingKey }.values
            .sortedWith(langComparator)
        refilter()
    }

    fun setSearchResults(osisIds: Set<String>?) { searchIds = osisIds; refilter() }
    fun setQuery(q: String) { _query.value = q } // host observes query, runs FTS when >=3, calls setSearchResults
    fun setLanguage(lang: LangOption?) { _selectedLanguage.value = lang; onStickyLanguage(lang); refilter() }
    fun setTypeFilter(f: DocTypeFilter) { _selectedTypeFilter.value = f; refilter() }

    private fun refilter() {
        clearSelection()
        val out = computeDisplayed(all, _selectedLanguage.value, _selectedTypeFilter.value, searchIds)
        _displayed.value = out
        _resultCount.value = out.size
    }

    fun computeDisplayed(all: List<DocRow>, lang: LangOption?, type: DocTypeFilter, searchIds: Set<String>?): List<DocRow> =
        all.filter { row ->
            type.test(row) &&
                (lang == null || row.language.groupingKey == lang.groupingKey || row.category == DocCategory.AND_BIBLE) &&
                (searchIds == null || searchIds.contains(row.osisId))
        }.sortedWith(
            compareBy<DocRow>(
                {
                    when (it.installStatus) {
                        DocInstallStatus.BEING_INSTALLED -> 0
                        DocInstallStatus.UPGRADE_AVAILABLE -> 1
                        else -> 2
                    }
                },
                { it.installStatus == DocInstallStatus.NOT_INSTALLED }, // not-installed after installed (false<true)
                { if (lang != null) !it.recommended else false },
                {
                    when (it.category) {
                        DocCategory.BIBLE -> 0; DocCategory.COMMENTARY -> 1; DocCategory.DICTIONARY -> 2
                        DocCategory.GENERAL_BOOK -> 4; DocCategory.MAPS -> 5; DocCategory.AND_BIBLE -> 6; DocCategory.OTHER -> 7
                    }
                },
                { it.abbreviation.lowercase() },
            )
        )

    fun enterSelection() { _selectionMode.value = true }
    fun toggle(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }
    fun clearSelection() { _selectionMode.value = false; _selectedIds.value = emptySet() }

    fun select(docId: String) = onSelect(docId)
    fun delete() { onDelete(_selectedIds.value) }
    fun deleteIndex() { onDeleteIndex(_selectedIds.value) }
    fun about() { _selectedIds.value.firstOrNull()?.let(onAbout) }
    fun unlock() { _selectedIds.value.firstOrNull()?.let(onUnlock) }

    fun showError() { _error.value = ChooserError.FAILED }
    fun dismissError() { _error.value = null }
}
