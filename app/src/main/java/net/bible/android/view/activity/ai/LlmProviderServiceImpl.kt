/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.ai

import kotlinx.coroutines.sync.withLock
import net.bible.service.llm.collectOwned
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.AppCoroutineScope
import org.koin.core.component.inject
import org.koin.core.component.KoinComponent
import net.bible.service.llm.LlmStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.service.llm.AiSettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.apiKey
import net.bible.service.llm.SecretStore
import net.bible.service.llm.removeApiKey
import net.bible.service.llm.setApiKey
import net.bible.sharedcore.ai.AvailableModelVd
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.ProviderTypeVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedcore.ai.RecommendedSetupVd

/**
 * Android impl of [LlmProviderService] backing [net.bible.sharedcore.ai.AiProvidersController] and
 * the easy-setup wizard. Wraps the classic provider domain:
 *
 * - [LlmProviderConfigDao] for the configured-provider rows, flattened to portable [ProviderVd]s.
 *   `endpoint`/`apiFormatId` are ALWAYS populated from `resolveEndpoint()`/`resolveApiFormat()`
 *   (the shared screen only shows them for custom providers, but the VD carries them regardless).
 * - the SharedPreferences-backed API key via [apiKey]/[setApiKey]/[removeApiKey].
 * - the [LlmProvider] enum → [ProviderTypeVd] for the add-provider type picker.
 * - [DynamicModelService] for available-model discovery, [LlmProcessingService.testApiConnection]
 *   for the easy-setup connection test, and the classic `EasySetupDialogs` recommended-setup list +
 *   `performEasySetup` create-flow (ported here so the wizard UI in Task 9 stays presentation-only).
 * - [AiSettings] `aiDisclaimerAccepted` for the disclaimer gate + `defaultModelId`.
 *
 * Save/delete/easy-setup mirror the classic `AiProvidersFragment.saveProviderConfig`/
 * `confirmDeleteProvider` and `EasySetupDialogs.performEasySetup` exactly (custom-only endpoint/
 * api-format, dynamic-model prefetch, default-model reassignment on delete). Every mutation calls
 * [AiSettings.notifyConfigChanged] (create/delete/easy-setup, matching classic) and re-emits [providers]; the
 * [AiSettings.configChanged] subscription also re-emits when an external change fires it (same
 * pattern as [AiSettingsServiceImpl]). Registered as a Koin single (lives for the process).
 *
 * DAO reads suspend; mutations await application-owned write-then-refresh sequences.
 * Network-touching prefetch still runs on [Dispatchers.IO].
 */
class LlmProviderServiceImpl(private val strings: LlmStrings) : LlmProviderService, KoinComponent {
    private val settings: AiSettings by inject()
    private val appScope: AppCoroutineScope by inject()
    private val secrets: SecretStore by inject()
    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    private val _providers = MutableStateFlow<List<ProviderVd>>(emptyList())
    override val providers: StateFlow<List<ProviderVd>> = _providers.asStateFlow()

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    init {
        appScope.launch { settings.ensureLoaded(); refresh() }
        // The application job owns subscriptions as well as refresh work.
        settings.configChanged.collectOwned(appScope) { refresh() }
    }

    private suspend fun buildProviders(): List<ProviderVd> = dao.all().map { config ->
        ProviderVd(
            id = config.id.toString(),
            displayName = config.displayName,
            providerTypeId = config.providerType,
            apiKeySet = config.apiKey(secrets).isNotBlank(),
            isCustom = config.resolveProvider() == LlmProvider.CUSTOM,
            endpoint = config.resolveEndpoint(),          // always populated (UI gates on isCustom)
            apiFormatId = config.resolveApiFormat().name,  // always populated
        )
    }

