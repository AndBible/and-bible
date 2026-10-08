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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.common.AiSettings
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.ApiFormat
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProcessingService
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.getApiKey
import net.bible.service.llm.removeApiKey
import net.bible.service.llm.setApiKey
import net.bible.sharedcore.ai.AvailableModelVd
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.ProviderTypeVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedcore.ai.RecommendedSetupVd
import net.bible.service.db.blockingDb

/**
 * Android impl of [LlmProviderService] backing [net.bible.sharedcore.ai.AiProvidersController] and
 * the easy-setup wizard. Wraps the classic provider domain:
 *
 * - [LlmProviderConfigDao] for the configured-provider rows, flattened to portable [ProviderVd]s.
 *   `endpoint`/`apiFormatId` are ALWAYS populated from `resolveEndpoint()`/`resolveApiFormat()`
 *   (the shared screen only shows them for custom providers, but the VD carries them regardless).
 * - the SharedPreferences-backed API key via [getApiKey]/[setApiKey]/[removeApiKey].
 * - the [LlmProvider] enum → [ProviderTypeVd] for the add-provider type picker.
 * - [DynamicModelService] for available-model discovery, [LlmProcessingService.testApiConnection]
 *   for the easy-setup connection test, and the classic `EasySetupDialogs` recommended-setup list +
 *   `performEasySetup` create-flow (ported here so the wizard UI in Task 9 stays presentation-only).
 * - [CommonUtils.aiSettings] `aiDisclaimerAccepted` for the disclaimer gate + `defaultModelId`.
 *
 * Save/delete/easy-setup mirror the classic `AiProvidersFragment.saveProviderConfig`/
 * `confirmDeleteProvider` and `EasySetupDialogs.performEasySetup` exactly (custom-only endpoint/
 * api-format, dynamic-model prefetch, default-model reassignment on delete). Every mutation calls
 * [AiSettings.notifyConfigChanged] (create/delete/easy-setup, matching classic) and re-emits [providers]; the
 * [AiSettings.configChanged] subscription also re-emits when an external change fires it (same
 * pattern as [AiSettingsServiceImpl]). Registered as a Koin single (lives for the process).
 *
 * The DAOs are `suspend`; the non-suspend reads/[deleteProvider]
 * bridge them with `blockingDb` and block the caller thread (as elsewhere in the AI settings layer); the suspend
 * mutations still hop to [Dispatchers.IO] for the network-touching prefetch.
 */
class LlmProviderServiceImpl : LlmProviderService {
    private val settings get() = CommonUtils.aiSettings
    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    private val _providers = MutableStateFlow(buildProviders())
    override val providers: StateFlow<List<ProviderVd>> = _providers.asStateFlow()

    init {
        // Process lifetime, like the bus registration it replaces: never cancelled.
        AiSettings.configChanged.subscribeOnMain { refresh() }
    }

    private fun buildProviders(): List<ProviderVd> = blockingDb { dao.all() }.map { config ->
        ProviderVd(
            id = config.id.toString(),
            displayName = config.displayName,
            providerTypeId = config.providerType,
            apiKeySet = config.getApiKey().isNotBlank(),
            isCustom = config.resolveProvider() == LlmProvider.CUSTOM,
            endpoint = config.resolveEndpoint(),          // always populated (UI gates on isCustom)
            apiFormatId = config.resolveApiFormat().name,  // always populated
        )
    }

    override fun providerTypes(): List<ProviderTypeVd> = LlmProvider.entries.map { p ->
        ProviderTypeVd(
            id = p.name,
            displayName = if (p == LlmProvider.CUSTOM) application.getString(R.string.llm_provider_custom) else p.displayName,
            tier = p.tier.name,
            apiKeyUrl = p.apiKeyUrl,
            defaultEndpoint = p.endpoint,
            supportsDynamicModels = p.supportsDynamicModels,
            isCustom = p == LlmProvider.CUSTOM,
        )
    }

