package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the mutable in-memory state for the ManageLabels list (mirroring classic `data` +
 * `allLabels`): the label list, the String-id sets (selected/autoAssign/changed/deleted/
 * deletedWithOrphaned), the primary ids, the search text, and derives the mode-categorized,
 * name-filtered, 3-key-sorted [rows]. Save/flush and the LabelData round-trip are host concerns;
 * this controller exposes hooks the host calls after those (`applyLabelChanged`,
 * `applyLabelDeleted`, `refresh`) plus result snapshots for building the host's result object.
 */
class ManageLabelsController(
    val mode: ManageLabelsMode,
    private val service: ManageLabelsService,
    @Suppress("unused") private val scope: CoroutineScope,
    // seed from ManageLabelsData (host converts IdType->String):
    initialSelected: Set<String>,
    initialAutoAssign: Set<String>,
    initialAutoAssignPrimary: String?,
    initialBookmarkPrimary: String?,
    private val highlightLabelId: String?,        // STUDYPAD current key label
    // host callbacks (Room/Intent live in the host):
    private val onEditLabel: (labelId: String?) -> Unit,   // null == new label
    private val onSelectStudyPad: (labelId: String) -> Unit,
    private val onSave: () -> Unit,
    private val onReset: () -> Unit,
) {
    private val selected = initialSelected.toMutableSet()
    private val autoAssign = initialAutoAssign.toMutableSet()
    private val changed = mutableSetOf<String>()
    private val deleted = mutableSetOf<String>()
    private val deletedWithOrphaned = mutableSetOf<String>()
    private var autoAssignPrimary = initialAutoAssignPrimary
    private var bookmarkPrimary = initialBookmarkPrimary
    private var labels = service.assignableLabels().toMutableList()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()
    private val _nameSearchInside = MutableStateFlow(false) // NAME_START(false)/NAME_CONTAINS(true); CONTENT in 7b-2
    val nameSearchInside: StateFlow<Boolean> = _nameSearchInside.asStateFlow()

    private val _rows = MutableStateFlow<List<ManageLabelsRow>>(emptyList())
    val rows: StateFlow<List<ManageLabelsRow>> = _rows.asStateFlow()

    init { rebuild() }

    // ---- derived (verbatim classic) ----
    private fun contextSelected(): MutableSet<String> = if (mode == ManageLabelsMode.WORKSPACE) autoAssign else selected
    private fun contextPrimary(): String? = when (mode) {
        ManageLabelsMode.WORKSPACE -> autoAssignPrimary
        ManageLabelsMode.ASSIGN -> bookmarkPrimary
        else -> null
    }
    private fun setContextPrimary(id: String?) { when (mode) {
        ManageLabelsMode.WORKSPACE -> autoAssignPrimary = id
        ManageLabelsMode.ASSIGN -> bookmarkPrimary = id
        else -> {}
    } }

    private fun nameMatches(name: String): Boolean {
        val t = _searchText.value
        if (t.isBlank()) return true
        return if (_nameSearchInside.value) name.contains(t, ignoreCase = true)
        else name.startsWith(t, ignoreCase = true)
    }

    private fun rebuild() {
        val recent = service.recentLabelIds().toSet()
        val overridden = service.overriddenLabelIds()
        val ctx = contextSelected()
        // relink override flag onto labels
        val shown = labels.filter { nameMatches(it.name) }.map { it.copy(hasOverride = overridden.contains(it.id)) }.toMutableList<Any>()
        if (mode.showUnassigned) {
            val unl = service.unlabeledLabel()
            if (nameMatches(unl.name) && !changed.contains(unl.id)) shown.add(unl)
        }
        val headers = mutableListOf<LabelCategory>()
        if (mode.showActiveCategory && ctx.isNotEmpty()) headers.add(LabelCategory.ACTIVE)
        if (!mode.hideCategories) { headers.add(LabelCategory.RECENT); headers.add(LabelCategory.OTHER) }
        val mixed: MutableList<Any> = (shown + headers).toMutableList()
        mixed.sortWith(compareBy(
            { any -> bucket(any, ctx, recent) },
            { any -> if (any is LabelCategory) 1 else 2 },
            { any -> if (any is LabelItem) any.name.lowercase() else "" },
        ))
        _rows.value = mixed.map { any ->
            when (any) {
                is LabelCategory -> ManageLabelsRow.Header(any)
                is LabelItem -> ManageLabelsRow.Item(
                    label = any,
                    checked = ctx.contains(any.id),
                    isAutoAssign = autoAssign.contains(any.id),
                    isPrimary = contextPrimary() == any.id,
                    highlighted = any.id == highlightLabelId,
                )
                else -> error("unreachable")
            }
        }
    }
    private fun bucket(any: Any, ctx: Set<String>, recent: Set<String>): Int {
        val active = mode.showActiveCategory && (any == LabelCategory.ACTIVE || (any is LabelItem && ctx.contains(any.id)))
        val rec = !mode.hideCategories && (any == LabelCategory.RECENT || (any is LabelItem && recent.contains(any.id)))
        return if (active) 1 else if (rec) 2 else 3
    }

    // ---- actions ----
    fun setSearch(t: String) { _searchText.value = t; rebuild() }
    fun setNameSearchInside(inside: Boolean) { _nameSearchInside.value = inside; rebuild() }
    fun reOrder() = rebuild()
    fun toggleChecked(id: String) {
        val ctx = contextSelected()
        if (ctx.contains(id)) { ctx.remove(id); if (contextPrimary() == id) setContextPrimary(ctx.firstOrNull()) }
        else { ctx.add(id); if (contextPrimary() == null && mode.primaryShown) setContextPrimary(id) }
        rebuild()
    }
    fun toggleAutoAssign(id: String) { // workspaceEdits row-icon toggle
        if (autoAssign.contains(id)) { autoAssign.remove(id); if (autoAssignPrimary == id) autoAssignPrimary = autoAssign.firstOrNull() }
        else { autoAssign.add(id); if (autoAssignPrimary == null) autoAssignPrimary = id }
        rebuild()
    }
    fun setPrimary(id: String) { setContextPrimary(id); rebuild() }
    fun toggleFavourite(id: String) {
        val i = labels.indexOfFirst { it.id == id }; if (i < 0) return
        labels[i] = labels[i].copy(favourite = !labels[i].favourite); changed.add(id); rebuild()
    }
    fun editLabel(id: String) = onEditLabel(id)
    fun newLabel() = onEditLabel(null)
    fun selectStudyPad(id: String) = onSelectStudyPad(id)
    fun save() = onSave()
    fun reset() = onReset()

    // ---- host apply hooks (after a LabelEdit round-trip) ----
    fun applyLabelChanged(item: LabelItem, selectedFlag: Boolean?, autoAssignFlag: Boolean?, primaryFlag: Boolean?) {
        val i = labels.indexOfFirst { it.id == item.id }
        if (i >= 0) labels[i] = item else labels.add(item)
        changed.add(item.id)
        selectedFlag?.let { if (it) selected.add(item.id) else selected.remove(item.id) }
        autoAssignFlag?.let { if (it) autoAssign.add(item.id) else autoAssign.remove(item.id) }
        primaryFlag?.let { if (it) setContextPrimary(item.id) }
        rebuild()
    }
    fun applyLabelDeleted(id: String, orphaned: Boolean) {
        deleted.add(id); if (orphaned) deletedWithOrphaned.add(id)
        labels.removeAll { it.id == id }; selected.remove(id); autoAssign.remove(id)
        rebuild()
    }
    fun refresh() = rebuild()

    // ---- result snapshot for the host to build ManageLabelsData ----
    fun resultSelected(): Set<String> = selected
    fun resultAutoAssign(): Set<String> = autoAssign
    fun resultChanged(): Set<String> = changed
    fun resultDeleted(): Set<String> = deleted
    fun resultDeletedWithOrphaned(): Set<String> = deletedWithOrphaned
    fun resultAutoAssignPrimary(): String? = autoAssignPrimary
    fun resultBookmarkPrimary(): String? = bookmarkPrimary
}
