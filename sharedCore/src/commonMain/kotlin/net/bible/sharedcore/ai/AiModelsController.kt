package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Edit-dialog state for the add-model flow, mirroring classic `showAddModelDialog`
 * (`ModelDialogs.kt`): pick a provider, then pick a model from that provider's
 * available-models list (or the synthetic [AiModelsController.CUSTOM_MODEL_ID] choice for a
 * free-text model id). Custom-model text input is shown only when [isCustom]; editable pricing
 * ([priceInput]/[priceOutput]) is shown when [isCustom] OR the picked model has unknown pricing
 * (`AvailableModelVd.knownPricing == false`) — for a known-pricing model, pricing is resolved
 * server-side and no editable fields are shown. [showUnsupported] is a display filter only (the
 * host decides whether to hide `!AvailableModelVd.supported` entries from [availableModels]); it
 * does not affect [canSave].
 *
 * [id] is null for the add flow (the default, going through [Step.PICK_PROVIDER] /
 * [Step.PICK_MODEL]) and non-null when editing an existing model
 * ([AiModelsController.startEdit], which prefills the form and jumps straight to
 * [Step.PICK_MODEL] — the same step add uses for its editable form once a model is chosen —
 * skipping both pick steps, symmetric to `AiProvidersController.startEdit`). While editing an
 * existing model, [showPriceFields] is frozen at the value [AiModelsController.startEdit] computed
 * from [ModelVd.priceInput]/[ModelVd.priceOutput] being non-null (it does NOT get recomputed
 * against [availableModels], which is irrelevant/empty in the edit flow).
 */
data class ModelEditState(
    val id: String?,               // null = new (add flow); non-null = editing this existing model
    val step: Step,
    val providerChoices: List<ProviderVd>,
    val providerId: String,
    val availableModels: List<AvailableModelVd>,
    val loadingModels: Boolean,
    val modelId: String,           // selected model id, or CUSTOM_MODEL_ID
    val customModelId: String,
    val priceInput: String,
    val priceOutput: String,
    val setAsDefault: Boolean,
    val showUnsupported: Boolean,
    val isCustom: Boolean,         // derived: modelId == CUSTOM_MODEL_ID
    val showPriceFields: Boolean,  // derived
    val canSave: Boolean,          // derived
) {
    enum class Step { PICK_PROVIDER, PICK_MODEL }
}

/**
 * Staging brain for the models list/add screen. Backed directly by [LlmModelService.models]
 * re-sorted default-first (mirroring classic `refreshModelList`'s
 * `sortedByDescending { it.id == defaultModelId }`), plus a dialog stack for the add-model flow:
 * pick a configured provider, load that provider's available models
 * ([LlmModelService.availableModelsFor], suspend), pick one (or enter a custom model id +
 * pricing), Save persists via [LlmModelService.saveModel] and dismisses the dialog. A model is
 * always saved as default when no currently-configured model is marked default
 * (`models.value.none { it.isDefault }` — mirroring classic's
 * `if (settings.defaultModelId == null) settings.defaultModelId = newModel.id`, but derived
 * directly from [ModelVd.isDefault] rather than an empty list, so it also covers "every remaining
 * model lost its default" cases independent of how that came about), regardless of the
 * [ModelEditState.setAsDefault] checkbox.
 */
