package net.bible.sharedcore.mydocuments

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.search.SearchModeController

/** Framework-free view-data for one user document (see the classic MyDocument entity). */
data class MyDocItem(
    val id: Long,
    val initials: String,
    val name: String,
    val description: String,
    val isAiGenerated: Boolean,
    val canDelete: Boolean,
)

/**
 * Controller for the MyDocuments list editor. Owns the ordered view-data + dirty flag + the
 * changed/toDelete id-sets. Rename/description/delete/reorder mutate locally and are flushed by
 * [save] via [onSave] — mirroring the classic applyChanges() (all DB writes deferred to save).
 * Navigation + SAF + DB-create are host seams ([onOpen]/[onImport]/[onExport]/[onCreate]).
 */
class MyDocumentsController(
    val onOpen: (id: Long) -> Unit,
    val onImport: () -> Unit,
    val onExport: (id: Long) -> Unit,
    val onCreate: (name: String) -> Unit,
    /** Batch export. The `= {}` default is TEMPORARY: it keeps MyDocumentsComposeActivity compiling
     *  until Task 11 wires this seam, which removes the default in the same commit. */
    val onExportSelected: (ids: List<Long>) -> Unit = {},
    val onSave: (orderedIds: List<Long>, changed: Set<Long>, deleted: Set<Long>) -> Unit,
) {
    /** The full, unfiltered order. [documents] publishes a filtered view of this. */
    private val working = mutableListOf<MyDocItem>()

    private val _documents = MutableStateFlow<List<MyDocItem>>(emptyList())
    val documents: StateFlow<List<MyDocItem>> = _documents.asStateFlow()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filtering = MutableStateFlow(false)
    val filtering: StateFlow<Boolean> = _filtering.asStateFlow()
    /** The UNFILTERED row count — what the create sheet's default name counts from. */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()
    private val searchMode = SearchModeController(onClearQuery = { setQuery("") })

    /** Whether the top bar shows its inline search field. Held here, not in the composable, so it
     *  survives recomposition and so closing routes through [setQuery] — which is what keeps
     *  [filtering], and therefore the row drag handles, in step with it. */
    val searchModeActive: StateFlow<Boolean> = searchMode.active

    private val changed = mutableSetOf<Long>()
    private val toDelete = mutableSetOf<Long>()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun setDocuments(items: List<MyDocItem>) {
        working.clear(); working.addAll(items)
        changed.clear(); toDelete.clear()
        _dirty.value = false
        _query.value = ""
        searchMode.reset()
        _selection.value = emptySet()
        publish()
    }

    private fun publish() {
        val q = _query.value.trim()
        _filtering.value = q.isNotEmpty()
        _totalCount.value = working.size
        _documents.value =
            if (q.isEmpty()) working.toList()
            else working.filter {
                it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
            }
    }

    fun setQuery(q: String) { _query.value = q; _selection.value = emptySet(); publish() }
    fun openSearch() = searchMode.open()

    /** Classic parity (RecyclerViewSearchHelper:110-114): collapsing search clears the filter. */
    fun closeSearch() = searchMode.close()

    fun moveItem(from: Int, to: Int) {
        // The rows the user can drag are the PUBLISHED rows, so a drag while filtered would carry
        // filtered indices into the full order and silently write a wrong order at save time. The
        // screens hide the handles while filtering; this is the guard behind that.
        if (_filtering.value) return
        if (from == to) return
        if (from !in working.indices || to !in working.indices) return
        working.add(to, working.removeAt(from))
        // Every row between the two positions shifted, so mark each of them changed (classic
        // compared orderNumber != index; the [lo,hi] span is exactly the set that moved).
        for (i in minOf(from, to)..maxOf(from, to)) changed.add(working[i].id)
        _dirty.value = true
        publish()
    }

    fun rename(id: Long, name: String) = mutate(id) { it.copy(name = name) }
    fun editDescription(id: Long, description: String) = mutate(id) { it.copy(description = description) }

    private inline fun mutate(id: Long, crossinline f: (MyDocItem) -> MyDocItem) {
        val i = working.indexOfFirst { it.id == id }
        if (i < 0) return
        working[i] = f(working[i])
        changed.add(id)
        _dirty.value = true
        publish()
    }

    fun addDocument(item: MyDocItem) {
        working.add(item)
        _dirty.value = true
        publish()
    }

    fun delete(id: Long) {
        working.removeAll { it.id == id }
        toDelete.add(id)
        changed.remove(id)
        _selection.value = _selection.value - id
        _dirty.value = true
        publish()
    }

    fun toggleSelect(id: Long) {
        val s = _selection.value
        _selection.value = if (id in s) s - id else s + id
    }

    fun clearSelection() { _selection.value = emptySet() }

    /**
     * Deletes every selected document EXCEPT those whose [MyDocItem.canDelete] is false (AI-generated
     * documents), which are left in the list. The screen tells the user about the skipped ones; it can
     * count them from its own rows because a selection can only exist over the currently published
     * rows — [setQuery] clears the selection.
     */
    fun deleteSelected() {
        val ids = _selection.value
        _selection.value = emptySet()
        if (ids.isEmpty()) return
        val deletable = working.filter { it.id in ids && it.canDelete }.map { it.id }
        if (deletable.isEmpty()) return
        working.removeAll { it.id in deletable }
        toDelete.addAll(deletable)
        changed.removeAll(deletable.toSet())
        _dirty.value = true
        publish()
    }

    /** Hands the selected ids to the host (which owns SAF) and leaves selection mode. */
    fun exportSelected() {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        _selection.value = emptySet()
        onExportSelected(ids)
    }

    fun open(id: Long) = onOpen(id)
    fun importDocuments() = onImport()
    fun export(id: Long) = onExport(id)
    fun create(name: String) = onCreate(name)

    fun save() = onSave(working.map { it.id }, changed.toSet(), toDelete.toSet())
}
