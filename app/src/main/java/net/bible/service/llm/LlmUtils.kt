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

enum class LlmProvider(
    val displayName: String,
    val endpoint: String,
    val models: List<String>,
    val apiKeyPrefix: String? = null
) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/",
        listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-3-flash"), "AIza"),
    OPENAI("OpenAI (ChatGPT)", "https://api.openai.com/v1",
        listOf("gpt-5-mini", "gpt-5-nano", "gpt-5.2", "gpt-4o-mini"), "sk-"),
    XAI("xAI (Grok)", "https://api.x.ai/v1",
        listOf("grok-4-0709", "grok-4-1-fast-reasoning", "grok-3-mini"), "xai-"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1",
        listOf("mistral-small-latest", "mistral-large-latest")),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1",
        listOf("deepseek-chat", "deepseek-reasoner")),
    GROQ("Groq", "https://api.groq.com/openai/v1",
        listOf("llama-3.3-70b-versatile", "openai/gpt-oss-120b", "llama-3.1-8b-instant")),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1",
        listOf("anthropic/claude-sonnet-4", "google/gemini-2.5-flash", "openai/gpt-5-mini")),
    CUSTOM("Custom", "", listOf());

    companion object {
        fun fromEndpoint(endpoint: String): LlmProvider {
            val normalized = endpoint.trimEnd('/')
            return entries.firstOrNull {
                it != CUSTOM && it.endpoint.trimEnd('/') == normalized
            } ?: CUSTOM
        }

        fun fromApiKey(apiKey: String): LlmProvider? =
            entries.firstOrNull { it.apiKeyPrefix != null && apiKey.startsWith(it.apiKeyPrefix!!) }
    }
}
