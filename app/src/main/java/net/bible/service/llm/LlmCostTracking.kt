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
 *
 * Resolution order:
 * 1. Configured model pricing (from [LlmConfiguredModel] in database)
 * 2. Built-in enum pricing ([LlmProvider.findPricing])
 * 3. Dynamic model service pricing ([DynamicModelService.getPricingForModel])
 */
object LlmPricing {

    fun getPricing(model: String, configuredModelId: IdType? = null): ModelPricing? {
        if (configuredModelId != null) {
            getConfiguredModelPricing(configuredModelId)?.let { return it }
        }
        return LlmProvider.findPricing(model)
            ?: DynamicModelService.getPricingForModel(model)
    }

    fun estimateCost(usage: LlmUsage, model: String, configuredModelId: IdType? = null): Double? {
        val p = getPricing(model, configuredModelId) ?: return null
        return (usage.inputTokens * p.inputPerMillion +
            usage.outputTokens * p.outputPerMillion +
            usage.cacheCreationTokens * p.cacheCreationPerMillion +
            usage.cacheReadTokens * p.cacheReadPerMillion) / 1_000_000.0
    }

    private fun getConfiguredModelPricing(configuredModelId: IdType): ModelPricing? {
        val m = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao().getById(configuredModelId)
            ?: return null
        return if (m.inputPricePerMillion > 0 || m.outputPricePerMillion > 0) {
            ModelPricing(m.inputPricePerMillion, m.outputPricePerMillion, m.cacheCreationPricePerMillion, m.cacheReadPricePerMillion)
        } else null
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

    suspend fun addUsage(usage: LlmUsage, model: String, configuredModelId: IdType) = usageMutex.withLock {
        val existing = dao.get(configuredModelId, deviceId)
        val cost = LlmPricing.estimateCost(usage, model, configuredModelId) ?: 0.0
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
                configuredModelId = configuredModelId,
                deviceId = deviceId,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cacheCreationTokens = usage.cacheCreationTokens,
                cacheReadTokens = usage.cacheReadTokens,
                estimatedCostUsd = cost,
            ))
        }
    }

    private fun List<LlmUsageRecord>.sumUsage(): LlmUsage =
        fold(LlmUsage()) { acc, r -> acc + LlmUsage(r.inputTokens, r.outputTokens, r.cacheCreationTokens, r.cacheReadTokens) }

    fun getCumulativeUsage(configuredModelId: IdType): LlmUsage =
        dao.getByModel(configuredModelId).sumUsage()

    fun getCumulativeCost(configuredModelId: IdType): Double =
        dao.getByModel(configuredModelId).sumOf { it.estimatedCostUsd }

    fun getTotalUsage(): LlmUsage =
        dao.all().sumUsage()

    fun getTotalCost(): Double =
        dao.all().sumOf { it.estimatedCostUsd }

    fun reset(configuredModelId: IdType) {
        dao.deleteByModel(configuredModelId)
    }

    fun formatCost(cost: Double): String =
        if (cost < 0.01 && cost > 0) "\$%.3f".format(cost) else "\$%.2f".format(cost)

    /** Format a per-million-token price compactly, e.g. "$3.00", "< $0.01". */
    fun formatPriceCompact(pricePerMillion: Double): String =
        if (pricePerMillion < 0.01 && pricePerMillion > 0) "< \$0.01"
        else "\$%.2f".format(pricePerMillion)

    fun formatTokenCount(count: Long): String = when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fk".format(count / 1_000.0)
        else -> count.toString()
    }

    fun formatUsageSummary(usage: LlmUsage, model: String, configuredModelId: IdType? = null): String {
        val cost = LlmPricing.estimateCost(usage, model, configuredModelId)
        val base = "${usage.inputTokens} in / ${usage.outputTokens} out"
        return if (cost != null) "$base (~${formatCost(cost)})" else base
    }
}
