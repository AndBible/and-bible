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

import net.bible.android.database.IdType

fun LlmProviderConfig.resolveAdapter(): LlmApiAdapter = when (resolveApiFormat()) {
    ApiFormat.OPENAI -> {
        val provider = resolveProvider()
        val cacheControl = provider != LlmProvider.CUSTOM && provider.supportsCacheControl
        OpenAiApiAdapter(supportsCacheControl = cacheControl)
    }
    ApiFormat.ANTHROPIC -> AnthropicApiAdapter()
}

/** All available models for this provider (enum + dynamic). Used for "add model" picker. */
fun LlmProviderConfig.resolveAvailableModels(): List<String> {
    val provider = resolveProvider()
    val dynamic = DynamicModelService.getCachedModels(provider.name)
    return dynamic?.map { it.id } ?: provider.models
}

/** Create a configured model with pricing auto-filled from known sources. */
fun LlmConfiguredModel.Companion.create(
    providerConfigId: IdType,
    modelId: String,
    orderNumber: Int = 0,
): LlmConfiguredModel {
    val pricing = LlmProvider.findPricing(modelId)
        ?: DynamicModelService.getPricingForModel(modelId)
    return LlmConfiguredModel(
        providerConfigId = providerConfigId,
        modelId = modelId,
        orderNumber = orderNumber,
        inputPricePerMillion = pricing?.inputPerMillion ?: 0.0,
        outputPricePerMillion = pricing?.outputPerMillion ?: 0.0,
        cacheCreationPricePerMillion = pricing?.cacheCreationPerMillion ?: 0.0,
        cacheReadPricePerMillion = pricing?.cacheReadPerMillion ?: 0.0,
    )
}
