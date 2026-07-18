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
 * Read-only (built-in or add-on) prompts: every `setX`/`resetToolPermissions` mutator becomes a
 * no-op (checked via [PromptEditData.isReadOnly] on the live state, so it also covers a save()
 * later making the prompt read-only, though that shouldn't happen in practice), [canSave] is
 * false, and [save]/[delete] are no-ops — but [copyToCustomize] always works (that's the whole
 * point of a read-only prompt: "Copy to customize").
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
    val isNew: Boolean = state.value.id == null

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

    fun setToolPermission(toolId: String, permission: ToolPermission) = update {
        when (permission) {
            ToolPermission.ALLOW, ToolPermission.ENABLED ->
                it.copy(allowedTools = it.allowedTools + toolId, deniedTools = it.deniedTools - toolId)
            ToolPermission.DENY, ToolPermission.DISABLED ->
                it.copy(deniedTools = it.deniedTools + toolId, allowedTools = it.allowedTools - toolId)
            ToolPermission.DEFAULT ->
                it.copy(allowedTools = it.allowedTools - toolId, deniedTools = it.deniedTools - toolId)
        }
    }

    fun resetToolPermissions() = update { it.copy(allowedTools = emptySet(), deniedTools = emptySet()) }

    fun setModelOverride(modelId: String?) = update { it.copy(modelOverrideId = modelId) }
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

    /** Persists the current snapshot and returns the saved id, or null if [canSave] is false. */
    fun save(): String? {
        if (!_canSave.value) return null
        val id = service.savePrompt(_state.value)
        val saved = _state.value.copy(id = id)
        _state.value = saved
        initialData = saved
        _isDirty.value = false
        return id
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

    private fun update(transform: (PromptEditData) -> PromptEditData) {
        val current = _state.value
        if (current.isReadOnly) return
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

    private fun computeCanSave(data: PromptEditData): Boolean = data.name.isNotBlank() && !data.isReadOnly

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
