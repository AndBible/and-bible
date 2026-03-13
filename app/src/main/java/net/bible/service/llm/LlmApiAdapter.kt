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

import net.bible.service.llm.agent.ParsedResponse
import net.bible.service.llm.agent.ToolCall
import net.bible.service.llm.agent.ToolCallParser
import net.bible.service.llm.tools.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * Abstracts API format differences between LLM providers (OpenAI vs Anthropic).
 */
interface LlmApiAdapter {
    fun buildEndpointUrl(baseEndpoint: String): String
    fun buildHeaders(apiKey: String, extraHeaders: Map<String, String>): Map<String, String>
    fun buildRequestBody(model: String, messages: JSONArray, tools: JSONArray, temperature: Double): JSONObject
    fun parseResponse(responseJson: JSONObject): ParsedResponse
    fun extractUsage(responseJson: JSONObject): LlmUsage
    fun buildToolsArray(toolDefs: List<ToolDefinition>): JSONArray
    fun createAssistantToolCallMessage(toolCalls: List<ToolCall>, content: String?): JSONObject
    fun createToolResultMessages(results: List<Pair<String, String>>): List<JSONObject>
}

/**
 * OpenAI-compatible API format (also used by Gemini, xAI, Mistral, DeepSeek, Groq, OpenRouter).
 */
class OpenAiApiAdapter : LlmApiAdapter {

    override fun buildEndpointUrl(baseEndpoint: String): String =
        "${baseEndpoint.trimEnd('/')}/chat/completions"

    override fun buildHeaders(apiKey: String, extraHeaders: Map<String, String>): Map<String, String> {
        val headers = mutableMapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json"
        )
        headers.putAll(extraHeaders)
        return headers
    }

    override fun buildRequestBody(model: String, messages: JSONArray, tools: JSONArray, temperature: Double): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (tools.length() > 0) {
                put("tools", tools)
            }
            put("temperature", temperature)
        }
    }

    override fun parseResponse(responseJson: JSONObject): ParsedResponse {
        return try {
            val assistantMessage = responseJson
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
            ToolCallParser.parseMessage(assistantMessage)
        } catch (e: Exception) {
            ParsedResponse.ParseError("Failed to parse OpenAI response: ${e.message}")
        }
    }

    override fun extractUsage(responseJson: JSONObject): LlmUsage {
        val usage = responseJson.optJSONObject("usage") ?: return LlmUsage()
        val totalInput = usage.optLong("prompt_tokens", 0)
        val cachedInput = usage.optJSONObject("prompt_tokens_details")?.optLong("cached_tokens", 0) ?: 0
        return LlmUsage(
            inputTokens = totalInput - cachedInput,
            outputTokens = usage.optLong("completion_tokens", 0),
            cacheReadTokens = cachedInput
        )
    }

    override fun buildToolsArray(toolDefs: List<ToolDefinition>): JSONArray {
        val array = JSONArray()
        for (def in toolDefs) {
            array.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", def.name)
                    put("description", def.description)
                    put("parameters", def.parametersSchema)
                })
            })
        }
        return array
    }

    override fun createAssistantToolCallMessage(toolCalls: List<ToolCall>, content: String?): JSONObject {
        return ToolCallParser.createAssistantToolCallMessage(toolCalls, content)
    }

    override fun createToolResultMessages(results: List<Pair<String, String>>): List<JSONObject> {
        return results.map { (toolCallId, content) ->
            ToolCallParser.createToolResultMessage(toolCallId, content)
        }
    }
}

/**
 * Anthropic Messages API format with prompt caching support.
 */
