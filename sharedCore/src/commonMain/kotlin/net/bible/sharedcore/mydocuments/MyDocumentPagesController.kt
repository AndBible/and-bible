package net.bible.sharedcore.mydocuments

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val onSave: (orderedIds: List<Long>, changed: Set<Long>, deleted: Set<Long>) -> Unit,
) {
    private val _pages = MutableStateFlow<List<MyDocPageItem>>(emptyList())
    val pages: StateFlow<List<MyDocPageItem>> = _pages.asStateFlow()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val changed = mutableSetOf<Long>()
    private val toDelete = mutableSetOf<Long>()

    fun setPages(items: List<MyDocPageItem>) {
        _pages.value = items
        changed.clear(); toDelete.clear()
        _dirty.value = false
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        val list = _pages.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _pages.value = list
        // Every row between the two positions shifted, so mark each of them changed (classic
        // compared orderNumber != index; the [lo,hi] span is exactly the set that moved).
        for (i in minOf(from, to)..maxOf(from, to)) changed.add(list[i].id)
        _dirty.value = true
    }

    fun rename(id: Long, name: String) = mutate(id) { it.copy(name = name) }

    private inline fun mutate(id: Long, crossinline f: (MyDocPageItem) -> MyDocPageItem) {
        _pages.value = _pages.value.map { if (it.id == id) f(it) else it }
        changed.add(id)
        _dirty.value = true
    }

    fun addPage(item: MyDocPageItem) {
        _pages.value = _pages.value + item
        _dirty.value = true
    }

    fun delete(id: Long) {
        _pages.value = _pages.value.filterNot { it.id == id }
        toDelete.add(id)
        changed.remove(id)
        _dirty.value = true
    }

    fun openPage(id: Long) = onOpenPage(id)
    fun importPage() = onImport()
    fun export(id: Long) = onExport(id)
    fun createPage(name: String, type: ContentType) = onCreatePage(name, type)

    fun save() = onSave(_pages.value.map { it.id }, changed.toSet(), toDelete.toSet())
}
