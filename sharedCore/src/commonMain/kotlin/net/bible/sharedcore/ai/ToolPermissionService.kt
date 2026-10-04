package net.bible.sharedcore.ai

/**
 * Host seam for `GlobalToolPermissionsActivity` (global default tool permissions —
 * `ToolPermissionListBuilder.Mode.GLOBAL`).
 *
 * Wraps `ToolRegistry.getConfigurableToolsByCategory()`/`getCategoryDisplayName`/`getDisplayName`
 * for the tool catalog, and `GlobalAiSettings.permanentlyAllowedTools`/`permanentlyDeniedTools`
 * (read via `CommonUtils.aiSettings`, persisted via `GlobalAiSettingsDao.set`) for permission
 * state. GLOBAL mode only — read tools (`!requiresPermission`) are two-state
 * ([ToolPermission.ENABLED]/[ToolPermission.DISABLED]); write tools (`requiresPermission`) are
 * three-state ([ToolPermission.ASK]/[ToolPermission.ALLOW]/[ToolPermission.DENY]).
 * [ToolPermission.DEFAULT] never appears here (that value is PROMPT-mode only — see
 * `PromptService.globalToolPermission`).
 */
interface ToolPermissionService {
    /** All configurable tools (excludes `ToolRegistry.STRUCTURAL_TOOLS`), grouped by category. */
    fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>>

    /**
     * Current global permission for one tool (by [ToolVd.id], i.e. `AgentTool.name`).
     * Read tool: [ToolPermission.DISABLED] if in `permanentlyDeniedTools`, else
     * [ToolPermission.ENABLED]. Write tool: [ToolPermission.ALLOW]/[ToolPermission.DENY] if in
     * the matching set, else [ToolPermission.ASK] (neutral "ask every time" default).
     */
    fun permissionFor(toolId: String): ToolPermission

    /**
     * Persist the full permission map (classic "Save" button). For each tool id: [ToolPermission.ALLOW]
     * adds it to `permanentlyAllowedTools`; [ToolPermission.DENY]/[ToolPermission.DISABLED] adds it
     * to `permanentlyDeniedTools`; [ToolPermission.ASK]/[ToolPermission.ENABLED] (or an id simply
     * absent from the map) means neither set — the neutral/default state.
     */
    fun save(permissions: Map<String, ToolPermission>)
}
