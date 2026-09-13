package net.bible.sharedcore.workspaces

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.search.SearchModeController

/**
 * Staging brain for the workspace selector. Owns the working ordered list (as [WorkspaceRowVd]) plus
 * the created/deleted/renamed/changed staging sets, mirroring the classic WorkspaceSelectorActivity:
 * every edit is staged and applied on Save; session-created workspaces are hard-deleted on Cancel/back;
 * the only remaining workspace cannot be deleted; selecting a workspace while dirty prompts save/discard.
 * The [service] holds the authoritative working entities; this controller carries the metadata needed
 * to flush them.
 */
class WorkspaceSelectorController(
    private val service: WorkspaceService,
    private val scope: CoroutineScope,
    private val onResult: (workspaceId: String?, changed: Boolean) -> Unit,
    private val onCancel: () -> Unit,
    private val onEditSettings: (id: String) -> Unit,
) {
    private val working = mutableListOf<WorkspaceRowVd>()
    private val created = mutableSetOf<String>()
    private val deleted = mutableSetOf<String>()
    private val renamed = mutableMapOf<String, String>()
    private val changed = mutableSetOf<String>()

    private val _workspaces = MutableStateFlow<List<WorkspaceRowVd>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceRowVd>> = _workspaces.asStateFlow()
    private val _dirty = MutableStateFlow(false); val dirty: StateFlow<Boolean> = _dirty.asStateFlow()
    private val _query = MutableStateFlow(""); val query: StateFlow<String> = _query.asStateFlow()
    private val _filtering = MutableStateFlow(false); val filtering: StateFlow<Boolean> = _filtering.asStateFlow()
    private val searchMode = SearchModeController(onClearQuery = { setQuery("") })

    /**
     * Whether the top bar is showing its inline search field. Held in the controller rather than the
     * composable so it survives recomposition, and so closing routes through [setQuery] — which is
     * what keeps [filtering], and therefore the row drag handles, in step with it.
     */
    val searchModeActive: StateFlow<Boolean> = searchMode.active
    private val _copy = MutableStateFlow<CopySettingsState?>(null); val copySettingsState: StateFlow<CopySettingsState?> = _copy.asStateFlow()
    private val _pendingSelect = MutableStateFlow<String?>(null); val pendingSelectId: StateFlow<String?> = _pendingSelect.asStateFlow()
    private val _canDelete = MutableStateFlow(false); val canDelete: StateFlow<Boolean> = _canDelete.asStateFlow()

    fun load() {
        working.clear(); working.addAll(service.loadAll())
        created.clear(); deleted.clear(); renamed.clear(); changed.clear()
        _query.value = ""; _filtering.value = false; _dirty.value = false; _copy.value = null; _pendingSelect.value = null
        searchMode.reset()
        publish()
    }

    private fun publish() {
        val q = _query.value.trim()
        _filtering.value = q.isNotEmpty()
        _workspaces.value =
            if (q.isEmpty()) working.toList()
            else working.filter {
                it.name.contains(q, ignoreCase = true) || (it.summary?.contains(q, ignoreCase = true) == true)
            }
        _canDelete.value = working.size > 1
    }

    fun setQuery(q: String) { _query.value = q; publish() }

    fun openSearch() = searchMode.open()

    /** Classic parity: collapsing the SearchView clears the filter (RecyclerViewSearchHelper:110-114). */
    fun closeSearch() = searchMode.close()

    fun moveIndex(from: Int, to: Int) {
        if (from == to || from !in working.indices || to !in working.indices) return
        working.add(to, working.removeAt(from))
        for (i in minOf(from, to)..maxOf(from, to)) changed.add(working[i].id)
        _dirty.value = true; publish()
    }

    fun requestDelete(id: String) {
        if (working.size <= 1) return
        working.removeAll { it.id == id }
        deleted.add(id); renamed.remove(id); changed.remove(id)
        _dirty.value = true; publish()
    }

    fun rename(id: String, name: String) {
        val i = working.indexOfFirst { it.id == id }; if (i < 0) return
        working[i] = working[i].copy(name = name)
        renamed[id] = name; changed.add(id); _dirty.value = true; publish()
    }

    fun clone(sourceId: String, name: String) {
        val v = service.cloneWorkspace(sourceId, name)
        val at = working.indexOfFirst { it.id == sourceId }
        working.add(if (at >= 0) at + 1 else working.size, v)
        created.add(v.id); _dirty.value = true; publish()
    }

    fun createNew(name: String) {
        val v = service.createWorkspace(name)
        // NOTE: unlike clone(), classic createNewWorkspace() does NOT track the new workspace in
        // workspacesCreated — only cloneWorkspace() does. A created-and-persisted workspace must
        // survive a subsequent discard (Don't save); only session clones are discardable.
        working.add(v); publish()
        selectWorkspace(v.id)          // terminal, like classic goToWorkspace(newId)
    }

    fun editSettings(id: String) = onEditSettings(id)

    fun settingsBundleJson(id: String): String = service.settingsBundleJson(id)

    fun applyWorkspaceSettings(id: String, settingsBundleJson: String, reset: Boolean) {
        val v = service.applyWorkspaceSettings(id, settingsBundleJson, reset)
        val i = working.indexOfFirst { it.id == id }; if (i >= 0) working[i] = v
        changed.add(id); _dirty.value = true; publish()
    }

    fun beginCopySettings(id: String) {
        _copy.value = CopySettingsState.ChooseTypes(id, service.settingTypeLabels(id))
    }

    fun beginCopySettingsToGlobal(id: String) {
        _copy.value = CopySettingsState.ToGlobal(id, service.settingTypeLabels(id))
    }

    fun chooseCopyTypes(typeIndices: List<Int>) {
        val state = _copy.value
        if (typeIndices.isEmpty()) { _copy.value = null; return }
        when (state) {
            is CopySettingsState.ChooseTypes ->
                _copy.value = CopySettingsState.ChooseTargets(state.sourceId, typeIndices, working.toList())
            is CopySettingsState.ToGlobal -> {
                service.copySettingsToGlobal(state.sourceId, typeIndices); _copy.value = null
            }
            else -> _copy.value = null
        }
    }

    fun chooseCopyTargets(targetIds: List<String>) {
        val state = _copy.value as? CopySettingsState.ChooseTargets ?: run { _copy.value = null; return }
        val effective = targetIds.filter { it != state.sourceId }   // classic skips the source
        if (effective.isNotEmpty()) {
            val refreshed = service.copySettings(state.sourceId, state.typeIndices, effective)
            refreshed.forEach { v -> val i = working.indexOfFirst { it.id == v.id }; if (i >= 0) working[i] = v }
            changed.addAll(effective)                                // fix-forward: persist the copy on Save
            _dirty.value = true
        }
        _copy.value = null; publish()
    }

    fun cancelCopySettings() { _copy.value = null }

    fun selectWorkspace(id: String) {
        if (_dirty.value) { _pendingSelect.value = id }
        else { service.applyChanges(order(), deleted.toList(), renamed.toMap(), changed.toSet()); onResult(id, false) }
    }

    fun confirmPendingSelect(save: Boolean) {
        val id = _pendingSelect.value ?: return
        _pendingSelect.value = null
        if (save) { service.applyChanges(order(), deleted.toList(), renamed.toMap(), changed.toSet()); onResult(id, true) }
        else { service.deleteCreated(created.toList()); onResult(id, false) }
    }

    fun dismissPendingSelect() { _pendingSelect.value = null }

    fun save() {
        service.applyChanges(order(), deleted.toList(), renamed.toMap(), changed.toSet())
        onResult(null, true)
    }

    fun cancel() { discardCreated(); onCancel() }

    /**
     * [cancel]'s cleanup WITHOUT its exit -- the half a host needs when it is being destroyed
     * mid-visit and there is nobody left to navigate.
     *
     * Classic `WorkspaceSelectorComposeActivity.onDetachedFromWindow` ran the whole of `cancel()`
     * there, exit included, because "the exit" was just `finish()` on an Activity that was already
     * going away. In the nav-graph host it is a `popBackStack()`/`finish()` on a host that is
     * mid-`onDestroy`, which is both meaningless and unsafe -- so the host calls this instead.
     *
     * Idempotent: [created] is cleared, so a later [cancel] on the same controller does not ask the
     * service to delete the same rows twice.
     */
    fun discardCreated() {
        if (created.isEmpty()) return
        service.deleteCreated(created.toList())
        created.clear()
    }

    private fun order(): List<String> = working.map { it.id }
}
