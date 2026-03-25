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

package net.bible.service.llm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer

/**
 * Token usage from a single LLM API call.
 */
data class LlmUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0
) {
    val totalTokens: Long get() = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens

    operator fun plus(other: LlmUsage) = LlmUsage(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        cacheCreationTokens = cacheCreationTokens + other.cacheCreationTokens,
        cacheReadTokens = cacheReadTokens + other.cacheReadTokens
    )
}

/**
 * Pricing information for LLM models (dollars per million tokens).
 */
object LlmPricing {

    fun getPricing(model: String, providerConfigId: IdType? = null): ModelPricing? =
        LlmProvider.findPricing(model)
            ?: DynamicModelService.getPricingForModel(model)
            ?: getCustomPricing(providerConfigId)

    fun estimateCost(usage: LlmUsage, model: String, providerConfigId: IdType? = null): Double? {
        val p = getPricing(model, providerConfigId) ?: return null
        return (usage.inputTokens * p.inputPerMillion +
            usage.outputTokens * p.outputPerMillion +
            usage.cacheCreationTokens * p.cacheCreationPerMillion +
            usage.cacheReadTokens * p.cacheReadPerMillion) / 1_000_000.0
    }

    fun getCustomPricing(providerConfigId: IdType? = null): ModelPricing? {
        if (providerConfigId == null) return null
        val config = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao().getById(providerConfigId) ?: return null
        val input = config.customInputPrice
        val output = config.customOutputPrice
        return if (input > 0 || output > 0) ModelPricing(input, output) else null
    }

    fun setCustomPricing(inputPerMillion: Double, outputPerMillion: Double, providerConfigId: IdType) {
        val dao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val config = dao.getById(providerConfigId) ?: return
        dao.update(config.copy(customInputPrice = inputPerMillion, customOutputPrice = outputPerMillion))
    }

    fun isKnownModel(model: String): Boolean =
        LlmProvider.hasKnownPricing(model) || DynamicModelService.getPricingForModel(model) != null
}

/**
 * Cumulative cost tracker persisted in AiSettingsDatabase as per-device [LlmUsageRecord] rows.
 * Each device writes only its own row; the UI sums across all devices for totals.
 */
object LlmCostTracker {
    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmUsageRecordDao()
    private val deviceId get() = CommonUtils.deviceIdentifier

    private val usageMutex = Mutex()

    suspend fun addUsage(usage: LlmUsage, model: String, providerConfigId: IdType) = usageMutex.withLock {
        val existing = dao.get(providerConfigId, deviceId)
        val cost = LlmPricing.estimateCost(usage, model, providerConfigId) ?: 0.0
        if (existing != null) {
            dao.upsert(existing.copy(
                inputTokens = existing.inputTokens + usage.inputTokens,
                outputTokens = existing.outputTokens + usage.outputTokens,
                cacheCreationTokens = existing.cacheCreationTokens + usage.cacheCreationTokens,
                cacheReadTokens = existing.cacheReadTokens + usage.cacheReadTokens,
                estimatedCostUsd = existing.estimatedCostUsd + cost,
            ))
        } else {
            dao.upsert(LlmUsageRecord(
                providerConfigId = providerConfigId,
                deviceId = deviceId,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cacheCreationTokens = usage.cacheCreationTokens,
                cacheReadTokens = usage.cacheReadTokens,
                estimatedCostUsd = cost,
            ))
        }
    }

    fun getCumulativeUsage(providerConfigId: IdType): LlmUsage {
        val records = dao.getByConfig(providerConfigId)
        return records.fold(LlmUsage()) { acc, r ->
            LlmUsage(
                inputTokens = acc.inputTokens + r.inputTokens,
                outputTokens = acc.outputTokens + r.outputTokens,
                cacheCreationTokens = acc.cacheCreationTokens + r.cacheCreationTokens,
                cacheReadTokens = acc.cacheReadTokens + r.cacheReadTokens,
            )
        }
    }

    fun getCumulativeCost(providerConfigId: IdType): Double =
        dao.getByConfig(providerConfigId).sumOf { it.estimatedCostUsd }

    fun reset(providerConfigId: IdType) {
        dao.deleteByConfig(providerConfigId)
    }

    fun formatCost(cost: Double): String =
        if (cost < 0.01 && cost > 0) "< \$0.01" else "\$%.2f".format(cost)

    fun formatUsageSummary(usage: LlmUsage, model: String): String {
        val cost = LlmPricing.estimateCost(usage, model)
        val base = "${usage.inputTokens} in / ${usage.outputTokens} out"
        return if (cost != null) "$base (~${formatCost(cost)})" else base
    }
}
