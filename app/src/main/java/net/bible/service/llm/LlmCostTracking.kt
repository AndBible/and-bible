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
        LlmProvider.findPricing(model) ?: getCustomPricing(providerConfigId)

    fun estimateCost(usage: LlmUsage, model: String, providerConfigId: IdType? = null): Double? {
        val p = getPricing(model, providerConfigId) ?: return null
        return (usage.inputTokens * p.inputPerMillion +
            usage.outputTokens * p.outputPerMillion +
            usage.cacheCreationTokens * p.cacheCreationPerMillion +
            usage.cacheReadTokens * p.cacheReadPerMillion) / 1_000_000.0
    }

    fun getCustomPricing(providerConfigId: IdType? = null): ModelPricing? {
        val prefix = if (providerConfigId != null) "llm_custom_price_${providerConfigId}_" else "llm_custom_"
        val input = CommonUtils.settings.getDouble("${prefix}input_price", 0.0)
        val output = CommonUtils.settings.getDouble("${prefix}output_price", 0.0)
        return if (input > 0 || output > 0) ModelPricing(input, output) else null
    }

    fun setCustomPricing(inputPerMillion: Double, outputPerMillion: Double, providerConfigId: IdType? = null) {
        val prefix = if (providerConfigId != null) "llm_custom_price_${providerConfigId}_" else "llm_custom_"
        CommonUtils.settings.setDouble("${prefix}input_price", inputPerMillion)
        CommonUtils.settings.setDouble("${prefix}output_price", outputPerMillion)
    }

    fun isKnownModel(model: String): Boolean = LlmProvider.hasKnownPricing(model)
}

/**
 * Cumulative cost tracker persisted in SharedPreferences, keyed by LlmProviderConfig.id.
 */
object LlmCostTracker {
    private fun keyPrefix(configId: IdType): String = "llm_cost_${configId}_"

    private val usageMutex = Mutex()

    suspend fun addUsage(usage: LlmUsage, model: String, providerConfigId: IdType) = usageMutex.withLock {
        val settings = CommonUtils.settings
        val prefix = keyPrefix(providerConfigId)
        settings.setLong("${prefix}input", settings.getLong("${prefix}input", 0) + usage.inputTokens)
        settings.setLong("${prefix}output", settings.getLong("${prefix}output", 0) + usage.outputTokens)
        settings.setLong("${prefix}cache_create", settings.getLong("${prefix}cache_create", 0) + usage.cacheCreationTokens)
        settings.setLong("${prefix}cache_read", settings.getLong("${prefix}cache_read", 0) + usage.cacheReadTokens)
        val cost = LlmPricing.estimateCost(usage, model, providerConfigId)
        if (cost != null) {
            settings.setDouble("${prefix}cost", settings.getDouble("${prefix}cost", 0.0) + cost)
        }
    }

    fun getCumulativeUsage(providerConfigId: IdType): LlmUsage {
        val settings = CommonUtils.settings
        val prefix = keyPrefix(providerConfigId)
        return LlmUsage(
            inputTokens = settings.getLong("${prefix}input", 0),
            outputTokens = settings.getLong("${prefix}output", 0),
            cacheCreationTokens = settings.getLong("${prefix}cache_create", 0),
            cacheReadTokens = settings.getLong("${prefix}cache_read", 0)
        )
    }

    fun getCumulativeCost(providerConfigId: IdType): Double {
        val settings = CommonUtils.settings
        return settings.getDouble("${keyPrefix(providerConfigId)}cost", 0.0)
    }

    fun reset(providerConfigId: IdType) {
        val settings = CommonUtils.settings
        val prefix = keyPrefix(providerConfigId)
        settings.setLong("${prefix}input", 0)
        settings.setLong("${prefix}output", 0)
        settings.setLong("${prefix}cache_create", 0)
        settings.setLong("${prefix}cache_read", 0)
        settings.setDouble("${prefix}cost", 0.0)
    }

    fun formatCost(cost: Double): String =
        if (cost < 0.01 && cost > 0) "< \$0.01" else "\$%.2f".format(cost)

    fun formatUsageSummary(usage: LlmUsage, model: String): String {
        val cost = LlmPricing.estimateCost(usage, model)
        val base = "${usage.inputTokens} in / ${usage.outputTokens} out"
        return if (cost != null) "$base (~${formatCost(cost)})" else base
    }
}