class AnthropicApiAdapter : LlmApiAdapter {

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val DEFAULT_MAX_TOKENS = 8192
    }

    override fun buildEndpointUrl(baseEndpoint: String): String =
        "${baseEndpoint.trimEnd('/')}/messages"

    override fun buildHeaders(apiKey: String, extraHeaders: Map<String, String>): Map<String, String> {
        val headers = mutableMapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to ANTHROPIC_VERSION,
            "Content-Type" to "application/json"
        )
        headers.putAll(extraHeaders)
        return headers
    }

    override fun buildRequestBody(model: String, messages: JSONArray, tools: JSONArray, temperature: Double): JSONObject {
        // Extract system message from messages array, add cache_control for prompt caching
        var systemContent: JSONArray? = null
        val apiMessages = JSONArray()

        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            if (msg.getString("role") == "system") {
                // System message goes to top-level "system" field with prompt caching
                systemContent = JSONArray().put(JSONObject().apply {
                    put("type", "text")
                    put("text", msg.getString("content"))
                    put("cache_control", JSONObject().put("type", "ephemeral"))
                })
            } else {
                apiMessages.put(msg)
            }
        }

        return JSONObject().apply {
            put("model", model)
            if (systemContent != null) {
                put("system", systemContent)
            }
            put("messages", apiMessages)
            put("max_tokens", DEFAULT_MAX_TOKENS)
            if (tools.length() > 0) {
                put("tools", tools)
            }
            put("temperature", temperature)
        }
    }

    override fun parseResponse(responseJson: JSONObject): ParsedResponse {
        return try {
            val contentArray = responseJson.getJSONArray("content")
            val textParts = mutableListOf<String>()
            val toolCalls = mutableListOf<ToolCall>()

            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                when (block.getString("type")) {
                    "text" -> textParts.add(block.getString("text"))
                    "tool_use" -> {
                        toolCalls.add(ToolCall(
                            id = block.getString("id"),
                            name = block.getString("name"),
                            arguments = block.getJSONObject("input").toString()
                        ))
                    }
                }
            }

            val textContent = textParts.joinToString("").takeIf { it.isNotBlank() }

            if (toolCalls.isNotEmpty()) {
                ParsedResponse.ToolCalls(toolCalls, textContent)
            } else {
                ParsedResponse.TextResponse(textContent ?: "")
            }
        } catch (e: Exception) {
            ParsedResponse.ParseError("Failed to parse Anthropic response: ${e.message}")
        }
    }

    override fun extractUsage(responseJson: JSONObject): LlmUsage {
        val usage = responseJson.optJSONObject("usage") ?: return LlmUsage()
        return LlmUsage(
            inputTokens = usage.optLong("input_tokens", 0),
            outputTokens = usage.optLong("output_tokens", 0),
            cacheCreationTokens = usage.optLong("cache_creation_input_tokens", 0),
            cacheReadTokens = usage.optLong("cache_read_input_tokens", 0)
        )
    }

    override fun buildToolsArray(toolDefs: List<ToolDefinition>): JSONArray {
        val array = JSONArray()
        for (def in toolDefs) {
            array.put(JSONObject().apply {
                put("name", def.name)
                put("description", def.description)
                put("input_schema", def.parametersSchema)
            })
        }
        return array
    }

    override fun createAssistantToolCallMessage(toolCalls: List<ToolCall>, content: String?): JSONObject {
        val contentArray = JSONArray()

        // Add text block if present
        if (content != null) {
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", content)
            })
        }

        // Add tool_use blocks
        for (toolCall in toolCalls) {
            contentArray.put(JSONObject().apply {
                put("type", "tool_use")
                put("id", toolCall.id)
                put("name", toolCall.name)
                put("input", JSONObject(toolCall.arguments))
            })
        }

        return JSONObject().apply {
            put("role", "assistant")
            put("content", contentArray)
        }
    }

    override fun createToolResultMessages(results: List<Pair<String, String>>): List<JSONObject> {
        // Anthropic requires all tool results in a single user message
        val contentArray = JSONArray()
        for ((toolCallId, content) in results) {
            contentArray.put(JSONObject().apply {
                put("type", "tool_result")
                put("tool_use_id", toolCallId)
                put("content", content)
            })
        }
        return listOf(JSONObject().apply {
            put("role", "user")
            put("content", contentArray)
        })
    }
}
