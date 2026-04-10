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
import net.bible.service.common.AiSettings
import net.bible.service.db.DatabaseContainer

enum class ApiFormat { OPENAI, ANTHROPIC }

enum class ProviderTier { RECOMMENDED, COMMUNITY, UNCATEGORIZED }

/** Pricing per million tokens (USD). */
data class ModelPricing(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
    val cacheCreationPerMillion: Double = inputPerMillion,
    val cacheReadPerMillion: Double = inputPerMillion * 0.1,
    /** Supported models can send AI bug reports. */
    val supported: Boolean = false,
)

private fun p(input: Double, output: Double, cacheCreate: Double = input, cacheRead: Double = input * 0.1, supported: Boolean = false) =
    ModelPricing(input, output, cacheCreate, cacheRead, supported)

enum class LlmProvider(
    val displayName: String,
    val endpoint: String,
    val modelPricing: List<Pair<String, ModelPricing?>>,

    val apiFormat: ApiFormat = ApiFormat.OPENAI,
    val tier: ProviderTier = ProviderTier.RECOMMENDED,
    val apiKeyUrl: String? = null,
    /** Whether this provider supports dynamic model list fetching via GET /v1/models. */
    val supportsDynamicModels: Boolean = true,
    /** Whether the /models endpoint works without an API key. */
    val modelsEndpointPublic: Boolean = false,
    /** Whether this provider supports explicit cache_control breakpoints in requests. */
    val supportsCacheControl: Boolean = false,
) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", listOf(
        "gemini-3-flash-preview" to p(0.50, 3.00, supported = true),
        "gemini-3.1-pro-preview" to p(2.00, 12.00, 2.00, 0.20, supported = true),
        "gemini-2.5-pro" to p(1.25, 10.00, 1.25, 0.3125, supported = true),
        "gemini-2.5-flash" to p(0.15, 0.60, 0.15, 0.0375, supported = true),
    ), apiKeyUrl = "https://aistudio.google.com/apikey"),
    OPENAI("OpenAI (ChatGPT)", "https://api.openai.com/v1", listOf(
        "gpt-5.4-mini" to p(0.75, 4.50, 0.75, 0.075, supported = true),
        "gpt-5.4" to p(2.50, 15.00, 2.50, 0.25, supported = true),
        "gpt-5-mini" to p(0.40, 1.60, 0.40, 0.10),
        "gpt-5-nano" to p(0.10, 0.40, 0.10, 0.025),
        "gpt-5.2" to p(2.00, 8.00, 2.00, 0.50),
        "gpt-4o-mini" to p(0.15, 0.60, 0.15, 0.075),
    ), apiKeyUrl = "https://platform.openai.com/api-keys"),
    ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1", listOf(
        "claude-haiku-4-5" to p(0.80, 4.00, 1.00, 0.08, supported = true),
        "claude-sonnet-4-6" to p(3.00, 15.00, 3.75, 0.30, supported = true),
        "claude-opus-4-6" to p(15.00, 75.00, 18.75, 1.50, supported = true),
    ), apiFormat = ApiFormat.ANTHROPIC, apiKeyUrl = "https://console.anthropic.com/settings/keys", supportsDynamicModels = false),
    XAI("xAI (Grok)", "https://api.x.ai/v1", listOf(
        "grok-4-0709" to p(3.00, 15.00),
        "grok-4-1-fast-reasoning" to p(3.00, 15.00),
        "grok-3-mini" to p(0.30, 0.50),
    ), tier = ProviderTier.COMMUNITY, apiKeyUrl = "https://console.x.ai/"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", listOf(
        "mistral-small-latest" to p(0.10, 0.30),
        "mistral-large-latest" to p(2.00, 6.00),
    ), tier = ProviderTier.COMMUNITY, apiKeyUrl = "https://console.mistral.ai/api-keys"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", listOf(
        "deepseek-chat" to p(0.27, 1.10, 0.27, 0.07),
        "deepseek-reasoner" to p(0.55, 2.19, 0.55, 0.14),
    ), tier = ProviderTier.COMMUNITY, apiKeyUrl = "https://platform.deepseek.com/api_keys"),
    GROQ("Groq", "https://api.groq.com/openai/v1", listOf(
        "llama-3.3-70b-versatile" to p(0.59, 0.79),
        "openai/gpt-oss-120b" to p(0.30, 0.60),
        "llama-3.1-8b-instant" to p(0.05, 0.08),
    ), tier = ProviderTier.COMMUNITY, apiKeyUrl = "https://console.groq.com/keys"),
    ALIBABA("Alibaba Cloud (Qwen)", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", listOf(
        "qwen-plus" to p(0.80, 2.00),
        "qwen-turbo" to p(0.30, 0.60),
        "qwen3-max" to p(1.60, 6.40),
    ), tier = ProviderTier.COMMUNITY, apiKeyUrl = "https://bailian.console.alibabacloud.com/?apiKey=1#/api-key"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", listOf(
        "anthropic/claude-sonnet-4" to null,
        "google/gemini-3-flash" to null,
        "openai/gpt-5.4-mini" to null,
    ), apiKeyUrl = "https://openrouter.ai/keys", modelsEndpointPublic = true, supportsCacheControl = true),
    CUSTOM("Custom", "", listOf(), tier = ProviderTier.UNCATEGORIZED, supportsDynamicModels = false);

    val models: List<String> get() = modelPricing.map { it.first }

    companion object {
        /** Look up pricing for a model across all providers. */
        fun findPricing(model: String): ModelPricing? {
            for (provider in entries) {
                for ((name, pricing) in provider.modelPricing) {
                    if (name == model) return pricing
                }
            }
            // Strip OpenRouter prefix (e.g. "anthropic/claude-sonnet-4" → "claude-sonnet-4")
            val stripped = model.substringAfter('/')
            if (stripped != model) {
                for (provider in entries) {
                    for ((name, pricing) in provider.modelPricing) {
                        if (name == stripped) return pricing
                    }
                }
            }
            return null
        }

        /** Check if a model has known pricing. */
        fun hasKnownPricing(model: String): Boolean = findPricing(model) != null

        /** Whether a model is marked as supported (eligible for AI bug reports). Handles OpenRouter prefixes. */
        fun isModelSupported(model: String): Boolean = findPricing(model)?.supported == true
    }
}

/**
 * Transport object that travels through the LLM call chain.
 *
 * Built from an AgentPrompt's `configuredModelId`. When null, the global default
 * model from [GlobalAiSettings.defaultModelId] is used.
 */
data class LlmModelConfig(
    val configuredModelId: IdType? = null,
) {
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
    private val providerDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    /**
     * Resolve the configured model. Falls back to the global default.
     * Returns null if the model was deleted or no default is configured.
     */
    fun resolveConfiguredModel(): LlmConfiguredModel? {
        if (configuredModelId != null) {
            modelDao.getById(configuredModelId)?.let { return it }
            // Model was deleted → fall back to global default
        }
        val defaultId = AiSettings.defaultModelId ?: return null
        return modelDao.getById(defaultId)
    }

    /** Resolve the provider config via the configured model. */
    fun resolveProviderConfig(): LlmProviderConfig? {
        val model = resolveConfiguredModel() ?: return null
        return providerDao.getById(model.providerConfigId)
    }

    companion object {
        /** Build from an AgentPrompt's per-prompt model override. */
        fun fromPrompt(prompt: AgentPrompt): LlmModelConfig =
            LlmModelConfig(prompt.configuredModelId)
    }
}
