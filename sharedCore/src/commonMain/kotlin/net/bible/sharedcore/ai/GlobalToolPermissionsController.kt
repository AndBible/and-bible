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

    private fun toolsFor(categoryId: String): List<ToolVd> =
        categories.firstOrNull { (category, _) -> category.id == categoryId }?.second ?: emptyList()

    private fun readToolsFor(categoryId: String): List<ToolVd> = toolsFor(categoryId).filterNot { it.requiresPermission }
    private fun writeToolsFor(categoryId: String): List<ToolVd> = toolsFor(categoryId).filter { it.requiresPermission }

    /**
     * Bulk read-tool toggle for the category header (E2/F35), mirroring classic
     * `ToolPermissionListBuilder.setAllRows` for `readRows`: ON sets every read tool in the category
     * to [ToolPermission.ENABLED] (GLOBAL mode's neutral/first option), OFF to
     * [ToolPermission.DISABLED] (the trailing option). A no-op if the category has no read tools.
     */
    fun setCategoryRead(categoryId: String, enabled: Boolean) {
        val value = if (enabled) ToolPermission.ENABLED else ToolPermission.DISABLED
        val toolIds = readToolsFor(categoryId).map { it.id }
        if (toolIds.isEmpty()) return
        publish(_permissions.value + toolIds.associateWith { value })
    }

    /**
     * Bulk write-tool toggle for the category header (E2/F37), mirroring classic `setAllRows` for
     * `writeRows`: ON sets every write tool in the category to [ToolPermission.ASK] (GLOBAL mode's
     * neutral "ask every time" first option -- NOT [ToolPermission.ALLOW]), OFF to
     * [ToolPermission.DENY]. A no-op if the category has no write tools.
     */
    fun setCategoryWrite(categoryId: String, enabled: Boolean) {
        val value = if (enabled) ToolPermission.ASK else ToolPermission.DENY
        val toolIds = writeToolsFor(categoryId).map { it.id }
        if (toolIds.isEmpty()) return
        publish(_permissions.value + toolIds.associateWith { value })
    }

    /** Aggregate state of the category's read-tool bulk toggle (`null` = no read tools, hide it). */
    fun categoryReadState(categoryId: String): CategoryToggleState? =
        categoryToggleState(readToolsFor(categoryId)) { toolId -> _permissions.value[toolId] ?: ToolPermission.ENABLED }

    /** Aggregate state of the category's write-tool bulk toggle (`null` = no write tools, hide it). */
    fun categoryWriteState(categoryId: String): CategoryToggleState? =
        categoryToggleState(writeToolsFor(categoryId)) { toolId -> _permissions.value[toolId] ?: ToolPermission.ASK }

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
