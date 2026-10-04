package net.bible.sharedcore.workspaces

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
    private val onSwitch: (String) -> Unit,
) {
    private val _rows = MutableStateFlow<List<WorkspaceRowVd>>(emptyList())
    val rows: StateFlow<List<WorkspaceRowVd>> = _rows.asStateFlow()

    init { reload() }

    fun reload() { _rows.value = service.loadAll() }

    fun select(id: String) {
        if (_rows.value.firstOrNull { it.id == id }?.isCurrent == true) return
        onSwitch(id)
    }
}
