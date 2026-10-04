package net.bible.sharedcore.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Seam for the provider list/edit screen (`AiProvidersController`) and the easy-setup wizard.
 * Implemented in `:app` by `LlmProviderServiceImpl`, wrapping `LlmProviderConfigDao`,
 * `LlmProviderConfig.getApiKey()`/`setApiKey()`/`removeApiKey()`, the `LlmProvider` enum,
 * `DynamicModelService`, connection testing, and `GlobalAiSettings.aiDisclaimerAccepted`.
 */
interface LlmProviderService {
    val providers: StateFlow<List<ProviderVd>>
    fun providerTypes(): List<ProviderTypeVd>
    fun apiKeyFor(providerId: String): String
    suspend fun saveProvider(id: String?, typeId: String, displayName: String, apiKey: String, endpoint: String, apiFormatId: String)  // id null = create
    fun deleteProvider(id: String)
    suspend fun testConnection(typeId: String, endpoint: String, apiKey: String): Result<Unit>
    suspend fun fetchAvailableModels(providerId: String): List<AvailableModelVd>
    fun refresh()
    // recommended-setup data for the easy-setup wizard (Task 6/7):
    fun recommendedSetups(): List<RecommendedSetupVd>
    suspend fun performEasySetup(setupId: String, apiKey: String)
    fun disclaimerAccepted(): Boolean
    fun acceptDisclaimer()
}