    override fun apiKeyFor(providerId: String): String =
        blockingDb { dao.getById(IdType(providerId)) }?.getApiKey() ?: ""

    override suspend fun saveProvider(
        id: String?,
        typeId: String,
        displayName: String,
        apiKey: String,
        endpoint: String,
        apiFormatId: String,
    ) {
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
                newConfig.setApiKey(key)
                prefetchModels(provider, key)
            } else {
                val config = dao.getById(IdType(id)) ?: return@withContext
                val updated = config.copy(
                    displayName = name,
                    endpoint = if (isCustom) endpointTrimmed else config.endpoint,
                    apiFormat = if (isCustom) parseApiFormat(apiFormatId) else config.apiFormat,
                )
                dao.update(updated)
                updated.setApiKey(key)
            }
        }
        // Classic broadcast the config change only on create (now [AiSettings.notifyConfigChanged]); edits just refresh the list locally.
        if (id == null) AiSettings.notifyConfigChanged()
        refresh()
    }

    override fun deleteProvider(id: String) {
        val config = blockingDb { dao.getById(IdType(id)) } ?: return
        config.removeApiKey()
        val deletedModelIds = blockingDb {
            modelDao.getByProvider(config.id).map { it.id }.toSet().also { dao.delete(config) }
        }
        val currentDefault = settings.defaultModelId
        if (currentDefault != null && currentDefault in deletedModelIds) {
            settings.defaultModelId = blockingDb { modelDao.all() }.firstOrNull()?.id
        }
        AiSettings.notifyConfigChanged()
        refresh()
    }

    override suspend fun testConnection(typeId: String, endpoint: String, apiKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val provider = resolveProviderType(typeId)
                // The classic easy-setup test picks the setup's model; the seam only carries the
                // type, so use the provider's first representative model to validate the key/endpoint.
                val modelId = provider.models.firstOrNull() ?: ""
                LlmProcessingService.testApiConnection(provider, modelId, apiKey.trim())
            }
        }

    override suspend fun fetchAvailableModels(providerId: String): List<AvailableModelVd> {
        val config = dao.getById(IdType(providerId)) ?: return emptyList()
        val provider = config.resolveProvider()
        withContext(Dispatchers.IO) {
            if (provider.supportsDynamicModels) {
                val fetchKey = if (provider.modelsEndpointPublic) "" else config.getApiKey()
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

    override fun refresh() {
        _providers.value = buildProviders()
    }

    // --- Easy-setup wizard (ported from classic EasySetupDialogs) ---------------------------------

    override fun recommendedSetups(): List<RecommendedSetupVd> = listOf(
        recommendedSetup(
            LlmProvider.GEMINI, "gemini-3-flash-preview",
            application.getString(R.string.easy_setup_gemini_desc),
            application.getString(R.string.easy_setup_free_tier),
        ),
        recommendedSetup(
            LlmProvider.ANTHROPIC, "claude-haiku-4-5",
            application.getString(R.string.easy_setup_anthropic_desc),
            null,
        ),
        recommendedSetup(
            LlmProvider.OPENAI, "gpt-5.4-mini",
            application.getString(R.string.easy_setup_openai_desc),
            null,
        ),
    )

    override suspend fun performEasySetup(setupId: String, apiKey: String) {
        val setup = recommendedSetups().firstOrNull { it.id == setupId } ?: return
        val provider = resolveProviderType(setup.providerTypeId)
        val key = apiKey.trim()
        withContext(Dispatchers.IO) {
            val providerConfig = LlmProviderConfig(
                providerType = provider.name,
                displayName = provider.displayName,
                orderNumber = dao.getCount(),
            )
            dao.insert(providerConfig)
            providerConfig.setApiKey(key)

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
        AiSettings.notifyConfigChanged()
        refresh()
        withContext(Dispatchers.IO) { prefetchModels(provider, key) }
    }

    override fun disclaimerAccepted(): Boolean = settings.aiDisclaimerAccepted

    override fun acceptDisclaimer() {
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
