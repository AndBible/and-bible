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
import net.bible.service.db.DatabaseContainer

enum class ApiFormat { OPENAI, ANTHROPIC }

/** Pricing per million tokens (USD). */
data class ModelPricing(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
    val cacheCreationPerMillion: Double = inputPerMillion,
    val cacheReadPerMillion: Double = inputPerMillion * 0.1
)

private fun p(input: Double, output: Double, cacheCreate: Double = input, cacheRead: Double = input * 0.1) =
    ModelPricing(input, output, cacheCreate, cacheRead)

enum class LlmProvider(
    val displayName: String,
    val endpoint: String,
    val modelPricing: List<Pair<String, ModelPricing?>>,
    val apiKeyPrefix: String? = null,
    val apiFormat: ApiFormat = ApiFormat.OPENAI
) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", listOf(
        "gemini-2.5-flash" to p(0.15, 0.60, 0.15, 0.0375),
        "gemini-2.5-pro" to p(1.25, 10.00, 1.25, 0.3125),
        "gemini-3-flash" to p(0.15, 0.60),
    ), "AIza"),
    OPENAI("OpenAI (ChatGPT)", "https://api.openai.com/v1", listOf(
        "gpt-5-mini" to p(0.40, 1.60, 0.40, 0.10),
        "gpt-5-nano" to p(0.10, 0.40, 0.10, 0.025),
        "gpt-5.2" to p(2.00, 8.00, 2.00, 0.50),
        "gpt-4o-mini" to p(0.15, 0.60, 0.15, 0.075),
    ), "sk-"),
    ANTHROPIC("Anthropic (Claude)", "https://api.anthropic.com/v1", listOf(
        "claude-haiku-4-5" to p(0.80, 4.00, 1.00, 0.08),
        "claude-sonnet-4-6" to p(3.00, 15.00, 3.75, 0.30),
        "claude-opus-4-6" to p(15.00, 75.00, 18.75, 1.50),
    ), "sk-ant-", ApiFormat.ANTHROPIC),
    XAI("xAI (Grok)", "https://api.x.ai/v1", listOf(
        "grok-4-0709" to p(3.00, 15.00),
        "grok-4-1-fast-reasoning" to p(3.00, 15.00),
        "grok-3-mini" to p(0.30, 0.50),
    ), "xai-"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1", listOf(
        "mistral-small-latest" to p(0.10, 0.30),
        "mistral-large-latest" to p(2.00, 6.00),
    )),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", listOf(
        "deepseek-chat" to p(0.27, 1.10, 0.27, 0.07),
        "deepseek-reasoner" to p(0.55, 2.19, 0.55, 0.14),
    )),
    GROQ("Groq", "https://api.groq.com/openai/v1", listOf(
        "llama-3.3-70b-versatile" to p(0.59, 0.79),
        "openai/gpt-oss-120b" to p(0.30, 0.60),
        "llama-3.1-8b-instant" to p(0.05, 0.08),
    )),
    ALIBABA("Alibaba Cloud (Qwen)", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", listOf(
        "qwen-plus" to p(0.80, 2.00),
        "qwen-turbo" to p(0.30, 0.60),
        "qwen3-max" to p(1.60, 6.40),
    )),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", listOf(
        "anthropic/claude-sonnet-4" to null,
        "google/gemini-2.5-flash" to null,
        "openai/gpt-5-mini" to null,
    )),
    CUSTOM("Custom", "", listOf());

    val models: List<String> get() = modelPricing.map { it.first }

    val apiAdapter: LlmApiAdapter get() = when (apiFormat) {
        ApiFormat.OPENAI -> OpenAiApiAdapter()
        ApiFormat.ANTHROPIC -> AnthropicApiAdapter()
    }

    companion object {
        fun fromEndpoint(endpoint: String): LlmProvider {
            val normalized = endpoint.trimEnd('/')
            return entries.firstOrNull {
                it != CUSTOM && it.endpoint.trimEnd('/') == normalized
            } ?: CUSTOM
        }

        fun fromApiKey(apiKey: String): LlmProvider? =
            entries.filter { it.apiKeyPrefix != null && apiKey.startsWith(it.apiKeyPrefix!!) }
                .maxByOrNull { it.apiKeyPrefix!!.length }

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
    }
}

/**
 * Transport object that travels through the LLM call chain.
 *
 * Built from an AgentPrompt's DB columns:
 *   `LlmModelConfig(prompt.providerConfigId, prompt.modelOverride)`
 *
 * When both fields are null, the global default provider is used.
 */
data class LlmModelConfig(
    val providerConfigId: IdType? = null,
    val model: String? = null,
) {
    val isDefault: Boolean get() = providerConfigId == null && model == null

    private val dao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    /** Resolve the LlmProviderConfig from the database. */
    fun resolveProviderConfig(): LlmProviderConfig? =
        providerConfigId?.let { dao.getById(it) } ?: dao.getDefault()

    /** Resolve the effective model name. */
    fun resolveModel(providerConfig: LlmProviderConfig): String =
        model?.takeIf { it.isNotBlank() } ?: providerConfig.resolveDefaultModel()

    companion object {
        /** Build from an AgentPrompt's per-prompt overrides. */
        fun fromPrompt(prompt: AgentPrompt): LlmModelConfig =
            LlmModelConfig(prompt.providerConfigId, prompt.modelOverride)
    }
}
