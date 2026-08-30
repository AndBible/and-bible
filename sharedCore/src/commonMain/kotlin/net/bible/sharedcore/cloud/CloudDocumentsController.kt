package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocGroup
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.navigation.decodeArrangement
import net.bible.sharedcore.navigation.defaultArrangement
import net.bible.sharedcore.navigation.encodeArrangement
import net.bible.sharedcore.navigation.groupDocuments
import net.bible.sharedcore.navigation.sortDocuments
import net.bible.sharedcore.search.SearchModeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The multi-choice "Sync now" dialog's per-direction labels + pre-checked state (host-built). */
data class SyncNowDialogState(val labels: List<String>, val checked: List<Boolean>)

/**
 * Framework-free controller for the cloud documents management view. The host flattens
 * DocumentSync.DocumentStatusItem → [CloudDocItem] and pushes via [setItems]; all cloud/service side
 * effects happen behind the injected seams. Optimistic updates use the ported pure functions so the
 * list re-renders before the background transfer completes.
 */
class CloudDocumentsController(
    val syncEnabled: () -> Boolean,
    private val onAction: (CloudDocAction, String) -> Unit,
    private val onBulkAction: (CloudDocAction, List<String>) -> Unit,
    private val onSyncNow: (download: Boolean, upload: Boolean, delete: Boolean) -> Unit,
    private val onRescan: () -> Unit,
    private val onShowRemovedChange: (Boolean) -> Unit,
    storedArrangement: String? = null,
    rememberArrangementInitially: Boolean = true,
    private val onArrangementChange: (encoded: String?, remember: Boolean) -> Unit = { _, _ -> },
) {
    // No LANGUAGE or REPOSITORY: a cloud listing has neither. No RECOMMENDED: nothing marks a
    // synced document as recommended.
    private val applicableSortKeys: Set<DocSortKey> =
        setOf(DocSortKey.STATUS, DocSortKey.TYPE, DocSortKey.NAME, DocSortKey.SIZE)
    private val applicableGroupKeys: List<DocGroupBy> =
        listOf(DocGroupBy.NONE, DocGroupBy.TYPE, DocGroupBy.STATUS)
    private val _items = MutableStateFlow<List<CloudDocItem>>(emptyList())
    val items: StateFlow<List<CloudDocItem>> = _items.asStateFlow()
    private val _displayed = MutableStateFlow<List<CloudDocItem>>(emptyList())
    val displayed: StateFlow<List<CloudDocItem>> = _displayed.asStateFlow()
    private val _statusFilter = MutableStateFlow(CloudDocFilter.ALL)
    val statusFilter: StateFlow<CloudDocFilter> = _statusFilter.asStateFlow()
    private val _categoryFilter = MutableStateFlow<DocCategory?>(null)
    val categoryFilter: StateFlow<DocCategory?> = _categoryFilter.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val searchMode = SearchModeController(onClearQuery = { setQuery("") })
    val searchModeActive: StateFlow<Boolean> = searchMode.active
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    // busy is a COUNTER-backed boolean: overlapping sources (rescan + a transfer + a per-item op)
    // each push/pop the counter, so the loading indicator stays on until every source has balanced.
    // Mirrors the classic busyCount.
    private var busyCount = 0
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _transferRunning = MutableStateFlow(false)
    val transferRunning: StateFlow<Boolean> = _transferRunning.asStateFlow()
    private val _showRemoved = MutableStateFlow(false)
    val showRemoved: StateFlow<Boolean> = _showRemoved.asStateFlow()
    private val _syncNowDialog = MutableStateFlow<SyncNowDialogState?>(null)
    val syncNowDialog: StateFlow<SyncNowDialogState?> = _syncNowDialog.asStateFlow()

    private val _arrangement = MutableStateFlow(
        decodeArrangement(storedArrangement, applicableSortKeys, applicableGroupKeys.toSet())
    )
    val arrangement: StateFlow<DocArrangement> = _arrangement.asStateFlow()
    private val _rememberArrangement = MutableStateFlow(rememberArrangementInitially)
    val rememberArrangement: StateFlow<Boolean> = _rememberArrangement.asStateFlow()
    private val _grouped = MutableStateFlow<List<DocGroup<CloudDocItem>>>(emptyList())
    val grouped: StateFlow<List<DocGroup<CloudDocItem>>> = _grouped.asStateFlow()
    private val _arrangementIsDefault = MutableStateFlow(
        decodeArrangement(storedArrangement, applicableSortKeys, applicableGroupKeys.toSet()) ==
            defaultArrangement(applicableSortKeys)
    )
    val arrangementIsDefault: StateFlow<Boolean> = _arrangementIsDefault.asStateFlow()

    /** The group keys this screen offers, for the arrangement sheet's radio row. */
    val groupKeys: List<DocGroupBy> get() = applicableGroupKeys

    fun moveSortCriterion(from: Int, to: Int) {
        val list = _arrangement.value.sort.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        applyArrangement(_arrangement.value.copy(sort = list))
    }

    fun toggleSortDirection(key: DocSortKey) {
        applyArrangement(_arrangement.value.copy(
            sort = _arrangement.value.sort.map { if (it.key == key) it.copy(descending = !it.descending) else it },
        ))
    }

    fun setGroupBy(groupBy: DocGroupBy) = applyArrangement(_arrangement.value.copy(groupBy = groupBy))
    fun resetArrangement() = applyArrangement(defaultArrangement(applicableSortKeys))

    /**
     * The user's "remember these settings" switch. Turning it OFF clears the stored value ONCE
     * (so the next launch really does start from the default) and then stops writing; the live
     * arrangement is untouched, because the switch is about persistence, not about this session.
     */
    fun setRememberArrangement(on: Boolean) {
        _rememberArrangement.value = on
        if (on) onArrangementChange(encodeArrangement(_arrangement.value), true)
        else onArrangementChange(null, false)
    }

    /**
     * Sort/group changes never hide a row (unlike a status/category/query change, which can), so
     * unlike [refilter]'s other callers this does not reset the current selection.
     */
    private fun applyArrangement(next: DocArrangement) {
        _arrangement.value = next
        _arrangementIsDefault.value = next == defaultArrangement(applicableSortKeys)
        if (_rememberArrangement.value) onArrangementChange(encodeArrangement(next), true)
        refilter(resetSelection = false)
    }

    fun setItems(items: List<CloudDocItem>) {
        _items.value = items
        if (_selectionMode.value) {
            val present = items.mapTo(mutableSetOf()) { it.initials }
            _selectedIds.value = _selectedIds.value.intersect(present)
        }
        refilter(resetSelection = false)
    }

    fun setStatusFilter(f: CloudDocFilter) { _statusFilter.value = f; refilter(resetSelection = true) }
    fun setCategoryFilter(c: DocCategory?) { _categoryFilter.value = c; refilter(resetSelection = true) }
    fun setQuery(q: String) { _query.value = q; refilter(resetSelection = true) }
    fun openSearch() = searchMode.open()
    fun closeSearch() = searchMode.close()
    fun setShowRemoved(show: Boolean) {
        _showRemoved.value = show
        // The REMOVED filter is only reachable while removed items are shown; hiding them again
        // must not strand the view on the REMOVED list. When that flip happens the effective filter
        // changed, so reset it to ALL and recompute `displayed` (and exit selection mode, like the
        // other filter-changing setters) — otherwise `statusFilter` would report REMOVED with the
        // filter no longer available. Tombstone *presence* is the host's job: onShowRemovedChange
        // re-loads the list with the new includeDeleted value (classic parity), so this controller
        // only owns which filter is selected, not whether tombstones are in `_items`.
        if (!show && _statusFilter.value == CloudDocFilter.REMOVED) {
            _statusFilter.value = CloudDocFilter.ALL
            refilter(resetSelection = true)
        }
        onShowRemovedChange(show)
    }

    private fun refilter(resetSelection: Boolean) {
        if (resetSelection) clearSelection()
        // Run the pure filter over the current item list. Classic semantics: ALL keeps everything
        // (tombstones included when present), REMOVED surfaces only tombstones. Tombstone presence
        // in `_items` is gated by the host's scan (includeDeleted), not stripped here. Then apply
        // the user's arrangement (round 17e-2): sort, then split into groups for the grouped view.
        val filtered = filterCloudDocuments(_items.value, _statusFilter.value, _query.value, _categoryFilter.value)
        val ordered = sortDocuments(filtered, _arrangement.value)
        _displayed.value = ordered
        _grouped.value = groupDocuments(ordered, _arrangement.value.groupBy)
    }

    fun enterSelection() { _selectionMode.value = true }
    fun toggle(id: String) { _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) } }
    fun clearSelection() { _selectionMode.value = false; _selectedIds.value = emptySet() }
    fun selectedItems(): List<CloudDocItem> = _items.value.filter { it.initials in _selectedIds.value }

    fun pushBusy(busy: Boolean) {
        busyCount = (busyCount + if (busy) 1 else -1).coerceAtLeast(0)
        _busy.value = busyCount > 0
    }
    fun setTransferRunning(running: Boolean) { _transferRunning.value = running }

    fun performAction(item: CloudDocItem, action: CloudDocAction) = onAction(action, item.initials)
    fun performBulk(action: CloudDocAction) {
        val applicable = applicableInitials(action, selectedItems(), syncEnabled())
        if (applicable.isNotEmpty()) onBulkAction(action, applicable)
    }
    fun rescan() = onRescan()

    fun showSyncNow(labels: List<String>, checked: List<Boolean>) { _syncNowDialog.value = SyncNowDialogState(labels, checked) }
    fun confirmSyncNow(selected: List<Boolean>) {
        _syncNowDialog.value = null
        onSyncNow(selected.getOrElse(0) { false }, selected.getOrElse(1) { false }, selected.getOrElse(2) { false })
    }
    fun dismissSyncNow() { _syncNowDialog.value = null }

    fun applyRemoval(initials: String) { _items.value = applyOptimisticRemoval(_items.value, initials, syncEnabled()); refilter(false) }
    fun applyPurge(initials: String) { _items.value = applyOptimisticPurge(_items.value, initials); refilter(false) }
    fun setBlocked(initials: String, blocked: Boolean) {
        _items.value = _items.value.map { if (it.initials == initials) it.copy(blocked = blocked) else it }
        refilter(false)
    }
}
