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
import net.bible.sharedcore.search.SearchModeController

fun computeDisplayedDocuments(all: List<DocRow>, lang: LangOption?, type: DocTypeFilter, searchIds: Set<String>?): List<DocRow> =
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
    private val searchMode = SearchModeController(onClearQuery = { setQuery("") })
    val searchModeActive: StateFlow<Boolean> = searchMode.active
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

    /**
     * Update one row's live download status/progress IN PLACE — deliberately without re-sorting,
     * and without clearing an active selection.
     *
     * Classic sorts only in DocumentSelectionBase.filterDocuments() (a spinner/search change, or
     * after populateMasterDocumentList on refresh); DownloadActivity.doDownload issues a bare
     * notifyDataSetChanged(). Re-running computeDisplayed here instead made the row jump to the
     * top the instant it entered BEING_INSTALLED — that is computeDisplayed's first sort key —
     * which reads as the row disappearing from where the user left it. The sort keys are correct
     * and unchanged; they apply at the next re-sort (setDocuments / setLanguage / setTypeFilter /
     * setSearchResults), exactly as in classic.
     *
     * [canDelete] travels with the status because a finished install CHANGES it: the flag is
     * derived from the installed copy of the document, which does not exist until the download
     * completes. Without it the row kept the deletability it was loaded with, so the delete action
     * stayed hidden on a document the user had just installed until the screen was reopened. It is
     * part of the equality short-circuit below for the same reason — the terminal event can carry
     * a status and percentage the row already shows, and that is exactly the update that matters.
     */
    fun updateDownloadStatus(docId: String, status: DocInstallStatus, percentDone: Int, canDelete: Boolean) {
        val idx = all.indexOfFirst { it.docId == docId }
        if (idx < 0) return
        val cur = all[idx]
        if (cur.installStatus == status && cur.percentDone == percentDone && cur.canDelete == canDelete) return
        val updated = cur.copy(installStatus = status, percentDone = percentDone, canDelete = canDelete)
        all = all.toMutableList().apply { this[idx] = updated }
        _documents.value = all
        val shown = _displayed.value
        val shownIdx = shown.indexOfFirst { it.docId == docId }
        if (shownIdx >= 0) {
            _displayed.value = shown.toMutableList().apply { this[shownIdx] = updated }
        }
        // resultCount is deliberately NOT recomputed: no predicate in computeDisplayed reads
        // installStatus, so a status change can never add or remove a row from the displayed set.
    }

    fun setSearchResults(osisIds: Set<String>?) { searchIds = osisIds; refilter() }
    fun setQuery(q: String) { _query.value = q } // host observes query, runs FTS when >=3, calls setSearchResults
    fun openSearch() = searchMode.open()
    fun closeSearch() = searchMode.close()
    fun setLanguage(lang: LangOption?) { _selectedLanguage.value = lang; onStickyLanguage(lang); refilter() }
    fun setTypeFilter(f: DocTypeFilter) { _selectedTypeFilter.value = f; refilter() }

    private fun refilter() {
        clearSelection()
        val out = computeDisplayed(all, _selectedLanguage.value, _selectedTypeFilter.value, searchIds)
        _displayed.value = out
        _resultCount.value = out.size
    }

    fun computeDisplayed(all: List<DocRow>, lang: LangOption?, type: DocTypeFilter, searchIds: Set<String>?): List<DocRow> =
        computeDisplayedDocuments(all, lang, type, searchIds)

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