    override fun providerTypes(): List<ProviderTypeVd> = LlmProvider.entries.map { p ->
        ProviderTypeVd(
            id = p.name,
            displayName = if (p == LlmProvider.CUSTOM) strings.llmProviderCustom else p.displayName,
            tier = p.tier.name,
            apiKeyUrl = p.apiKeyUrl,
            defaultEndpoint = p.endpoint,
            supportsDynamicModels = p.supportsDynamicModels,
            isCustom = p == LlmProvider.CUSTOM,
        )
    }

    override suspend fun apiKeyFor(providerId: String): String =
        dao.getById(IdType(providerId))?.apiKey(secrets) ?: ""

    override suspend fun saveProvider(
        id: String?,
        typeId: String,
        displayName: String,
        apiKey: String,
        endpoint: String,
        apiFormatId: String,
    ) = kotlinx.coroutines.withContext(appScope.coroutineContext) {
        val provider = resolveProviderType(typeId)
        val isCustom = provider == LlmProvider.CUSTOM
        val name = displayName.trim().ifEmpty { provider.displayName }
        val key = apiKey.trim()
        val endpointTrimmed = endpoint.trim()

        withContext(Dispatchers.IO) {
            if (id == null) {
                val newConfig = LlmProviderConfig(
                    providerType = provider.name,
                    displayName = name,
                    endpoint = if (isCustom) endpointTrimmed else null,
                    apiFormat = if (isCustom) parseApiFormat(apiFormatId) else null,
                    orderNumber = dao.getCount(),
                )
                dao.insert(newConfig)
                newConfig.setApiKey(secrets, key)
                prefetchModels(provider, key)
            } else {
                val config = dao.getById(IdType(id)) ?: return@withContext
                val updated = config.copy(
                    displayName = name,
                    endpoint = if (isCustom) endpointTrimmed else config.endpoint,
                    apiFormat = if (isCustom) parseApiFormat(apiFormatId) else config.apiFormat,
                )
                dao.update(updated)
                updated.setApiKey(secrets, key)
            }
        }
        // Classic broadcast the config change only on create (now [AiSettings.notifyConfigChanged]); edits just refresh the list locally.
        if (id == null) settings.notifyConfigChanged()
        refresh()
    }

    override suspend fun deleteProvider(id: String) = kotlinx.coroutines.withContext(appScope.coroutineContext) {
        val config = dao.getById(IdType(id)) ?: return@withContext
        config.removeApiKey(secrets)
        val deletedModelIds = run {
            modelDao.getByProvider(config.id).map { it.id }.toSet().also { dao.delete(config) }
        }
        val currentDefault = settings.defaultModelId
        if (currentDefault != null && currentDefault in deletedModelIds) {
            settings.defaultModelId = modelDao.all().firstOrNull()?.id
        }
        settings.awaitWrites()
        settings.notifyConfigChanged()
        refresh()
    }

