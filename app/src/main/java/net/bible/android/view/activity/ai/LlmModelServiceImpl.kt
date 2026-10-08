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
import net.bible.android.database.IdType
import net.bible.service.common.AiSettings
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.DynamicModelService
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmPricing
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.getApiKey
import net.bible.sharedcore.ai.AvailableModelVd
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.ModelVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.service.db.blockingDb

/**
 * Android impl of [LlmModelService] backing [net.bible.sharedcore.ai.AiModelsController] and the
 * shared [net.bible.sharedui.ai.AiModelsScreen]. Wraps the classic model-management domain:
 *
 * - [net.bible.service.llm.LlmConfiguredModelDao] for the configured-model rows, flattened to
 *   portable [ModelVd]s. The pre-formatted [ModelVd.pricingSummary] mirrors classic
 *   `AiModelsFragment.refreshModelList` exactly ("<provider> — <modelId> ([in]/[out])" + an
 *   optional cumulative-cost second line), built from [LlmCostTracker.formatPriceCompact]/
 *   `formatCost`/`getCumulativeCost`. Raw [ModelVd.priceInput]/[ModelVd.priceOutput] are populated
 *   ONLY for models with unknown/editable pricing (`!LlmProvider.hasKnownPricing`, mirroring
 *   [AvailableModelVd.knownPricing]) so the edit dialog can prefill its editable price fields.
 * - `GlobalAiSettings.defaultModelId` (`CommonUtils.aiSettings`) + [AiSettings.defaultModelChanged] for
 *   the per-model default flag and the tap-to-set-default action.
 * - [DynamicModelService] + [net.bible.service.llm.LlmProviderConfig.resolveAvailableModels] for
 *   the add-model provider picker (identical to `LlmProviderServiceImpl.fetchAvailableModels`).
 *
 * Save/delete/set-default mirror classic `ModelDialogs.kt`
 * (`showAddModelDialog`/`showEditModelDialog`/`confirmDeleteModel`) with one deliberate hardening:
 * **a default always exists whenever the list is non-empty** — see [applyDefault]. Classic's edit
 * dialog set `defaultModelId = null` when you unchecked "set default" on the current default
 * (leaving the list default-less); the T3 review flagged this, so [applyDefault] reassigns to
 * another model instead. Every mutation calls [AiSettings.notifyConfigChanged] and re-emits [models]; the
 * [AiSettings.configChanged] subscription also re-emits when an external change fires it, as does
 * [AiSettings.defaultModelChanged] (same pattern as [AiSettingsServiceImpl]/[LlmProviderServiceImpl]).
 * Registered as a Koin single (lives for the process).
 *
 * The DAOs are `suspend`; the non-suspend seam methods
 * ([providersForPicker], [deleteModel], [setDefault]) bridge them with one `blockingDb` per method (as
 * elsewhere in the AI settings layer); the suspend paths ([saveModel], [availableModelsFor]) still
 * hop to [Dispatchers.IO] for the network-touching prefetch/insert.
 */
class LlmModelServiceImpl : LlmModelService {
    private val settings get() = CommonUtils.aiSettings
    private val providerDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    private val _models = MutableStateFlow(buildModels())
    override val models: StateFlow<List<ModelVd>> = _models.asStateFlow()

    init {
        // Process lifetime, like the bus registration it replaces: never cancelled.
        AiSettings.configChanged.subscribeOnMain { refresh() }
        // Process lifetime: never cancelled.
        AiSettings.defaultModelChanged.subscribeOnMain { refresh() }
    }

    private fun buildModels(): List<ModelVd> {
        val defaultId = settings.defaultModelId
        val (providers, models) = blockingDb { providerDao.all() to modelDao.all() }
        val providerNames = providers.associate { it.id to it.displayName }
        return models.map { m ->
            // Raw editable prices exposed only when pricing is unknown/custom (mirrors
            // AvailableModelVd.knownPricing / the ModelVd contract). Known-pricing models carry
            // null so the edit dialog shows the read-only pricingSummary instead.
            val editablePricing = !LlmProvider.hasKnownPricing(m.modelId)
            ModelVd(
                id = m.id.toString(),
                modelId = m.modelId,
                displayName = m.displayName,
                providerId = m.providerConfigId.toString(),
                isDefault = m.id == defaultId,
                supported = LlmProvider.isModelSupported(m.modelId),
                pricingSummary = pricingSummary(m, providerNames[m.providerConfigId] ?: "?"),
                priceInput = if (editablePricing) m.inputPricePerMillion.toString() else null,
                priceOutput = if (editablePricing) m.outputPricePerMillion.toString() else null,
            )
        }
    }

