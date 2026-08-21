package net.bible.sharedcore.mydocuments

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.search.SearchModeController

/** Page body format (mirrors the classic MyDocumentContentType). */
enum class ContentType { MARKDOWN, HTML }

/** Framework-free view-data for one page within a document. */
data class MyDocPageItem(
    val id: Long,
    val name: String,
    val contentType: ContentType,
    val isAiGenerated: Boolean,
)

/**
 * Controller for the pages-within-a-document editor. Owns the ordered view-data + dirty flag + the
 * changed/toDelete id-sets. Rename/delete/reorder mutate locally and are flushed by [save] via
 * [onSave] — the same deferred-save model as [MyDocumentsController]. Navigation + SAF + DB-create
 * are host seams ([onOpenPage]/[onImport]/[onExport]/[onCreatePage]).
 */
class MyDocumentPagesController(
    val onOpenPage: (id: Long) -> Unit,
    val onImport: () -> Unit,
    val onExport: (id: Long) -> Unit,
    val onCreatePage: (name: String, type: ContentType) -> Unit,
    /** Batch export. The `= {}` default is TEMPORARY: it keeps MyDocumentPagesComposeActivity
     *  compiling until Task 12 wires this seam, which removes the default in the same commit. */
    val onExportSelected: (ids: List<Long>) -> Unit = {},
    val onSave: (orderedIds: List<Long>, changed: Set<Long>, deleted: Set<Long>) -> Unit,
) {
    /** The full, unfiltered order. [pages] publishes a filtered view of this. */
    private val working = mutableListOf<MyDocPageItem>()

    private val _pages = MutableStateFlow<List<MyDocPageItem>>(emptyList())
    val pages: StateFlow<List<MyDocPageItem>> = _pages.asStateFlow()
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

    fun setPages(items: List<MyDocPageItem>) {
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
        _pages.value =
            if (q.isEmpty()) working.toList()
            else working.filter { it.name.contains(q, ignoreCase = true) }
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

    private inline fun mutate(id: Long, crossinline f: (MyDocPageItem) -> MyDocPageItem) {
        val i = working.indexOfFirst { it.id == id }
        if (i < 0) return
        working[i] = f(working[i])
        changed.add(id)
        _dirty.value = true
        publish()
    }

    fun addPage(item: MyDocPageItem) {
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

    /** Deletes every selected page — unlike [MyDocumentsController.deleteSelected] there is no
     *  AI-generated exemption here; a page carries no independent "keep this" signal. */
    fun deleteSelected() {
        val ids = _selection.value
        _selection.value = emptySet()
        if (ids.isEmpty()) return
        working.removeAll { it.id in ids }
        toDelete.addAll(ids)
        changed.removeAll(ids)
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

    fun openPage(id: Long) = onOpenPage(id)
    fun importPage() = onImport()
    fun export(id: Long) = onExport(id)
    fun createPage(name: String, type: ContentType) = onCreatePage(name, type)

    fun save() = onSave(working.map { it.id }, changed.toSet(), toDelete.toSet())
}
