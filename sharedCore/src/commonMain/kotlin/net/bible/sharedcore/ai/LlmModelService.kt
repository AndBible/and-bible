package net.bible.sharedcore.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Seam for the model list/edit screen (`AiModelsController`). Implemented in `:app` by
 * `LlmModelServiceImpl`, wrapping `LlmConfiguredModelDao`, `LlmCostTracker` pricing/cost
 * formatting, `GlobalAiSettings.defaultModelId` + `DefaultModelChangedEvent`, and
 * `DynamicModelService` for the available-models picker.
 */
interface LlmModelService {
    val models: StateFlow<List<ModelVd>>
    fun providersForPicker(): List<ProviderVd>
    suspend fun availableModelsFor(providerId: String): List<AvailableModelVd>
    suspend fun saveModel(id: String?, providerId: String, modelId: String, priceInput: String?, priceOutput: String?, setDefault: Boolean)  // id null = create
    fun deleteModel(id: String)
    fun setDefault(id: String)
    fun refresh()
}
