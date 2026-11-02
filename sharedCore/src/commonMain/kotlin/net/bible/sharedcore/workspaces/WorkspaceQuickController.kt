package net.bible.sharedcore.workspaces

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Workspace quick switch list plus immediate creation through [WorkspaceSelectorController].
 * Staged deletes and renames, reordering, cloning, copy-settings and the dirty/Save model stay in
 * the full manager. Listing and selecting existing workspaces are read-only; only [createNew]
 * enters the manager's write path.
 *
 * The current workspace's row is inert rather than hidden — the same "visible but not selectable"
 * idiom `QuickDocPicker` applies to the current document.
 */
class WorkspaceQuickController(
    private val service: WorkspaceService,
    private val scope: CoroutineScope,
    private val writeScope: CoroutineScope = scope,
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

    private var creating = false
    private var disposed = false

    /** Stop navigation from an in-flight creation when this opening leaves composition. */
    fun dispose() { disposed = true }

    /** Reuses management's immediate create-and-select path, including its save/load preparation. */
    fun createNew(name: String) {
        if (creating || disposed) return
        creating = true
        val selector = WorkspaceSelectorController(
            service = service,
            // Loading is part of the confirmed write: cancellation must not release its save barrier.
            scope = writeScope,
            writeScope = writeScope,
            onResult = { id, _ ->
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    if (isActive && !disposed && id != null) onSwitch(id)
                }
            },
            onCancel = {},
            onEditSettings = {},
        )
        service.saveCurrentIntoDb()
        selector.load()
        selector.createNew(name)
    }

    fun select(id: String) {
        if (creating || disposed) return
        if (_rows.value.firstOrNull { it.id == id }?.isCurrent == true) return
        onSwitch(id)
    }
}
