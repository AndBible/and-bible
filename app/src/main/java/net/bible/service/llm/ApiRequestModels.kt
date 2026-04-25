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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Wire-format role values for LLM API messages. */
@Serializable
enum class WireRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL
}

// --- OpenAI request models ---

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiWireMessage>,
    val tools: List<OpenAiWireTool>? = null,
    val temperature: Double? = null
)

@Serializable
data class OpenAiWireMessage(
    val role: WireRole,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<OpenAiWireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
data class OpenAiWireToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenAiWireFunction,
    @SerialName("extra_content") val extraContent: JsonObject? = null
)

@Serializable
data class OpenAiWireFunction(
    val name: String,
    val arguments: String
)

/** Shared interface for wire tool types that support cache_control breakpoints. */
interface CacheableWireTool<T> {
    val cacheControl: AnthropicCacheControl?
    fun withCacheControl(cc: AnthropicCacheControl): T
}

@Serializable
data class OpenAiWireTool(
    val type: String = "function",
    val function: OpenAiWireToolDef,
    @SerialName("cache_control") override val cacheControl: AnthropicCacheControl? = null
) : CacheableWireTool<OpenAiWireTool> {
    override fun withCacheControl(cc: AnthropicCacheControl) = copy(cacheControl = cc)
}

@Serializable
data class OpenAiWireToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

// --- Anthropic request models ---

@Serializable
data class AnthropicRequest(
    val model: String,
    val system: List<AnthropicSystemBlock>? = null,
    val messages: List<AnthropicWireMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val tools: List<AnthropicWireTool>? = null,
    val temperature: Double? = null
)

@Serializable
data class AnthropicSystemBlock(
    val type: String = "text",
    val text: String,
    @SerialName("cache_control") val cacheControl: AnthropicCacheControl? = null
)

@Serializable
data class AnthropicCacheControl(val type: String = "ephemeral")

@Serializable
data class AnthropicWireMessage(
    val role: WireRole,
    val content: JsonElement  // String or array of content blocks
)

@Serializable
sealed class AnthropicRequestContentBlock {
    @Serializable @SerialName("text")
    data class Text(val text: String) : AnthropicRequestContentBlock()

    @Serializable @SerialName("tool_use")
    data class ToolUse(val id: String, val name: String, val input: JsonObject) : AnthropicRequestContentBlock()

    @Serializable @SerialName("tool_result")
    data class ToolResult(
        @SerialName("tool_use_id") val toolUseId: String,
        val content: String,
        @SerialName("cache_control") val cacheControl: AnthropicCacheControl? = null
    ) : AnthropicRequestContentBlock()
}

@Serializable
data class AnthropicWireTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject,
    @SerialName("cache_control") override val cacheControl: AnthropicCacheControl? = null
) : CacheableWireTool<AnthropicWireTool> {
    override fun withCacheControl(cc: AnthropicCacheControl) = copy(cacheControl = cc)
}

// --- Utility: convert org.json to kotlinx.serialization ---

/** Convert org.json.JSONObject to kotlinx.serialization JsonObject via string round-trip. */
fun orgJsonToJsonObject(obj: org.json.JSONObject): JsonObject =
    llmJson.parseToJsonElement(obj.toString()) as JsonObject

/** Convert org.json.JSONArray to kotlinx.serialization JsonArray via string round-trip. */
fun orgJsonToJsonArray(arr: org.json.JSONArray): JsonArray =
    llmJson.parseToJsonElement(arr.toString()) as JsonArray
