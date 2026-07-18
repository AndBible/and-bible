package net.bible.sharedcore.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The 3 tabs of the PromptEdit screen, mirroring classic `PromptEditActivity`'s `TabLayout`. */
enum class PromptEditTab { PROMPT, PERMISSIONS, ADVANCED }

/**
 * Advanced-tab preference keys understood by [PromptEditController.setSwitch], mirroring classic
 * `AdvancedDataStore`'s boolean keys (`strict_context_matching` is NOT hidden by text
 * transformation; the other four are, see [PromptEditController.hiddenAdvancedKeys]).
 */
object PromptAdvancedSwitchKeys {
    const val STRICT_CONTEXT_MATCHING = "strict_context_matching"
    const val SPECIFY_BEFORE_RUN = "specify_before_run"
    const val NO_DOCUMENT_CREATION = "no_document_creation"
    const val AUTO_INCLUDE_DOCUMENTS = "auto_include_documents"
    const val AUTO_INCLUDE_COMMENTARIES = "auto_include_commentaries"
}

/**
 * Staging brain for the `PromptEdit` screen (create/edit an [AgentPrompt] via [PromptService]),
 * mirroring classic `PromptEditActivity`. Holds a single editable [PromptEditData] snapshot plus
 * the active [PromptEditTab], and tracks [isDirty] against the snapshot captured at load time.
 *
 * Two load modes, chosen by [promptId]:
 * - non-null: prefills from [PromptService.prompt] (edit an existing prompt). If the prompt no
 *   longer exists (e.g. deleted concurrently), falls back to a fresh [PromptService.newPromptData]
 *   rather than crashing.
 * - null: starts from [PromptService.newPromptData] with the optional [template] (pre-filled from
 *   the "customize this prompt" dialog) and [defaultContext] (pre-checked context checkbox,
 *   matching where the prompt editor was opened from).
 *
 * `bibleOnly` mirrors classic `updateBibleOnlyDependentState()`: turning it on immediately clears
 * [PromptContextIds.ordered]'s `WORKSPACE_MENU`/`NOTE_EDITOR` from `contexts` (applied on load too,
 * in case a prompt was saved with both bibleOnly and a stale workspace/note context), and
 * [toggleContext] refuses to (re)add either while `bibleOnly` is set. [disabledContexts] exposes
 * which context ids the screen should grey out.
 *
 * `isTextTransformation` mirrors classic `updateTextTransformationDependentState()`: the
 * Permissions tab is dropped from [availableTabs] (and the active [tab] is bounced back to
 * `PROMPT` if it was selected), and [hiddenAdvancedKeys] lists the Advanced-tab prefs classic
 * hides in that mode (`max_iterations` + the three auto-include/no-creation switches).
 *
 * Read-only prompts split into two cases, mirroring classic's SEPARATE `isBuiltIn`/`isReadOnly`
 * tracking:
 * - Genuinely read-only ADD-ON prompts (`isReadOnly && !isBuiltIn`): every `setX`/
 *   `resetToolPermissions` mutator is a no-op (checked via [PromptEditData.isReadOnly] on the live
 *   state, so it also covers a save() later making the prompt read-only, though that shouldn't
 *   happen in practice), [canSave] is false, and [save]/[delete] are no-ops — but
 *   [copyToCustomize] always works (that's the whole point: "Copy to customize").
 * - BUILT-IN prompts (`isReadOnly && isBuiltIn`): the prompt text/permissions/etc. stay locked
 *   (same mutator no-op), but [setModelOverride] is the one exception — it's still allowed, so a
 *   built-in prompt's model can be overridden without copying it. [canSave] becomes true once
 *   `modelOverrideId` differs from the value loaded, and [save] then persists ONLY that override
 *   via [PromptService.setBuiltinPromptModelOverride] (a distinct `BuiltinPromptOverride` row),
 *   not [PromptService.savePrompt] — no copy-to-customize involved.
 */
