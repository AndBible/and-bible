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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// --- OpenAI-compatible response models ---

@Serializable
data class OpenAiResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null
)

@Serializable
data class OpenAiChoice(val message: OpenAiMessage)

@Serializable
data class OpenAiMessage(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiToolCall>? = null
)

@Serializable
data class OpenAiToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiFunction,
    @SerialName("extra_content") val extraContent: JsonObject? = null
)

@Serializable
data class OpenAiFunction(
    val name: String,
    val arguments: String = "{}"
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
    @SerialName("prompt_tokens_details") val promptTokensDetails: OpenAiTokenDetails? = null
)

@Serializable
data class OpenAiTokenDetails(
    @SerialName("cached_tokens") val cachedTokens: Long = 0
)

// --- Anthropic response models ---

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    val usage: AnthropicUsage? = null
)

@Serializable
sealed class AnthropicContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : AnthropicContentBlock()
}

@Serializable
data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cache_creation_input_tokens") val cacheCreationTokens: Long = 0,
    @SerialName("cache_read_input_tokens") val cacheReadTokens: Long = 0
)
