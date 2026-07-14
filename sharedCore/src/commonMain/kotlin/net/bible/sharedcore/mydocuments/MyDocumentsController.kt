package net.bible.sharedcore.mydocuments

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val onSave: (orderedIds: List<Long>, changed: Set<Long>, deleted: Set<Long>) -> Unit,
) {
    private val _documents = MutableStateFlow<List<MyDocItem>>(emptyList())
    val documents: StateFlow<List<MyDocItem>> = _documents.asStateFlow()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val changed = mutableSetOf<Long>()
    private val toDelete = mutableSetOf<Long>()

    fun setDocuments(items: List<MyDocItem>) {
        _documents.value = items
        changed.clear(); toDelete.clear()
        _dirty.value = false
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        val list = _documents.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _documents.value = list
        // Every row between the two positions shifted, so mark each of them changed (classic
        // compared orderNumber != index; the [lo,hi] span is exactly the set that moved).
        for (i in minOf(from, to)..maxOf(from, to)) changed.add(list[i].id)
        _dirty.value = true
    }

    fun rename(id: Long, name: String) = mutate(id) { it.copy(name = name) }
    fun editDescription(id: Long, description: String) = mutate(id) { it.copy(description = description) }

    private inline fun mutate(id: Long, crossinline f: (MyDocItem) -> MyDocItem) {
        _documents.value = _documents.value.map { if (it.id == id) f(it) else it }
        changed.add(id)
        _dirty.value = true
    }

    fun addDocument(item: MyDocItem) {
        _documents.value = _documents.value + item
        _dirty.value = true
    }

    fun delete(id: Long) {
        _documents.value = _documents.value.filterNot { it.id == id }
        toDelete.add(id)
        changed.remove(id)
        _dirty.value = true
    }

    fun open(id: Long) = onOpen(id)
    fun importDocuments() = onImport()
    fun export(id: Long) = onExport(id)
    fun create(name: String) = onCreate(name)

    fun save() = onSave(_documents.value.map { it.id }, changed.toSet(), toDelete.toSet())
}
