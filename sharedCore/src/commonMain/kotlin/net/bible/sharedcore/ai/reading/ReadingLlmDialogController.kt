package net.bible.sharedcore.ai.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State machine driving the 4 reading-view LLM dialogs (prompt selector, specify-before-run,
 * model selection, regenerate-confirm). Mirrors classic `LlmDialogHelper`.
 *
 * The [ReadingLlmDialog] variants themselves carry no callback fields (they're plain data), so
 * this controller stashes the host-captured `onExecute`/`onRegenerate` lambdas plus the
 * in-flight prompt/spec/pageId/instructions as private fields, cleared on [dismiss].
 */
class ReadingLlmDialogController(
    private val service: ReadingLlmService,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ReadingLlmDialogState())
    val state: StateFlow<ReadingLlmDialogState> = _state.asStateFlow()

    // Stashed context for the in-flight prompt-selector flow.
    private var contextId: String? = null
    private var docCategoryId: String? = null
    private var onExecute: ((promptId: String, userSpecification: String?, modelOverrideId: String?) -> Unit)? = null

    // Stashed context for the in-flight regenerate flow.
    private var onRegenerate: ((pageId: String, instructions: String?, keepPrevious: Boolean, freshRun: Boolean, modelOverrideId: String?) -> Unit)? = null

    // Pending selection shared by both flows, disambiguated by forRegenerate.
    private var pendingPromptId: String? = null
    private var pendingUserSpec: String? = null
    private var pendingPageId: String? = null
    private var pendingInstructions: String? = null
    private var pendingKeepPrevious: Boolean = false
    private var pendingFreshRun: Boolean = false
    private var forRegenerate: Boolean = false

    fun openPromptSelector(
        contextId: String,
        docCategoryId: String?,
        onExecute: (promptId: String, userSpecification: String?, modelOverrideId: String?) -> Unit,
    ) {
        this.contextId = contextId
        this.docCategoryId = docCategoryId
        this.onExecute = onExecute
        scope.launch {
            val groups = service.promptGroupsFor(contextId, docCategoryId)
            if (groups.all { it.prompts.isEmpty() }) return@launch
            _state.value = ReadingLlmDialogState(ReadingLlmDialog.PromptSelector(groups))
        }
    }

    fun openRegenerate(
        pageId: String,
        onRegenerate: (pageId: String, instructions: String?, keepPrevious: Boolean, freshRun: Boolean, modelOverrideId: String?) -> Unit,
    ) {
        pendingPageId = pageId
        this.onRegenerate = onRegenerate
        _state.value = ReadingLlmDialogState(ReadingLlmDialog.Regenerate(pageId))
    }

    fun onPromptChosen(promptId: String) {
        val groups = (_state.value.dialog as? ReadingLlmDialog.PromptSelector)?.groups ?: return
        val chosen = groups.flatMap { it.prompts }.firstOrNull { it.id == promptId } ?: return
        if (chosen.specifyBeforeRun) {
            pendingPromptId = promptId
            _state.value = ReadingLlmDialogState(ReadingLlmDialog.SpecifyBeforeRun(promptId, chosen.name))
        } else {
            maybeAskModel(promptId, userSpec = null)
        }
    }

    fun onSpecifySubmitted(text: String) {
        if (text.isBlank()) return
        val promptId = pendingPromptId ?: return
        maybeAskModel(promptId, userSpec = text)
    }

    private fun maybeAskModel(promptId: String, userSpec: String?) {
        scope.launch {
            if (service.promptRequiresModelChoice(promptId)) {
                pendingPromptId = promptId
                pendingUserSpec = userSpec
                forRegenerate = false
                _state.value = ReadingLlmDialogState(ReadingLlmDialog.ModelSelection(service.configuredModels(), allowSetDefault = true))
            } else {
                onExecute?.invoke(promptId, userSpec, null)
                dismiss()
            }
        }
    }

    fun onModelChosen(modelId: String, setAsDefault: Boolean) {
        scope.launch {
            if (!forRegenerate && setAsDefault) {
                pendingPromptId?.let { service.setPromptModelDefault(it, modelId) }
            }
            if (forRegenerate) {
                onRegenerate?.invoke(pendingPageId!!, pendingInstructions, pendingKeepPrevious, pendingFreshRun, modelId)
            } else {
                onExecute?.invoke(pendingPromptId!!, pendingUserSpec, modelId)
            }
            dismiss()
        }
    }

    fun onRegenerateConfirmed(instructions: String?, keepPrevious: Boolean, freshRun: Boolean) {
        pendingInstructions = instructions
        pendingKeepPrevious = keepPrevious
        pendingFreshRun = freshRun
        scope.launch {
            val pageId = pendingPageId ?: return@launch
            if (service.regenerateRequiresModelChoice(pageId)) {
                forRegenerate = true
                _state.value = ReadingLlmDialogState(ReadingLlmDialog.ModelSelection(service.configuredModels(), allowSetDefault = false))
            } else {
                onRegenerate?.invoke(pageId, instructions, keepPrevious, freshRun, null)
                dismiss()
            }
        }
    }

    fun onToggleFavorite(promptId: String) {
        scope.launch {
            service.toggleFavorite(promptId)
            val ctx = contextId ?: return@launch
            val groups = service.promptGroupsFor(ctx, docCategoryId)
            _state.value = ReadingLlmDialogState(ReadingLlmDialog.PromptSelector(groups))
        }
    }

    fun onCategoryExpandedChanged(categoryId: String?, expanded: Boolean) {
        val ctx = contextId ?: return
        service.setCategoryCollapsed(ctx, categoryId, !expanded)
        val current = _state.value.dialog as? ReadingLlmDialog.PromptSelector ?: return
        val updatedGroups = current.groups.map { group ->
            if (group.categoryId == categoryId) group.copy(collapsed = !expanded) else group
        }
        _state.value = ReadingLlmDialogState(ReadingLlmDialog.PromptSelector(updatedGroups))
    }

    fun dismiss() {
        _state.value = ReadingLlmDialogState()
        contextId = null
        docCategoryId = null
        onExecute = null
        onRegenerate = null
        pendingPromptId = null
        pendingUserSpec = null
        pendingPageId = null
        pendingInstructions = null
        pendingKeepPrevious = false
        pendingFreshRun = false
        forRegenerate = false
    }
}