    /** Mirrors classic `AiModelsFragment.refreshModelList`'s row summary verbatim. */
    private fun pricingSummary(model: LlmConfiguredModel, providerName: String): String = buildString {
        append(providerName)
        append(" — ")
        append(model.modelId)
        if (model.inputPricePerMillion > 0 || model.outputPricePerMillion > 0) {
            append(" (")
            append(LlmCostTracker.formatPriceCompact(model.inputPricePerMillion))
            append("/")
            append(LlmCostTracker.formatPriceCompact(model.outputPricePerMillion))
            append(")")
        }
        val cost = LlmCostTracker.getCumulativeCost(model.id)
        if (cost > 0) {
            append("\n")
            append(LlmCostTracker.formatCost(cost))
        }
    }

    override fun providersForPicker(): List<ProviderVd> = blockingDb { providerDao.all() }.map { config ->
        ProviderVd(
            id = config.id.toString(),
            displayName = config.displayName,
            providerTypeId = config.providerType,
            apiKeySet = config.getApiKey().isNotBlank(),
            isCustom = config.resolveProvider() == LlmProvider.CUSTOM,
            endpoint = config.resolveEndpoint(),
            apiFormatId = config.resolveApiFormat().name,
        )
    }

    override suspend fun availableModelsFor(providerId: String): List<AvailableModelVd> {
        val config = providerDao.getById(IdType(providerId)) ?: return emptyList()
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

    override suspend fun saveModel(
        id: String?,
        providerId: String,
        modelId: String,
        priceInput: String?,
        priceOutput: String?,
        setDefault: Boolean,
    ) {
        val trimmedModelId = modelId.trim()
        if (trimmedModelId.isBlank()) return
        withContext(Dispatchers.IO) {
            if (id == null) {
                // Create — mirrors classic showAddModelDialog's OK handler. Auto-price a known
                // model via LlmConfiguredModel.create (enum + dynamic pricing), else store the
                // user-entered per-million prices.
                val providerConfigId = IdType(providerId)
                val orderNumber = modelDao.getByProvider(providerConfigId).size
                val newModel = if (LlmPricing.isKnownModel(trimmedModelId)) {
                    LlmConfiguredModel.create(providerConfigId, trimmedModelId, orderNumber)
                } else {
                    LlmConfiguredModel(
                        providerConfigId = providerConfigId,
                        modelId = trimmedModelId,
                        orderNumber = orderNumber,
                        inputPricePerMillion = priceInput?.toDoubleOrNull() ?: 0.0,
                        outputPricePerMillion = priceOutput?.toDoubleOrNull() ?: 0.0,
                    )
                }
                modelDao.insert(newModel)
                applyDefault(newModel.id, setDefault)
            } else {
                // Edit — mirrors classic showEditModelDialog's OK handler (model id is fixed; only
                // custom/unknown-pricing models have editable prices).
                val existing = modelDao.getById(IdType(id)) ?: return@withContext
                val updated = if (LlmPricing.isKnownModel(existing.modelId)) {
                    existing
                } else {
                    existing.copy(
                        inputPricePerMillion = priceInput?.toDoubleOrNull() ?: 0.0,
                        outputPricePerMillion = priceOutput?.toDoubleOrNull() ?: 0.0,
                    )
                }
                modelDao.update(updated)
                applyDefault(existing.id, setDefault)
            }
        }
        AiSettings.notifyConfigChanged()
        refresh()
    }

    override fun deleteModel(id: String) {
        val model = blockingDb {
            modelDao.getById(IdType(id))?.also { modelDao.delete(it) }
        } ?: return
        // Classic confirmDeleteModel: if the deleted model was default, reassign to the first
        // remaining model (null only when the list is now empty — a legitimately default-less state).
        if (settings.defaultModelId == model.id) {
            settings.defaultModelId = blockingDb { modelDao.all() }.firstOrNull()?.id
        }
        AiSettings.notifyConfigChanged()
        refresh()
    }

    override fun setDefault(id: String) {
        settings.defaultModelId = IdType(id)
        refresh()
    }

    /**
     * Persist the default-model choice for a just-saved model, enforcing the "a default always
     * exists (when the list is non-empty)" invariant (T3-review carry-forward):
     *
     * - [setDefault] true → this model becomes default.
     * - unchecked ON THE CURRENT DEFAULT → reassign to another existing model rather than clearing
     *   it (classic left `defaultModelId = null` here — the bug this fixes); if it is the only
     *   model, it stays default.
     * - no default configured at all → this model becomes default (classic's
     *   `if (defaultModelId == null) defaultModelId = newModel.id` first-model-becomes-default).
     * - otherwise (unchecked on a non-default model) → leave the existing default untouched.
     *
     * The `defaultModelId` setter emits [AiSettings.defaultModelChanged] itself.
     */
    private suspend fun applyDefault(modelId: IdType, setDefault: Boolean) {
        when {
            setDefault -> settings.defaultModelId = modelId
            settings.defaultModelId == modelId ->
                settings.defaultModelId = modelDao.all().firstOrNull { it.id != modelId }?.id ?: modelId
            settings.defaultModelId == null -> settings.defaultModelId = modelId
        }
    }

    override fun refresh() {
        _models.value = buildModels()
    }
}
