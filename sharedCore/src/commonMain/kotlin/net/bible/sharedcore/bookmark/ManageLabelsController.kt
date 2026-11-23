package net.bible.sharedcore.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.search.SearchModeController

/** Which of the two reset actions [ManageLabelsController.reset] is confirming — the mode decides,
 *  see [ManageLabelsController.reset]; the screen uses it to pick the right question. */
enum class ManageLabelsResetKind { WORKSPACE, HIDE_LABELS }

data class ManageLabelsSnapshot(
    val selected: Set<String>,
    val autoAssign: Set<String>,
    val changed: Set<String>,
    val deleted: Set<String>,
    val deletedWithOrphaned: Set<String>,
    val autoAssignPrimary: String?,
    val bookmarkPrimary: String?,
    val labels: List<LabelItem>,
    val search: String,
    val searchMode: SearchMode,
    val filters: Set<LabelFilter>,
    val searchOpen: Boolean,
)

/** Which modal the ManageLabels list is currently showing (screen-local, driven by the controller). */
sealed interface ManageLabelsDialog {
    data object None : ManageLabelsDialog
    data class ConfirmReset(val kind: ManageLabelsResetKind) : ManageLabelsDialog
}

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
    private val scope: CoroutineScope,
    // seed from ManageLabelsData (host converts IdType->String):
    initialSelected: Set<String>,
    initialAutoAssign: Set<String>,
    initialAutoAssignPrimary: String?,
    initialBookmarkPrimary: String?,
    private val highlightLabelId: String?,        // STUDYPAD current key label
    // host callbacks (Room/Intent live in the host):
    private val onEditLabel: (labelId: String?) -> Unit,   // null == new label
    private val onSelectStudyPad: (labelId: String, firstMatchEntryId: String?) -> Unit,
    private val onSave: () -> Unit,
    private val onReset: () -> Unit,
    private val restored: ManageLabelsSnapshot? = null,
) {
    private val selected = initialSelected.toMutableSet()
    private val autoAssign = initialAutoAssign.toMutableSet()
    private val changed = mutableSetOf<String>()
    private val deleted = mutableSetOf<String>()
    private val deletedWithOrphaned = mutableSetOf<String>()
    private var autoAssignPrimary = initialAutoAssignPrimary
    private var bookmarkPrimary = initialBookmarkPrimary
    // The seeds, kept for [hasChanges].
    private val seedSelected = initialSelected.toSet()
    private val seedAutoAssign = initialAutoAssign.toSet()
    private val seedAutoAssignPrimary = initialAutoAssignPrimary
    private val seedBookmarkPrimary = initialBookmarkPrimary
    /** Empty until the initial [load] has read them (the service calls are `suspend`). */
    private var labels = mutableListOf<LabelItem>()
    private var labelsLoaded = false
    /** Cached service values [rebuild] reads; refreshed by [load] (initially and on [refresh]). */
    private var unlabeled: LabelItem? = null
    private var overrides: Map<String, BookmarkDisplayStyle> = emptyMap()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()
    private val _searchMode = MutableStateFlow(SearchMode.NAME_START)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _filters = MutableStateFlow<Set<LabelFilter>>(emptySet())
    val filters: StateFlow<Set<LabelFilter>> = _filters.asStateFlow()

    // Owns the MODE only; the query stays in _searchText because setSearch also drives the debounced
    // StudyPad content search. The clear callback also drops the row FILTERS: they are offered only
    // inside this bar, so one surviving its dismissal would leave the list filtered with nothing on
    // screen saying so. Filters first, then the query — setSearch rebuilds, and it must already see
    // the cleared set.
    private val searchBarMode = SearchModeController(onClearQuery = { _filters.value = emptySet(); setSearch("") })
    val searchModeActive: StateFlow<Boolean> = searchBarMode.active

    private val _rows = MutableStateFlow<List<ManageLabelsRow>>(emptyList())
    val rows: StateFlow<List<ManageLabelsRow>> = _rows.asStateFlow()

    private val _styleTagsVisible = MutableStateFlow(service.styleTagsVisible())
    val styleTagsVisible: StateFlow<Boolean> = _styleTagsVisible.asStateFlow()

    private val _dialog = MutableStateFlow<ManageLabelsDialog>(ManageLabelsDialog.None)
    val dialog: StateFlow<ManageLabelsDialog> = _dialog.asStateFlow()

    // ---- StudyPad content-search debounce (verbatim classic ManageLabels.kt:804-842) ----
    private var contentSearchJob: Job? = null
    // Bumped on every dispatch (whether or not a job is actually launched) so a completed job can
    // tell whether it's still the most recent request before publishing its results -- belt & braces
    // alongside job cancellation, in case a slow service call doesn't observe cancellation promptly.
    private var searchGeneration: Long = 0L

    // The last emitted row sequence, as order keys. Classic's list is deliberately STICKY: its
    // toggle handlers end in updateLabelList(rePopulate = false, reOrder = false)
    // (ManageLabels.kt:865), which skips the sortWith block entirely, so a row never moves under
    // the user's finger and the ACTIVE header only ever materialises during a repopulate
    // (ManageLabels.kt:789-801). Re-sorting on every mutation is what made a ⚡ tap look like the
    // label had vanished into another section.
    private var lastOrder: List<String>? = null

    // Rows stay empty until the first load lands (one frame on device); the rows are then built
    // exactly as a synchronous constructor built them.
    init {
        restored?.let {
            selected.clear(); selected.addAll(it.selected)
            autoAssign.clear(); autoAssign.addAll(it.autoAssign)
            changed.addAll(it.changed); deleted.addAll(it.deleted); deletedWithOrphaned.addAll(it.deletedWithOrphaned)
            autoAssignPrimary = it.autoAssignPrimary; bookmarkPrimary = it.bookmarkPrimary
            labels.addAll(it.labels)
            _searchText.value = it.search
            _searchMode.value = it.searchMode
            _filters.value = it.filters
            if (it.searchOpen) searchBarMode.open()
        }
        scope.launch {
            load(); rebuild(reorder = restored == null)
            // A restored StudyPad CONTENT query shows content hits, not the name-filter fallback.
            if (restored != null && mode == ManageLabelsMode.STUDYPAD && _searchMode.value == SearchMode.CONTENT &&
                _searchText.value.length >= 3) dispatchSearchOrRebuild()
        }
    }

    /** Value-only working set; services, callbacks and jobs always belong to the new host. */
    fun snapshot() = ManageLabelsSnapshot(
        selected.toSet(), autoAssign.toSet(), changed.toSet(), deleted.toSet(), deletedWithOrphaned.toSet(),
        autoAssignPrimary, bookmarkPrimary, labels.filter { it.id in changed }, _searchText.value, _searchMode.value,
        _filters.value, searchModeActive.value,
    )

    /**
     * Reads the service's label data. The label list itself is read only once (later changes come
     * through [applyLabelChanged]/[applyLabelDeleted], as before); a change applied before this first
     * read landed wins over the loaded copy, and a label deleted meanwhile stays deleted. The
     * Unlabeled label and the workspace overrides are re-read on every call.
     */
    private suspend fun load() {
        val loadedLabels = if (labelsLoaded) null else service.assignableLabels()
        val loadedUnlabeled = service.unlabeledLabel()
        val loadedOverrides = service.overriddenLabelStyles()
        if (loadedLabels != null && !labelsLoaded) {
            labelsLoaded = true
            val applied = labels.associateBy { it.id }
            val merged = loadedLabels.filterNot { deleted.contains(it.id) }.map { applied[it.id] ?: it }
            labels = (merged + labels.filter { l -> merged.none { it.id == l.id } }).toMutableList()
        }
        unlabeled = loadedUnlabeled
        overrides = loadedOverrides
    }

    private fun dispatchSearchOrRebuild() {
        contentSearchJob?.cancel()
        val text = _searchText.value
        if (mode == ManageLabelsMode.STUDYPAD && _searchMode.value == SearchMode.CONTENT && text.length >= 3) {
            val generation = ++searchGeneration
            contentSearchJob = scope.launch {
                delay(300)
                val results = try {
                    service.searchStudyPadsByContent(text)
                } catch (e: Exception) {
                    emptyList()
                }
                // Stale-guard: only the most recent dispatch may publish (defense in depth on top of
                // the cancel() above).
                if (generation == searchGeneration) {
                    // A content-search result list is not a `lastOrder`-shaped sequence at all (it's
                    // SearchResult rows, not label/header keys), so the "lastOrder names the last
                    // emitted sequence" invariant must be broken deliberately here, not left stale.
                    if (results.isEmpty()) rebuild(reorder = true) else { lastOrder = null; _rows.value = results }
                }
            }
        } else {
            searchGeneration++ // invalidate any still-in-flight content search
            rebuild(reorder = true)
        }
    }

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

    // ---- classic ensureNotAutoAssignPrimaryLabel / ensureNotBookmarkPrimaryLabel (ManageLabels.kt:514-524) ----
    // "if this label IS the primary, or there is no primary at all, reassign it to the first
    // remaining member of the relevant set (or null if that set is now empty)."
    private fun ensureNotContextPrimary(id: String) {
        if (contextPrimary() == id || contextPrimary() == null) setContextPrimary(contextSelected().toList().firstOrNull())
    }
    private fun ensureNotAutoAssignPrimary(id: String) {
        if (autoAssignPrimary == id || autoAssignPrimary == null) autoAssignPrimary = autoAssign.toList().firstOrNull()
    }
    private fun ensureNotBookmarkPrimary(id: String) {
        if (bookmarkPrimary == id || bookmarkPrimary == null) bookmarkPrimary = selected.toList().firstOrNull()
    }

    // Classic already-selected bypass (ManageLabels.kt:847-850 labelMatches): uses the RAW
    // `data.selectedLabels` field, not the mode-aware getter — WORKSPACE-mode ManageLabelsData never
    // populates selectedLabels (only autoAssignLabels, via applyFrom), so this bypass is a no-op in
    // WORKSPACE mode. Mirror that with the raw `selected` field here, NOT contextSelected() (which
    // would wrongly resolve to `autoAssign` in WORKSPACE and bypass auto-assigned labels).
    //
    // The bypass also outranks the round-17b FILTERS, deliberately: its whole job is that a label
    // the user has already ticked never disappears from under them, and a filter is exactly the
    // kind of narrowing that would otherwise do it.
    private fun matches(item: LabelItem): Boolean {
        if (selected.contains(item.id)) return true
        if (!passesFilters(item)) return false
        val t = _searchText.value
        if (t.isBlank()) return true
        return when (_searchMode.value) {
            SearchMode.NAME_START -> item.name.startsWith(t, ignoreCase = true)
            // CONTENT's name-branch is unused once content search (Task 2) is active; as a
            // name-filter fallback it behaves like NAME_CONTAINS.
            SearchMode.NAME_CONTAINS, SearchMode.CONTENT -> item.name.contains(t, ignoreCase = true)
        }
    }

    /** ANDed, and each reads the same source its row control draws from: `autoAssign` for the ⚡
     *  column, the label's own `favourite` for the ♥ one. The Unlabeled pseudo-label is neither, so
     *  any active filter excludes it — which is correct: it has no workspace toggles at all. */
    private fun passesFilters(item: LabelItem): Boolean {
        val active = _filters.value
        if (active.contains(LabelFilter.AUTO_ADD) && !autoAssign.contains(item.id)) return false
        if (active.contains(LabelFilter.FAVOURITE) && !item.favourite) return false
        return true
    }

    private fun rebuild(reorder: Boolean = false) {
        val recent = service.recentLabelIds().toSet()
        val ctx = contextSelected()
        // relink override style onto labels
        val shown = labels.filter { matches(it) }
            .map { it.copy(overrideStyle = overrides[it.id]) }.toMutableList<Any>()
        val unl = unlabeled
        if (mode.showUnassigned && unl != null) {
            // Same relink as every real label above (:148-149) -- classic's adapter marks the ⚙
            // override tag for ANY overridden id, Unlabeled included (ManageLabelItemAdapter.kt:236).
            if (matches(unl) && !changed.contains(unl.id)) shown.add(unl.copy(overrideStyle = overrides[unl.id]))
        }
        // Sticky path: reuse the previous sequence, headers INCLUDED. The header set has to be
        // frozen too, not recomputed -- classic inserts headers only during a repopulate
        // (ManageLabels.kt:789-801), so the ACTIVE header neither materialises when the first label
        // is auto-assigned nor vanishes when the last one is un-assigned. Recomputing it would also
        // change the row-key set and so defeat the freeze on the very toggle this fixes.
        val previous = if (reorder) null else lastOrder
        val sticky = previous?.let { prev -> stickyOrder(prev, shown + prev.mapNotNull(::headerForKey)) }
        val mixed: MutableList<Any> = if (sticky != null) {
            sticky.toMutableList()
        } else {
            val fresh: MutableList<Any> = (shown + currentHeaders(ctx, shown, recent)).toMutableList()
            fresh.sortWith(compareBy(
                { any -> bucket(any, ctx, recent) },
                { any -> if (any is LabelCategory) 1 else 2 },
                { any -> if (any is LabelItem) any.name.lowercase() else "" },
            ))
            lastOrder = fresh.map(::orderKey)
            fresh
        }
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

    private fun orderKey(any: Any): String = when (any) {
        is LabelCategory -> "H:$any"
        is LabelItem -> "L:${any.id}"
        else -> error("unreachable")
    }

    /**
     * The headers a fresh sort would show, from the current context. "Recent labels" is left out when no
     * shown label would sit under it (nothing recent yet, or every recent label is already active): an
     * empty header only pushes the list down.
     */
    private fun currentHeaders(ctx: Set<String>, shown: List<Any>, recent: Set<String>): List<LabelCategory> {
        val headers = mutableListOf<LabelCategory>()
        if (mode.showActiveCategory && ctx.isNotEmpty()) headers.add(LabelCategory.ACTIVE)
        if (!mode.hideCategories) {
            if (shown.any { it is LabelItem && bucket(it, ctx, recent) == 2 }) headers.add(LabelCategory.RECENT)
            headers.add(LabelCategory.OTHER)
        }
        return headers
    }

    /** The category a remembered order key names, or `null` if the key is a label's. */
    private fun headerForKey(key: String): LabelCategory? =
        LabelCategory.entries.firstOrNull { orderKey(it) == key }

    /**
     * [previous]'s sequence refilled with the fresh values — or `null` when it cannot be reused and
     * a full sort is required.
     *
     * Reuse needs the row-key set to be UNCHANGED. That is the honest condition: a label that
     * appeared (the already-selected search bypass in [matches]) or vanished (a delete) has no
     * place in the old sequence, and inventing one would be worse than regrouping.
     */
    private fun stickyOrder(previous: List<String>, fresh: List<Any>): List<Any>? {
        val byKey = fresh.associateBy(::orderKey)
        if (byKey.size != fresh.size) return null              // duplicate key: never reuse
        if (byKey.keys != previous.toSet()) return null         // set changed: full sort
        return previous.map { byKey.getValue(it) }
    }

    // ---- actions ----
    fun setSearch(t: String) { _searchText.value = t; dispatchSearchOrRebuild() }
    fun setSearchMode(mode: SearchMode) { _searchMode.value = mode; dispatchSearchOrRebuild() }
    /**
     * The assign sheet's search (#4215): [t] matched anywhere in a label's name, whatever mode was set before (the sheet
     * has no mode toggle). One rebuild, like [setSearch]. The mode stays NAME_CONTAINS: the sheet's controller is an
     * ASSIGN one, and only a STUDYPAD screen persists its search mode (`MANAGE_LABELS_SEARCH_MODE_KEY`).
     */
    fun setNameSearch(t: String) {
        // The sheet calls this once on opening with the current text: no rebuild when nothing changes, so a re-show or
        // rotation keeps the sticky row order.
        if (_searchMode.value == SearchMode.NAME_CONTAINS && _searchText.value == t) return
        _searchMode.value = SearchMode.NAME_CONTAINS; setSearch(t)
    }

    private var searchModeSeeded = restored != null
    fun seedSearchMode(initialMode: () -> SearchMode) {
        if (searchModeSeeded) return
        searchModeSeeded = true
        val mode = initialMode()
        if (_searchMode.value != mode) setSearchMode(mode)
    }
    /** The search-options sheet's filter toggles. A filter changes the row-key SET, so this always
     *  re-sorts: `stickyOrder` would refuse to reuse the old sequence anyway, and saying so here is
     *  clearer than relying on that. */
    fun toggleFilter(filter: LabelFilter) {
        val current = _filters.value
        _filters.value = if (current.contains(filter)) current - filter else current + filter
        rebuild(reorder = true)
    }
    fun openSearch() = searchBarMode.open()
    fun closeSearch() = searchBarMode.close()
    /** The ⋮ Re-order action: the user asking for the regrouping the toggles deliberately skip. */
    fun reOrder() = rebuild(reorder = true)
    /** The ⋮ "Show style examples" toggle. Presentation only — deliberately does NOT rebuild(),
     *  because the row content is unchanged and a rebuild would drag the sticky-order machinery in
     *  for nothing. */
    fun toggleStyleTags() {
        val next = !_styleTagsVisible.value
        _styleTagsVisible.value = next
        service.setStyleTagsVisible(next)
    }
    fun toggleChecked(id: String) {
        val ctx = contextSelected()
        // An add-on label is never put on an own bookmark (BookmarkControl refuses it): its ASSIGN tick does not move.
        // Nor is it auto-assigned (WORKSPACE ticks are the auto-assign set); a stale one can still be removed there.
        val addon = labels.any { it.id == id && it.isAddon }
        if (addon && mode == ManageLabelsMode.ASSIGN) return
        if (ctx.contains(id)) { ctx.remove(id); ensureNotContextPrimary(id) }
        else if (addon && mode == ManageLabelsMode.WORKSPACE) return
        else { ctx.add(id); if (contextPrimary() == null && mode.primaryShown) setContextPrimary(id) }
        rebuild()
    }
    fun toggleAutoAssign(id: String) { // workspaceEdits row-icon toggle
        if (labels.any { it.id == id && it.isAddon }) return
        if (autoAssign.contains(id)) { autoAssign.remove(id); ensureNotAutoAssignPrimary(id) }
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
    fun selectStudyPad(labelId: String, firstMatchEntryId: String?) = onSelectStudyPad(labelId, firstMatchEntryId)
    fun save() = onSave()

    /**
     * Classic's `askConfirmation` question ("Do you want to remove all auto-assign labels..." /
     * "...reset setting for hiding..."), moved off the host into this controller's own [dialog]
     * state (Task 13) -- the two messages differ by [mode], which is why [ConfirmReset] carries a
     * [ManageLabelsResetKind] for the screen to pick the right one from. [confirmDialog] runs the
     * confirmed reset, which stays a host callback: HIDELABELS' branch also LEAVES the screen with a
     * result, which needs Room-adjacent host state this controller does not have.
     */
    fun reset() {
        _dialog.value = ManageLabelsDialog.ConfirmReset(
            when (mode) {
                ManageLabelsMode.WORKSPACE -> ManageLabelsResetKind.WORKSPACE
                ManageLabelsMode.HIDELABELS -> ManageLabelsResetKind.HIDE_LABELS
                else -> throw RuntimeException("Illegal value")
            },
        )
    }
    fun confirmDialog() {
        val wasConfirmReset = _dialog.value is ManageLabelsDialog.ConfirmReset
        _dialog.value = ManageLabelsDialog.None
        if (wasConfirmReset) onReset()
    }
    fun dismissDialog() { _dialog.value = ManageLabelsDialog.None }

    /**
     * The WORKSPACE ⋮ "Clear auto-assign labels" action (round 17b). Empties the auto-assign set
     * and its primary IN PLACE and leaves the user on the list — the ⚡ column goes hollow down the
     * whole list, which is what "see what happened" means here.
     *
     * Deliberately NOT [reset]: that one sets a result flag and finishes the activity, which
     * HIDELABELS still needs because ITS reset means "revert to the inherited value"
     * (`setNonSpecific`/`onRevert`), something an empty selection cannot express. WORKSPACE's can:
     * `WorkspaceSettings.updateFrom` assigns both fields straight from the result.
     */
    fun clearAutoAssign() {
        autoAssign.clear()
        autoAssignPrimary = null
        rebuild(reorder = true)
    }

    // ---- host apply hooks (after a LabelEdit round-trip) ----
    // Mirrors classic ManageLabels.editLabel's result handling (ManageLabels.kt:614-634): autoAssign,
    // autoAssignPrimary and bookmarkPrimary are each applied UNCONDITIONALLY (not mode-gated) — every
    // caller reads all three via updateFrom regardless of which mode opened the edit screen, so both
    // primaries must propagate independently of `mode`'s single contextPrimary() mapping. Only
    // `selectedFlag` mirrors classic's own `if(data.mode == Mode.ASSIGN)` gate — the host decides
    // whether to pass the round-tripped value at all (LabelEdit hides that checkbox outside ASSIGN,
    // so its value round-trips unchanged there anyway). Each primary flag, when true, claims that
    // primary; else falls back through its own ensureNot*Primary so a primary that pointed at this
    // label (or was already null) doesn't dangle.
    fun applyLabelChanged(
        item: LabelItem,
        selectedFlag: Boolean,
        autoAssignFlag: Boolean,
        bookmarkPrimaryFlag: Boolean,
        autoAssignPrimaryFlag: Boolean,
    ) {
        val i = labels.indexOfFirst { it.id == item.id }
        // The editor's result knows nothing of the module: the add-on marking stays with the listed label.
        val kept = if (item.addonModuleName == null && i >= 0) item.copy(addonModuleName = labels[i].addonModuleName) else item
        if (i >= 0) labels[i] = kept else labels.add(kept)
        changed.add(item.id)
        if (selectedFlag) selected.add(item.id) else selected.remove(item.id)
        if (autoAssignFlag) autoAssign.add(item.id) else autoAssign.remove(item.id)
        if (bookmarkPrimaryFlag) bookmarkPrimary = item.id else ensureNotBookmarkPrimary(item.id)
        if (autoAssignPrimaryFlag) autoAssignPrimary = item.id else ensureNotAutoAssignPrimary(item.id)
        rebuild(reorder = true)
    }
    // Mirrors classic ManageLabels.deleteLabel (ManageLabels.kt:537-550): remove from every set +
    // `changed`, then ensureNot* both primaries so a deleted primary is reassigned, never left dangling.
    fun applyLabelDeleted(id: String, orphaned: Boolean) {
        deleted.add(id); if (orphaned) deletedWithOrphaned.add(id)
        labels.removeAll { it.id == id }
        selected.remove(id); autoAssign.remove(id); changed.remove(id)
        ensureNotBookmarkPrimary(id)
        ensureNotAutoAssignPrimary(id)
        rebuild(reorder = true)
    }
    /** Re-reads the Unlabeled label and the workspace overrides, then re-sorts (on [scope]). */
    fun refresh() { scope.launch { load(); rebuild(reorder = true) } }

    /** Like [refresh], but reads the label list again too (a label was created outside this screen, e.g. "Copy to my labels"). */
    fun reloadLabels() { labelsLoaded = false; refresh() }

    // ---- current in-memory label items (host save-time favourite sourcing) ----
    // The list's quick favourite-toggle (toggleFavourite) only flips this controller's own LabelItem
    // copy, marking the id `changed` — it does NOT touch the host's authoritative
    // `BookmarkEntities.Label` map (labelsById), which is what saveAndExit actually persists. Classic
    // gets this for free because its adapter mutates the SAME Label instance later saved from
    // `allLabels` (ManageLabelItemAdapter.kt:179-183). The Compose host must instead read the current
    // favourite back from here at save time and apply it onto the Label about to be persisted.
    fun currentLabelItems(): List<LabelItem> = labels.toList()

    /**
     * Whether this visit changed anything a save would write or deliver: the ticked set, the auto-assign set or
     * either primary differs from the seeds, or a label was edited, created, favourited or deleted. Search text,
     * search mode and filters are presentation and do not count. The assign sheet closes without saving when this
     * is false (issue #4201): a save would give every selected bookmark the union it was seeded with.
     */
    fun hasChanges(): Boolean =
        selected != seedSelected || autoAssign != seedAutoAssign ||
            autoAssignPrimary != seedAutoAssignPrimary || bookmarkPrimary != seedBookmarkPrimary ||
            changed.isNotEmpty() || deleted.isNotEmpty() || deletedWithOrphaned.isNotEmpty()

    /**
     * Whether a save should rewrite the bookmarks' labels: the ticked set differs from the seed, or the bookmark's main
     * label does and there was one to begin with. With no seeded main label (several bookmarks selected, whose payload
     * carries none) a main label appears as a side effect of ticking (`ensureNotContextPrimary`), even when the ticks
     * end where they started, so it does not count there (issue #4201, review round 3). Label edits, favourites and
     * auto-assign do not count: they are saved without touching the bookmarks. Nor does a deleted label leaving the
     * ticks: the label's delete removes it from its bookmarks (foreign-key cascade).
     */
    fun bookmarkLabelsChanged(): Boolean =
        selected != seedSelected - deleted - deletedWithOrphaned ||
            (seedBookmarkPrimary != null && bookmarkPrimary != seedBookmarkPrimary)

    // ---- result snapshot for the host to build ManageLabelsData ----
    fun resultSelected(): Set<String> = selected
    fun resultAutoAssign(): Set<String> = autoAssign
    fun resultChanged(): Set<String> = changed
    fun resultDeleted(): Set<String> = deleted
    fun resultDeletedWithOrphaned(): Set<String> = deletedWithOrphaned
    fun resultAutoAssignPrimary(): String? = autoAssignPrimary
    fun resultBookmarkPrimary(): String? = bookmarkPrimary
}