class PromptEditController(
    private val service: PromptService,
    promptId: String?,
    template: String? = null,
    defaultContext: String? = null,
) {
    private val loaded: PromptEditData =
        (promptId?.let { service.prompt(it) } ?: service.newPromptData(template, defaultContext))
            .let(::gateBibleOnly)

    /** Static tool catalog, for the category bulk-toggle ops below (E2/F37) -- like
     *  [GlobalToolPermissionsController.categories], read once and never re-fetched. */
    private val toolsByCategory: List<Pair<ToolCategoryVd, List<ToolVd>>> by lazy { service.toolsByCategory() }

    private var initialData: PromptEditData = loaded

    private val _state = MutableStateFlow(loaded)
    val state: StateFlow<PromptEditData> = _state.asStateFlow()

    private val _tab = MutableStateFlow(PromptEditTab.PROMPT)
    val tab: StateFlow<PromptEditTab> = _tab.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _canSave = MutableStateFlow(computeCanSave(loaded))
    val canSave: StateFlow<Boolean> = _canSave.asStateFlow()

    private val _availableTabs = MutableStateFlow(computeAvailableTabs(loaded))
    val availableTabs: StateFlow<List<PromptEditTab>> = _availableTabs.asStateFlow()

    /** `true` if this is a brand-new (unsaved) prompt, i.e. [promptId] was null (or missing). */
    val isNew: Boolean get() = state.value.id == null

    /** Context ids the screen should grey out (currently: bibleOnly disables workspace/note). */
    val disabledContexts: Set<String>
        get() = if (_state.value.bibleOnly) BIBLE_ONLY_DISABLED_CONTEXTS else emptySet()

    /** Advanced-tab preference keys the screen should hide while `isTextTransformation` is set. */
    val hiddenAdvancedKeys: Set<String>
        get() = if (_state.value.isTextTransformation) TEXT_TRANSFORMATION_HIDDEN_ADVANCED_KEYS else emptySet()

    fun setName(name: String) = update { it.copy(name = name) }
    fun setDescription(description: String) = update { it.copy(description = description) }
    fun setTemplate(template: String) = update { it.copy(template = template) }
    fun setCategory(categoryId: String?) = update { it.copy(categoryId = categoryId) }

    fun toggleContext(contextId: String) = update {
        if (it.bibleOnly && contextId in BIBLE_ONLY_DISABLED_CONTEXTS) return@update it
        it.copy(contexts = if (contextId in it.contexts) it.contexts - contextId else it.contexts + contextId)
    }

    fun setBibleOnly(bibleOnly: Boolean) = update {
        gateBibleOnly(it.copy(bibleOnly = bibleOnly))
    }

    fun setTextTransformation(isTextTransformation: Boolean) = update {
        it.copy(isTextTransformation = isTextTransformation)
    }

    fun setPermissionMode(mode: String?) = update { it.copy(permissionMode = mode) }

    fun setToolPermission(toolId: String, permission: ToolPermission) = update { applyToolPermission(it, toolId, permission) }

    /**
     * Pure per-tool permission application, extracted from [setToolPermission] so the category bulk
     * ops below ([setCategoryRead]/[setCategoryWrite]) can fold multiple tools into ONE [update]/
     * [publish] cycle instead of one per tool.
     */
    private fun applyToolPermission(data: PromptEditData, toolId: String, permission: ToolPermission): PromptEditData =
        when (permission) {
            ToolPermission.ALLOW, ToolPermission.ENABLED ->
                data.copy(allowedTools = data.allowedTools + toolId, deniedTools = data.deniedTools - toolId)
            ToolPermission.DENY, ToolPermission.DISABLED ->
                data.copy(deniedTools = data.deniedTools + toolId, allowedTools = data.allowedTools - toolId)
            // ASK is the GLOBAL-mode neutral option (Batch 9d); this controller is PROMPT-mode only
            // and never offers it as a selectable option (see ToolPermissionList's toolOptions), but
            // the shared enum needs an exhaustive branch here too — treat it like DEFAULT (no override).
            ToolPermission.DEFAULT, ToolPermission.ASK ->
                data.copy(allowedTools = data.allowedTools - toolId, deniedTools = data.deniedTools - toolId)
        }

    fun resetToolPermissions() = update { it.copy(allowedTools = emptySet(), deniedTools = emptySet()) }

    private fun toolsFor(categoryId: String): List<ToolVd> =
        toolsByCategory.firstOrNull { (category, _) -> category.id == categoryId }?.second ?: emptyList()

    private fun readToolsFor(categoryId: String): List<ToolVd> = toolsFor(categoryId).filterNot { it.requiresPermission }
    private fun writeToolsFor(categoryId: String): List<ToolVd> = toolsFor(categoryId).filter { it.requiresPermission }

    /**
     * Bulk read-tool toggle for the category header (E2/F35), mirroring classic
     * `ToolPermissionListBuilder.setAllRows` for `readRows` in PROMPT mode: ON sets every read tool
     * in the category to [ToolPermission.DEFAULT] (PROMPT mode's neutral/first option -- clears any
     * per-tool override back to the global default), OFF to [ToolPermission.DISABLED]. A no-op if
     * the category has no read tools, or the prompt is read-only (same guard as [setToolPermission]).
     */
    fun setCategoryRead(categoryId: String, enabled: Boolean) {
        val value = if (enabled) ToolPermission.DEFAULT else ToolPermission.DISABLED
        val toolIds = readToolsFor(categoryId).map { it.id }
        if (toolIds.isEmpty()) return
        update { data -> toolIds.fold(data) { acc, toolId -> applyToolPermission(acc, toolId, value) } }
    }

    /**
     * Bulk write-tool toggle for the category header (E2/F37), mirroring classic `setAllRows` for
     * `writeRows` in PROMPT mode: ON sets every write tool in the category to [ToolPermission.DEFAULT],
     * OFF to [ToolPermission.DENY]. A no-op if the category has no write tools, or the prompt is
     * read-only.
     */
    fun setCategoryWrite(categoryId: String, enabled: Boolean) {
        val value = if (enabled) ToolPermission.DEFAULT else ToolPermission.DENY
        val toolIds = writeToolsFor(categoryId).map { it.id }
        if (toolIds.isEmpty()) return
        update { data -> toolIds.fold(data) { acc, toolId -> applyToolPermission(acc, toolId, value) } }
    }

    /** Aggregate state of the category's read-tool bulk toggle (`null` = no read tools, hide it). */
    fun categoryReadState(categoryId: String): CategoryToggleState? =
        categoryToggleState(readToolsFor(categoryId)) { toolId -> toolPermissionFor(toolId) }

    /** Aggregate state of the category's write-tool bulk toggle (`null` = no write tools, hide it). */
    fun categoryWriteState(categoryId: String): CategoryToggleState? =
        categoryToggleState(writeToolsFor(categoryId)) { toolId -> toolPermissionFor(toolId) }

    /** Mirrors `PromptEditScreen`'s own private `toolPermissionFor` -- kept here too so the aggregate
     *  queries above don't need a `ToolVd` (only the id + set membership). */
    private fun toolPermissionFor(toolId: String): ToolPermission = when (toolId) {
        in _state.value.allowedTools -> ToolPermission.ALLOW
        in _state.value.deniedTools -> ToolPermission.DENY
        else -> ToolPermission.DEFAULT
    }

    /**
     * Allowed even for a built-in (read-only) prompt — that's the whole point of the built-in
     * model-override save path (see class doc); genuinely read-only add-on prompts stay blocked.
     */
    fun setModelOverride(modelId: String?) =
        update(allowBuiltinOverride = true) { it.copy(modelOverrideId = modelId) }
    fun setMaxIterations(maxIterations: Int?) = update { it.copy(maxIterations = maxIterations) }

    fun setSwitch(key: String, value: Boolean) = update {
        when (key) {
            PromptAdvancedSwitchKeys.STRICT_CONTEXT_MATCHING -> it.copy(strictContextMatching = value)
            PromptAdvancedSwitchKeys.SPECIFY_BEFORE_RUN -> it.copy(specifyBeforeRun = value)
            PromptAdvancedSwitchKeys.NO_DOCUMENT_CREATION -> it.copy(noDocumentCreation = value)
            PromptAdvancedSwitchKeys.AUTO_INCLUDE_DOCUMENTS -> it.copy(autoIncludeDocuments = value)
            PromptAdvancedSwitchKeys.AUTO_INCLUDE_COMMENTARIES -> it.copy(autoIncludeCommentaries = value)
            else -> it
        }
    }

    fun selectTab(tab: PromptEditTab) {
        if (tab in _availableTabs.value) _tab.value = tab
    }

    /**
     * Persists the current snapshot and returns the saved id, or null if [canSave] is false.
     * For a built-in prompt this only persists the model override (via
     * [PromptService.setBuiltinPromptModelOverride]), never [PromptService.savePrompt].
     */
    fun save(): String? {
        if (!_canSave.value) return null
        val current = _state.value
        return if (current.isBuiltIn && current.isReadOnly) {
            val id = current.id ?: return null
            service.setBuiltinPromptModelOverride(id, current.modelOverrideId)
            rebaseline(current)
            id
        } else {
            val id = service.savePrompt(current)
            val saved = current.copy(id = id)
            _state.value = saved
            rebaseline(saved)
            id
        }
    }

    /** Re-baselines dirty/canSave tracking against [data] as the new "loaded" snapshot. */
    private fun rebaseline(data: PromptEditData) {
        initialData = data
        _isDirty.value = false
        _canSave.value = computeCanSave(data)
    }

    /** No-op for a new (unsaved) or read-only prompt. */
    fun delete() {
        val id = _state.value.id ?: return
        if (_state.value.isReadOnly) return
        service.deletePromptById(id)
    }

    /** Works even for a read-only prompt — that's the point of "Copy to customize". */
    fun copyToCustomize(): String? {
        val id = _state.value.id ?: return null
        return service.copyPrompt(id)
    }

    /**
     * @param allowBuiltinOverride if true, bypasses the read-only guard for a built-in prompt
     *   (`isReadOnly && isBuiltIn`) — used only by [setModelOverride]. A genuinely read-only
     *   add-on prompt (`isReadOnly && !isBuiltIn`) is still blocked either way.
     */
    private fun update(allowBuiltinOverride: Boolean = false, transform: (PromptEditData) -> PromptEditData) {
        val current = _state.value
        if (current.isReadOnly && !(allowBuiltinOverride && current.isBuiltIn)) return
        publish(transform(current))
    }

    private fun publish(next: PromptEditData) {
        _state.value = next
        _isDirty.value = next != initialData
        _canSave.value = computeCanSave(next)
        val tabs = computeAvailableTabs(next)
        _availableTabs.value = tabs
        if (_tab.value !in tabs) _tab.value = PromptEditTab.PROMPT
    }

    private fun gateBibleOnly(data: PromptEditData): PromptEditData =
        if (data.bibleOnly) data.copy(contexts = data.contexts - BIBLE_ONLY_DISABLED_CONTEXTS) else data

    /**
     * A genuinely read-only add-on prompt (`isReadOnly && !isBuiltIn`) never has a Save action.
     * A built-in prompt (`isReadOnly && isBuiltIn`) shows Save only once its model override
     * actually differs from what was loaded — that's the only thing a built-in's Save persists.
     * An editable (non-read-only) prompt additionally requires a non-blank template, mirroring
     * classic `PromptEditActivity`'s `prompt_template_required` guard — Save must not persist a
     * blank template.
     */
    private fun computeCanSave(data: PromptEditData): Boolean =
        data.name.isNotBlank() &&
            ((!data.isReadOnly && data.template.isNotBlank()) ||
                (data.isBuiltIn && data.modelOverrideId != initialData.modelOverrideId))

    private fun computeAvailableTabs(data: PromptEditData): List<PromptEditTab> =
        if (data.isTextTransformation) {
            listOf(PromptEditTab.PROMPT, PromptEditTab.ADVANCED)
        } else {
            listOf(PromptEditTab.PROMPT, PromptEditTab.PERMISSIONS, PromptEditTab.ADVANCED)
        }

    companion object {
        val BIBLE_ONLY_DISABLED_CONTEXTS: Set<String> = setOf("WORKSPACE_MENU", "NOTE_EDITOR")
        val TEXT_TRANSFORMATION_HIDDEN_ADVANCED_KEYS: Set<String> = setOf(
            "max_iterations",
            PromptAdvancedSwitchKeys.NO_DOCUMENT_CREATION,
            PromptAdvancedSwitchKeys.AUTO_INCLUDE_DOCUMENTS,
            PromptAdvancedSwitchKeys.AUTO_INCLUDE_COMMENTARIES,
        )
    }
}
