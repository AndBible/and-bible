/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

    fun getPricing(model: String): ModelPricing? =
        LlmProvider.findPricing(model) ?: getCustomPricing()

    fun estimateCost(usage: LlmUsage, model: String): Double? {
        val p = getPricing(model) ?: return null
        return (usage.inputTokens * p.inputPerMillion +
            usage.outputTokens * p.outputPerMillion +
            usage.cacheCreationTokens * p.cacheCreationPerMillion +
            usage.cacheReadTokens * p.cacheReadPerMillion) / 1_000_000.0
    }

    fun getCustomPricing(): ModelPricing? {
        val input = CommonUtils.settings.getDouble("llm_custom_input_price", 0.0)
        val output = CommonUtils.settings.getDouble("llm_custom_output_price", 0.0)
        return if (input > 0 || output > 0) ModelPricing(input, output) else null
    }

    fun setCustomPricing(inputPerMillion: Double, outputPerMillion: Double) {
        CommonUtils.settings.setDouble("llm_custom_input_price", inputPerMillion)
        CommonUtils.settings.setDouble("llm_custom_output_price", outputPerMillion)
    }

    fun isKnownModel(model: String): Boolean = LlmProvider.hasKnownPricing(model)
}

/**
 * Cumulative cost tracker persisted in SharedPreferences.
 */
object LlmCostTracker {
    private const val KEY_INPUT_TOKENS = "llm_cumulative_input_tokens"
    private const val KEY_OUTPUT_TOKENS = "llm_cumulative_output_tokens"
    private const val KEY_CACHE_CREATION_TOKENS = "llm_cumulative_cache_creation_tokens"
    private const val KEY_CACHE_READ_TOKENS = "llm_cumulative_cache_read_tokens"
    private const val KEY_CUMULATIVE_COST = "llm_cumulative_cost"
    private const val KEY_TRACKED_PROVIDER = "llm_tracked_provider"

    fun addUsage(usage: LlmUsage, model: String) {
        val settings = CommonUtils.settings

        // Auto-reset if provider changed
        val currentProvider = settings.llmProvider
        val trackedProvider = settings.getString(KEY_TRACKED_PROVIDER, "") ?: ""
        if (trackedProvider.isNotBlank() && trackedProvider != currentProvider) {
            reset()
        }
        settings.setString(KEY_TRACKED_PROVIDER, currentProvider)

        settings.setLong(KEY_INPUT_TOKENS, settings.getLong(KEY_INPUT_TOKENS, 0) + usage.inputTokens)
        settings.setLong(KEY_OUTPUT_TOKENS, settings.getLong(KEY_OUTPUT_TOKENS, 0) + usage.outputTokens)
        settings.setLong(KEY_CACHE_CREATION_TOKENS, settings.getLong(KEY_CACHE_CREATION_TOKENS, 0) + usage.cacheCreationTokens)
        settings.setLong(KEY_CACHE_READ_TOKENS, settings.getLong(KEY_CACHE_READ_TOKENS, 0) + usage.cacheReadTokens)

        val cost = LlmPricing.estimateCost(usage, model)
        if (cost != null) {
            settings.setDouble(KEY_CUMULATIVE_COST, settings.getDouble(KEY_CUMULATIVE_COST, 0.0) + cost)
        }
    }

    fun getCumulativeUsage(): LlmUsage = LlmUsage(
        inputTokens = CommonUtils.settings.getLong(KEY_INPUT_TOKENS, 0),
        outputTokens = CommonUtils.settings.getLong(KEY_OUTPUT_TOKENS, 0),
        cacheCreationTokens = CommonUtils.settings.getLong(KEY_CACHE_CREATION_TOKENS, 0),
        cacheReadTokens = CommonUtils.settings.getLong(KEY_CACHE_READ_TOKENS, 0)
    )

    fun getCumulativeCost(): Double = CommonUtils.settings.getDouble(KEY_CUMULATIVE_COST, 0.0)

    fun reset() {
        val settings = CommonUtils.settings
        settings.setLong(KEY_INPUT_TOKENS, 0)
        settings.setLong(KEY_OUTPUT_TOKENS, 0)
        settings.setLong(KEY_CACHE_CREATION_TOKENS, 0)
        settings.setLong(KEY_CACHE_READ_TOKENS, 0)
        settings.setDouble(KEY_CUMULATIVE_COST, 0.0)
        settings.setString(KEY_TRACKED_PROVIDER, "")
    }

    fun formatCost(cost: Double): String =
        if (cost < 0.01 && cost > 0) "< \$0.01" else "\$%.2f".format(cost)

    fun formatUsageSummary(usage: LlmUsage, model: String): String {
        val cost = LlmPricing.estimateCost(usage, model)
        val base = "${usage.inputTokens} in / ${usage.outputTokens} out"
        return if (cost != null) "$base (~${formatCost(cost)})" else base
    }
}
