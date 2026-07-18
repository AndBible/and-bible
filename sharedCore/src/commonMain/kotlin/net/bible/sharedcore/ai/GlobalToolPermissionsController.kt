package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Staging brain for `GlobalToolPermissionsActivity` (global default tool permissions,
 * `ToolPermissionListBuilder.Mode.GLOBAL`).
 *
 * [state] is the static tool catalog (category + tools), read once from
 * [ToolPermissionService.toolsByCategory] — it never changes with edits, since [ToolPermGroupVd]
 * carries no permission field. The per-tool permission is tracked separately in [permissions], a
 * locally-edited working map seeded from [ToolPermissionService.permissionFor] for every tool in
 * the catalog.
 *
 * Classic save-on-apply: [setPermission] and [resetAll] only edit the working map — nothing
 * reaches the service until [save]. This mirrors classic's `GlobalToolPermissionsActivity`
 * reset-all (+ `ToolPermissionListBuilder.resetAll`), which only flips the UI to defaults; nothing
 * persists until the user presses Save. Same staged-editing shape as
 * [AiDocumentFilterController.resetAll].
 */
class GlobalToolPermissionsController(
    private val service: ToolPermissionService,
    private val scope: CoroutineScope,
) {
    private val categories: List<Pair<ToolCategoryVd, List<ToolVd>>> = service.toolsByCategory()
    private val allToolIds: List<String> = categories.flatMap { (_, tools) -> tools.map { it.id } }

    private val _state = MutableStateFlow(categories.map { (category, tools) -> ToolPermGroupVd(category, tools) })
    val state: StateFlow<List<ToolPermGroupVd>> = _state.asStateFlow()

    private fun seedPermissions(): Map<String, ToolPermission> = allToolIds.associateWith(service::permissionFor)

    private var initial: Map<String, ToolPermission> = seedPermissions()

    private val _permissions = MutableStateFlow(initial)
    val permissions: StateFlow<Map<String, ToolPermission>> = _permissions.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /** Edits the working map only; nothing is persisted until [save]. */
    fun setPermission(toolId: String, permission: ToolPermission) {
        publish(_permissions.value + (toolId to permission))
    }

    /**
     * Resets the working map to neutral defaults (write tools → [ToolPermission.ASK], read tools
     * → [ToolPermission.ENABLED]) — a purely local edit, like [setPermission]. Nothing is
     * persisted until [save]; [isDirty] is computed normally against the loaded baseline, so
     * resetting a customized baseline shows dirty until Save.
     */
    fun resetAll() {
        publish(allToolIds.associateWith { toolId -> defaultPermissionFor(toolId) })
    }

    private fun defaultPermissionFor(toolId: String): ToolPermission {
        val requiresPermission = categories.flatMap { (_, tools) -> tools }.first { it.id == toolId }.requiresPermission
        return if (requiresPermission) ToolPermission.ASK else ToolPermission.ENABLED
    }

    /** Persists the working map (classic "Save" button), then re-baselines dirty tracking. */
    fun save() {
        service.save(_permissions.value)
        initial = _permissions.value
        _isDirty.value = false
    }

    private fun publish(next: Map<String, ToolPermission>) {
        _permissions.value = next
        _isDirty.value = next != initial
    }
}
