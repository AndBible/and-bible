package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.search.SearchModeController

/** Which modal the Bookmarks list is currently showing (screen-local, driven by the controller). */
sealed interface BookmarksDialog {
    data object None : BookmarksDialog
    data class ConfirmDelete(val count: Int) : BookmarksDialog
}

/**
 * Owns the UI state for the Bookmarks list (mirrors classic `Bookmarks.kt`): filter/sort/search/
 * showNotes selection plus the loaded [rows] and multi-[selection]. Every state change that affects
 * which rows are shown (filter, sort, search, showNotes) triggers [reload], which asks the
 * [BookmarksService] seam to (re)load the rows on [scope] — the actual Room/JSword work, and pref
 * persistence, live in the seam / host, keeping this class portable to iOS.
 */
class BookmarksController(
    private val service: BookmarksService,
    private val scope: CoroutineScope,
    initialFilterIndex: Int,
    private val onSelectBookmark: (id: String, listPosition: Int) -> Unit,
    private val onAssignLabels: (ids: List<String>) -> Unit,
    private val onDeleteSelected: (ids: List<String>) -> Unit,
    private val onExportCsv: () -> Unit,
    private val onImportCsv: () -> Unit,
    private val onManageLabels: () -> Unit,
) {
    private val _filterLabels = MutableStateFlow(service.filterLabels())
    val filterLabels: StateFlow<List<BookmarkFilterLabel>> = _filterLabels.asStateFlow()

    private val _selectedFilterIndex = MutableStateFlow(
        initialFilterIndex.coerceIn(0, (_filterLabels.value.size - 1).coerceAtLeast(0))
    )
    val selectedFilterIndex: StateFlow<Int> = _selectedFilterIndex.asStateFlow()

    private val _sortMode = MutableStateFlow(service.loadSortMode())
    val sortMode: StateFlow<BookmarkSortMode> = _sortMode.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val searchMode = SearchModeController(onClearQuery = { setSearch("") })
    val searchModeActive: StateFlow<Boolean> = searchMode.active

    private val _showNotes = MutableStateFlow(service.loadShowNotes())
    val showNotes: StateFlow<Boolean> = _showNotes.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _expandedIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    private val _rows = MutableStateFlow<List<BookmarkRow>>(emptyList())
    val rows: StateFlow<List<BookmarkRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _dialog = MutableStateFlow<BookmarksDialog>(BookmarksDialog.None)
    val dialog: StateFlow<BookmarksDialog> = _dialog.asStateFlow()

    init { reload() }

    private fun reload() {
        _loading.value = true
        val filterIndex = _selectedFilterIndex.value
        val sort = _sortMode.value
        val search = _searchText.value.ifBlank { null }
        val showNotes = _showNotes.value
        scope.launch {
            val r = service.loadRows(filterIndex, sort, search, showNotes)
            _rows.value = r
            _loading.value = false
        }
    }

    fun setFilter(index: Int) {
        _selectedFilterIndex.value = index
        reload()
    }

    fun cycleSort() {
        val next = _sortMode.value.next()
        _sortMode.value = next
        service.saveSortMode(next)
        reload()
    }

    fun setSearch(text: String) {
        _searchText.value = text
        reload()
    }

    fun openSearch() = searchMode.open()
    fun closeSearch() = searchMode.close()

    fun toggleShowNotes() {
        val next = !_showNotes.value
        _showNotes.value = next
        service.saveShowNotes(next)
        if (!next) { _searchText.value = ""; searchMode.reset() }
        reload()
    }

    // ---- selection ----
    fun enterSelection(id: String) {
        _selection.value = _selection.value + id
    }
    fun toggleSelection(id: String) {
        val current = _selection.value
        _selection.value = if (current.contains(id)) current - id else current + id
    }
    fun clearSelection() {
        _selection.value = emptySet()
    }

    /**
     * Expand or collapse one row's full bible text and note. This is view state, hoisted here
     * rather than remembered inside the row so the screen stays stateless — and so a unit test and
     * a golden can drive it. It is deliberately NOT reset by [reload] or [refresh]: ids that leave
     * the list simply stop matching, and a filter change that keeps a row should keep its state.
     */
    fun toggleExpanded(id: String) {
        val current = _expandedIds.value
        _expandedIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectRow(id: String, listPosition: Int) {
        if (_selection.value.isEmpty()) onSelectBookmark(id, listPosition)
        else toggleSelection(id)
    }

    // ---- selection actions (delegate to host) ----
    fun assignSelected() = onAssignLabels(_selection.value.toList())

    /** Classic's `AlertDialog` question ("Delete N selected bookmarks?"), moved off the host into
     *  this controller's own [dialog] state (Task 13) — [confirmDialog] runs the deletion, which
     *  still lives in the host because it needs the Room entities behind the selected ids. */
    fun requestDelete() {
        val count = _selection.value.size
        if (count > 0) _dialog.value = BookmarksDialog.ConfirmDelete(count)
    }
    fun confirmDialog() {
        val wasConfirmDelete = _dialog.value is BookmarksDialog.ConfirmDelete
        _dialog.value = BookmarksDialog.None
        if (wasConfirmDelete) onDeleteSelected(_selection.value.toList())
    }
    fun dismissDialog() { _dialog.value = BookmarksDialog.None }

    // ---- toolbar actions (delegate to host) ----
    fun exportCsv() = onExportCsv()
    fun importCsv() = onImportCsv()
    fun manageLabels() = onManageLabels()

    /** Host calls this after assign/delete/import round-trips. */
    fun refresh() {
        _filterLabels.value = service.filterLabels()
        reload()
        clearSelection()
    }
}
