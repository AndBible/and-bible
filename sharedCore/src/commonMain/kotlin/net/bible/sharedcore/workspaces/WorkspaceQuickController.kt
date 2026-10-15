package net.bible.sharedcore.workspaces

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 15b's workspace QUICK switch (spec §4.4): a read-only view of the workspace list plus
 * "switch to this one". Everything the full `WorkspaceSelectorController` does — staged deletes and
 * renames, reordering, cloning, copy-settings, the dirty/Save model — is deliberately absent; that
 * is the whole quick/full distinction, and [itNeverMutatesTheWorkspaceService] pins it.
 *
 * The current workspace's row is inert rather than hidden — the same "visible but not selectable"
 * idiom `QuickDocPicker` applies to the current document.
 */
class WorkspaceQuickController(
    private val service: WorkspaceService,
    private val scope: CoroutineScope,
    private val onSwitch: (String) -> Unit,
) {
    private val _rows = MutableStateFlow<List<WorkspaceRowVd>>(emptyList())
    val rows: StateFlow<List<WorkspaceRowVd>> = _rows.asStateFlow()

    init { reload() }

    private var loadJob: Job? = null

    /** Rows stay as they were until the load completes; a newer reload supersedes an older one still in flight. */
    fun reload() {
        loadJob?.cancel()
        loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { _rows.value = service.loadAll() }
    }

    fun select(id: String) {
        if (_rows.value.firstOrNull { it.id == id }?.isCurrent == true) return
        onSwitch(id)
    }
}