    override suspend fun testConnection(typeId: String, endpoint: String, apiKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val provider = resolveProviderType(typeId)
                // The classic easy-setup test picks the setup's model; the seam only carries the
                // type, so use the provider's first representative model to validate the key/endpoint.
                val modelId = provider.models.firstOrNull() ?: ""
                LlmProcessingService.testApiConnection(provider, modelId, apiKey.trim())
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fetchAvailableModels(providerId: String): List<AvailableModelVd> {
        val config = dao.getById(IdType(providerId)) ?: return emptyList()
        val provider = config.resolveProvider()
        withContext(Dispatchers.IO) {
            if (provider.supportsDynamicModels) {
                val fetchKey = if (provider.modelsEndpointPublic) "" else config.apiKey(secrets)
                DynamicModelService.fetchModels(provider.endpoint, fetchKey, provider.name)
            }
        }
        return config.resolveAvailableModels().map { modelId ->
            AvailableModelVd(
                modelId = modelId,
                label = modelId,
                supported = LlmProvider.isModelSupported(modelId),
                knownPricing = LlmProvider.hasKnownPricing(modelId),
            )
        }
    }

    override suspend fun refresh() = refreshMutex.withLock {
        _providers.value = buildProviders()
    }

    // --- Easy-setup wizard (ported from classic EasySetupDialogs) ---------------------------------

    override fun recommendedSetups(): List<RecommendedSetupVd> = listOf(
        recommendedSetup(
            LlmProvider.GEMINI, "gemini-3-flash-preview",
            strings.easySetupGeminiDesc,
            strings.easySetupFreeTier,
        ),
        recommendedSetup(
            LlmProvider.ANTHROPIC, "claude-haiku-4-5",
            strings.easySetupAnthropicDesc,
            null,
        ),
        recommendedSetup(
            LlmProvider.OPENAI, "gpt-5.4-mini",
            strings.easySetupOpenaiDesc,
            null,
        ),
    )

    override suspend fun performEasySetup(setupId: String, apiKey: String) = kotlinx.coroutines.withContext(appScope.coroutineContext) {
        val setup = recommendedSetups().firstOrNull { it.id == setupId } ?: return@withContext
        val provider = resolveProviderType(setup.providerTypeId)
        val key = apiKey.trim()
        withContext(Dispatchers.IO) {
            val providerConfig = LlmProviderConfig(
                providerType = provider.name,
                displayName = provider.displayName,
                orderNumber = dao.getCount(),
            )
            dao.insert(providerConfig)
            providerConfig.setApiKey(secrets, key)

            val configuredModel = LlmConfiguredModel.create(
                providerConfigId = providerConfig.id,
                modelId = setup.modelId,
            )
            modelDao.insert(configuredModel)
            settings.defaultModelId = configuredModel.id
        }
        // The writes above are the commit; broadcast it BEFORE prefetchModels below (whole-branch
        // review I3). prefetchModels is a cache warm-up (see its own kdoc: "best-effort"), not part
        // of the commit -- hoisted out of the withContext(Dispatchers.IO) block above so a
        // cancellation during its network fetch can no longer swallow this notify/refresh() and
        // leave the DB configured while every consumer outside the AI cluster never hears about it.
        settings.awaitWrites()
        settings.notifyConfigChanged()
        refresh()
        withContext(Dispatchers.IO) { prefetchModels(provider, key) }
    }

    override fun disclaimerAccepted(): Boolean = settings.aiDisclaimerAccepted

    override suspend fun acceptDisclaimer() {
        settings.aiDisclaimerAccepted = true
        refresh()
    }

    // --- helpers ----------------------------------------------------------------------------------

    /** One recommended-setup entry: label mirrors classic step-1 items ("Name — desc (badge)"). */
    private fun recommendedSetup(
        provider: LlmProvider,
        modelId: String,
        description: String,
        badge: String?,
    ): RecommendedSetupVd {
        val base = "${provider.displayName} — $description"
        return RecommendedSetupVd(
            id = provider.name,
            label = if (badge != null) "$base ($badge)" else base,
            providerTypeId = provider.name,
            modelId = modelId,
            apiKeyUrl = provider.apiKeyUrl,
        )
    }

    /** Refresh the dynamic model cache after adding a provider (classic parity, best-effort). */
    private suspend fun prefetchModels(provider: LlmProvider, apiKey: String) {
        if (!provider.supportsDynamicModels) return
        val fetchKey = if (provider.modelsEndpointPublic) "" else apiKey
        DynamicModelService.fetchModels(provider.endpoint, fetchKey, provider.name)
    }

    private fun resolveProviderType(typeId: String): LlmProvider =
        try { LlmProvider.valueOf(typeId) } catch (_: IllegalArgumentException) { LlmProvider.CUSTOM }

    /** Custom-provider default is OPENAI (matches `LlmProviderConfig.resolveApiFormat()`'s fallback
     * and the classic add-provider spinner default). */
    private fun parseApiFormat(id: String): ApiFormat =
        try { ApiFormat.valueOf(id) } catch (_: IllegalArgumentException) { ApiFormat.OPENAI }
}