class AiModelsController(
    private val service: LlmModelService,
    private val scope: CoroutineScope,
) {
    enum class Field { CUSTOM_MODEL_ID, PRICE_INPUT, PRICE_OUTPUT }

    private val _models = MutableStateFlow(sortDefaultFirst(service.models.value))
    val models: StateFlow<List<ModelVd>> = _models.asStateFlow()

    private val _dialog = MutableStateFlow<ModelEditState?>(null)
    val dialog: StateFlow<ModelEditState?> = _dialog.asStateFlow()

    init {
        scope.launch { service.models.collect { _models.value = sortDefaultFirst(it) } }
    }

    fun startAdd() {
        _dialog.value = ModelEditState(
            id = null,
            step = ModelEditState.Step.PICK_PROVIDER,
            providerChoices = service.providersForPicker(),
            providerId = "",
            availableModels = emptyList(),
            loadingModels = false,
            modelId = "",
            customModelId = "",
            priceInput = "",
            priceOutput = "",
            setAsDefault = false,
            showUnsupported = false,
            isCustom = false,
            showPriceFields = false,
            canSave = false,
        )
    }

    /**
     * Opens the edit dialog scoped to an existing model, prefilled from its [ModelVd] — provider
     * fixed, model id shown read-only, jumping straight to [ModelEditState.Step.PICK_MODEL] (the
     * same step add's editable form uses) and skipping both pick steps, symmetric to
     * [AiProvidersController.startEdit]. Editable price fields are shown/prefilled exactly when
     * [ModelVd.priceInput]/[ModelVd.priceOutput] are non-null (i.e. pricing is unknown/custom) —
     * see [ModelEditState] doc.
     */
    fun startEdit(id: String) {
        val m = models.value.firstOrNull { it.id == id } ?: return
        publish(
            ModelEditState(
                id = m.id,
                step = ModelEditState.Step.PICK_MODEL,
                providerChoices = emptyList(),
                providerId = m.providerId,
                availableModels = emptyList(),
                loadingModels = false,
                modelId = m.modelId,
                customModelId = "",
                priceInput = m.priceInput ?: "",
                priceOutput = m.priceOutput ?: "",
                setAsDefault = m.isDefault,
                showUnsupported = false,
                isCustom = false,
                showPriceFields = m.priceInput != null || m.priceOutput != null,
                canSave = false,
            )
        )
    }

    fun pickProvider(providerId: String) {
        val current = _dialog.value ?: return
        publish(
            current.copy(
                providerId = providerId,
                step = ModelEditState.Step.PICK_MODEL,
                loadingModels = true,
                availableModels = emptyList(),
                modelId = "",
            )
        )
        scope.launch {
            val available = service.availableModelsFor(providerId)
            val cur = _dialog.value ?: return@launch
            publish(cur.copy(availableModels = available, loadingModels = false))
        }
    }

    fun pickModel(modelId: String) {
        val current = _dialog.value ?: return
        publish(current.copy(modelId = modelId))
    }

    fun updateField(field: Field, value: String) {
        val current = _dialog.value ?: return
        publish(
            when (field) {
                Field.CUSTOM_MODEL_ID -> current.copy(customModelId = value)
                Field.PRICE_INPUT -> current.copy(priceInput = value)
                Field.PRICE_OUTPUT -> current.copy(priceOutput = value)
            }
        )
    }

    fun setAsDefault(value: Boolean) {
        val current = _dialog.value ?: return
        publish(current.copy(setAsDefault = value))
    }

    fun setShowUnsupported(value: Boolean) {
        val current = _dialog.value ?: return
        publish(current.copy(showUnsupported = value))
    }

    fun save() {
        val s = _dialog.value ?: return
        if (!s.canSave) return
        val modelId = if (s.isCustom) s.customModelId.trim() else s.modelId
        val setDefault = s.setAsDefault || models.value.none { it.isDefault }
        scope.launch {
            service.saveModel(
                id = s.id,
                providerId = s.providerId,
                modelId = modelId,
                priceInput = if (s.showPriceFields) s.priceInput else null,
                priceOutput = if (s.showPriceFields) s.priceOutput else null,
                setDefault = setDefault,
            )
            _dialog.value = null
        }
    }

    fun delete(id: String) = service.deleteModel(id)

    fun setDefault(id: String) = service.setDefault(id)

    fun dismissDialog() { _dialog.value = null }

    private fun publish(next: ModelEditState) {
        val isCustom = next.modelId == CUSTOM_MODEL_ID
        // Editing an existing model never goes through pickProvider/pickModel (availableModels
        // stays empty, so an availableModels-based lookup can't tell known from unknown pricing);
        // showPriceFields was fixed once in startEdit from ModelVd.priceInput/priceOutput, so keep it.
        val showPriceFields = if (next.id != null) {
            next.showPriceFields
        } else {
            val selected = if (isCustom) null else next.availableModels.firstOrNull { it.modelId == next.modelId }
            val hasUnknownPricing = !isCustom && next.modelId.isNotBlank() && selected?.knownPricing != true
            isCustom || hasUnknownPricing
        }
        val modelChosen = if (isCustom) next.customModelId.isNotBlank() else next.modelId.isNotBlank()
        val canSave = next.step == ModelEditState.Step.PICK_MODEL && next.providerId.isNotBlank() && modelChosen
        _dialog.value = next.copy(isCustom = isCustom, showPriceFields = showPriceFields, canSave = canSave)
    }

    private fun sortDefaultFirst(list: List<ModelVd>): List<ModelVd> = list.sortedByDescending { it.isDefault }

    companion object {
        /** Sentinel model id for the free-text "Custom…" choice in the model picker. */
        const val CUSTOM_MODEL_ID = "Custom…"
    }
}
