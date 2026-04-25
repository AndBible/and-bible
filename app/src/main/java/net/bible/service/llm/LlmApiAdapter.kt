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

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

import net.bible.service.llm.tools.ToolDefinition
import android.util.Log
import org.json.JSONObject

/** Shared Json instance for all LLM serialization. */
val llmJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; explicitNulls = false }

data class ToolCall(
    val id: String,
    val tool: AgentTool,
    val arguments: String,
    val thoughtSignature: String? = null
) {
    fun parseArguments(): JSONObject = if (arguments.isBlank()) {
        JSONObject()
    } else {
        JSONObject(arguments)
    }
}

sealed class ParsedResponse {
    data class ToolCalls(val toolCalls: List<ToolCall>, val content: String? = null) : ParsedResponse()
    data class TextResponse(val content: String) : ParsedResponse()
    data class ParseError(val error: String) : ParsedResponse()
}

/** Provider-agnostic message type. Adapters convert to provider-specific JSON. */
data class ChatMessage(
    val role: Role,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val toolResultBlocks: List<ToolResultBlock>? = null
) {
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

data class ToolResultBlock(val toolCallId: String, val content: String)

private fun ChatMessage.Role.toWireRole(): WireRole = when (this) {
    ChatMessage.Role.SYSTEM -> WireRole.SYSTEM
    ChatMessage.Role.USER -> WireRole.USER
    ChatMessage.Role.ASSISTANT -> WireRole.ASSISTANT
    ChatMessage.Role.TOOL -> WireRole.TOOL
}

/**
 * Abstracts API format differences between LLM providers (OpenAI vs Anthropic).
 */
interface LlmApiAdapter {
    fun buildEndpointUrl(baseEndpoint: String): String
    fun buildHeaders(apiKey: String, extraHeaders: Map<String, String>): Map<String, String>
    fun buildRequestBody(model: String, messages: List<ChatMessage>, toolDefs: List<ToolDefinition>, temperature: Double?): String
    fun parseResponse(responseBody: String): ParsedResponse
    fun extractUsage(responseBody: String): LlmUsage
    fun createAssistantToolCallMessage(toolCalls: List<ToolCall>, content: String?): ChatMessage =
        ChatMessage(role = ChatMessage.Role.ASSISTANT, content = content, toolCalls = toolCalls)
    fun createToolResultMessages(results: List<ToolResultBlock>): List<ChatMessage>
}

/** Cached JSON representation of the ephemeral cache_control object. */
private val CACHE_CONTROL_JSON: JsonElement =
    llmJson.encodeToJsonElement(AnthropicCacheControl.serializer(), AnthropicCacheControl())

/**
 * Inject a cache_control breakpoint into a message's JSON content.
 * Handles both JsonPrimitive (wraps into array) and JsonArray (adds to last block).
 * Returns the modified content, or the original if injection is not applicable.
 */
private fun injectCacheControlToContent(content: JsonElement?): JsonElement? {
    if (content == null) return null
    return when (content) {
        is JsonPrimitive -> JsonArray(listOf(JsonObject(mapOf(
            "type" to JsonPrimitive("text"),
            "text" to content,
            "cache_control" to CACHE_CONTROL_JSON
        ))))
        is JsonArray -> {
            if (content.isEmpty()) return content
            val last = content.last()
            if (last is JsonObject) {
                val modified = JsonObject(last.toMutableMap().also { it["cache_control"] = CACHE_CONTROL_JSON })
                JsonArray(content.toMutableList().apply { set(lastIndex, modified) })
            } else content
        }
        else -> content
    }
}

/** Add a cache_control breakpoint to the last element in a list of wire tools. */
private fun <T : CacheableWireTool<T>> addToolsCacheBreakpoint(tools: List<T>): List<T> {
    if (tools.isEmpty()) return tools
    return tools.toMutableList().apply {
        this[lastIndex] = last().withCacheControl(AnthropicCacheControl())
    }
}

/**
 * Anthropic Claude models on OpenRouter that do NOT support inline cache_control
 * breakpoints. Sending cache_control to these models triggers a 400 error
 * ("You invoked an unsupported model or your request did not allow prompt caching").
 *
 * Matches both dot- and dash-separated version numbers (e.g. `claude-3.5-sonnet`
 * and `claude-3-5-sonnet`) and any suffix variants (e.g. `:thinking`, `:beta`,
 * version dates).
 */
private val NO_CACHE_CONTROL_REGEX = Regex(
    "^anthropic/claude-(" +
        "3-haiku" +              // claude-3-haiku
        "|3[.-]5-haiku" +        // claude-3.5-haiku
        "|3[.-]5-sonnet" +       // claude-3.5-sonnet
        "|3[.-]7-sonnet" +       // claude-3.7-sonnet (incl. :thinking variant)
    ")"
)

/**
 * Whether the given model supports Anthropic-style cache_control breakpoints
 * via OpenRouter.
 *
 * Only Anthropic Claude models accept cache_control markers. Older Claude
 * variants without prompt-caching support (see [NO_CACHE_CONTROL_REGEX]) are
 * excluded. Non-Anthropic models (gemini/, openai/, etc.) do not understand
 * the cache_control field at all.
 */
internal fun modelSupportsCacheControl(model: String): Boolean {
    if (!model.startsWith("anthropic/claude")) return false
    if (NO_CACHE_CONTROL_REGEX.containsMatchIn(model)) return false
    return true
}

/**
 * OpenAI-compatible API format (also used by Gemini, xAI, Mistral, DeepSeek, Groq, OpenRouter).
 *
 * @param supportsCacheControl When true, the provider (e.g. OpenRouter) is capable of
 *   forwarding inline Anthropic-style cache_control breakpoints. The actual decision to
 *   add them per request is gated by [modelSupportsCacheControl], so non-Anthropic
 *   models and older Claude variants without caching support are skipped. Top-level
 *   cache_control is never used (OpenRouter rejects it with 404 for unsupported
 *   automatic-caching endpoints).
 */
class OpenAiApiAdapter(
    private val supportsCacheControl: Boolean = false
) : LlmApiAdapter {

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

    override fun buildRequestBody(model: String, messages: List<ChatMessage>, toolDefs: List<ToolDefinition>, temperature: Double?): String {
        val wireMessages = messages.map { it.toOpenAiWire() }.toMutableList()
        var wireTools = toolDefs.map { def ->
            OpenAiWireTool(function = OpenAiWireToolDef(
                name = def.name,
                description = def.description,
                parameters = def.parametersSchema
            ))
        }

        if (supportsCacheControl && modelSupportsCacheControl(model)) {
            wireTools = addToolsCacheBreakpoint(wireTools)
            addLastMessageCacheBreakpoint(wireMessages)
        }

        val request = OpenAiRequest(
            model = model,
            messages = wireMessages,
            tools = wireTools.ifEmpty { null },
            temperature = temperature
        )
        return llmJson.encodeToString(request)
    }

    private fun addLastMessageCacheBreakpoint(messages: MutableList<OpenAiWireMessage>) {
        val targetIdx = messages.indexOfLast { it.role == WireRole.USER || it.role == WireRole.TOOL }
        if (targetIdx < 0) return
        val msg = messages[targetIdx]
        val modified = injectCacheControlToContent(msg.content)
        if (modified !== msg.content) {
            messages[targetIdx] = msg.copy(content = modified)
        }
    }

    private fun ChatMessage.toOpenAiWire(): OpenAiWireMessage = when {
        // Tool result message
        role == ChatMessage.Role.TOOL && toolCallId != null -> OpenAiWireMessage(
            role = WireRole.TOOL,
            content = JsonPrimitive(content ?: ""),
            toolCallId = toolCallId
        )
        // Assistant message with tool calls
        toolCalls != null -> OpenAiWireMessage(
            role = WireRole.ASSISTANT,
            content = if (content != null) JsonPrimitive(content) else JsonNull,
            toolCalls = toolCalls.map { tc ->
                val extraContent = tc.thoughtSignature?.let { sig ->
                    JsonObject(mapOf("google" to JsonObject(mapOf(
                        "thought_signature" to JsonPrimitive(sig)
                    ))))
                }
                OpenAiWireToolCall(
                    id = tc.id,
                    function = OpenAiWireFunction(
                        name = tc.tool.camelCaseName,
                        arguments = tc.arguments
                    ),
                    extraContent = extraContent
                )
            }
        )
        // Simple text message
        else -> OpenAiWireMessage(
            role = role.toWireRole(),
            content = JsonPrimitive(content ?: "")
        )
    }

    override fun parseResponse(responseBody: String): ParsedResponse {
        return try {
            val response = llmJson.decodeFromString<OpenAiResponse>(responseBody)
            val message = response.choices.firstOrNull()?.message
                ?: return ParsedResponse.ParseError(application.getString(R.string.llm_parse_error_no_choices))

            val toolCalls = message.toolCalls?.mapNotNull { tc ->
                if (tc.type != "function") null
                else {
                    val agentTool = AgentTool.fromToolName(tc.function.name)
                    if (agentTool == null) {
                        Log.w("LlmApiAdapter", "Unknown tool name from LLM: ${tc.function.name}")
                        null
                    } else {
                        val thoughtSig = tc.extraContent
                            ?.get("google")
                            ?.let { it as? JsonObject }
                            ?.get("thought_signature")
                            ?.let { it as? JsonPrimitive }
                            ?.content
                        ToolCall(tc.id, agentTool, tc.function.arguments, thoughtSig)
                    }
                }
            }

            val content = message.content?.takeIf { it.isNotBlank() }

            if (!toolCalls.isNullOrEmpty()) {
                ParsedResponse.ToolCalls(toolCalls, content)
            } else {
                ParsedResponse.TextResponse(content ?: "")
            }
        } catch (e: Exception) {
            ParsedResponse.ParseError(application.getString(R.string.llm_parse_error_openai, e.message ?: ""))
        }
    }

    override fun extractUsage(responseBody: String): LlmUsage {
        return try {
            val response = llmJson.decodeFromString<OpenAiResponse>(responseBody)
            val usage = response.usage ?: return LlmUsage()
            val cachedInput = usage.promptTokensDetails?.cachedTokens ?: 0
            LlmUsage(
                inputTokens = usage.promptTokens - cachedInput,
                outputTokens = usage.completionTokens,
                cacheReadTokens = cachedInput
            )
        } catch (_: Exception) {
            LlmUsage()
        }
    }

    override fun createToolResultMessages(results: List<ToolResultBlock>): List<ChatMessage> {
        return results.map { (toolCallId, content) ->
            ChatMessage(
                role = ChatMessage.Role.TOOL,
                content = content,
                toolCallId = toolCallId
            )
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

    override fun buildRequestBody(model: String, messages: List<ChatMessage>, toolDefs: List<ToolDefinition>, temperature: Double?): String {
        var systemBlocks: List<AnthropicSystemBlock>? = null
        val wireMessages = mutableListOf<AnthropicWireMessage>()

        for (msg in messages) {
            if (msg.role == ChatMessage.Role.SYSTEM) {
                systemBlocks = listOf(AnthropicSystemBlock(
                    text = msg.content ?: "",
                    cacheControl = AnthropicCacheControl()
                ))
            } else {
                wireMessages.add(msg.toAnthropicWire())
            }
        }

        var wireTools = toolDefs.map { def ->
            AnthropicWireTool(
                name = def.name,
                description = def.description,
                inputSchema = def.parametersSchema
            )
        }

        wireTools = addToolsCacheBreakpoint(wireTools)
        addConversationCacheBreakpoint(wireMessages)

        val request = AnthropicRequest(
            model = model,
            system = systemBlocks,
            messages = wireMessages,
            maxTokens = DEFAULT_MAX_TOKENS,
            tools = wireTools.ifEmpty { null },
            temperature = temperature
        )
        return llmJson.encodeToString(request)
    }

    /**
     * Adds a cache_control breakpoint to the last user message's last content block.
     * This ensures the entire conversation history up to this point is cached.
     */
    private fun addConversationCacheBreakpoint(messages: MutableList<AnthropicWireMessage>) {
        val lastUserIdx = messages.indexOfLast { it.role == WireRole.USER }
        if (lastUserIdx < 0) return
        val msg = messages[lastUserIdx]
        val modified = injectCacheControlToContent(msg.content)
        if (modified !== msg.content) {
            messages[lastUserIdx] = AnthropicWireMessage(role = msg.role, content = modified!!)
        }
    }

    private fun ChatMessage.toAnthropicWire(): AnthropicWireMessage = when {
        // User message with batched tool results
        toolResultBlocks != null -> AnthropicWireMessage(
            role = WireRole.USER,
            content = llmJson.encodeToJsonElement(ListSerializer(AnthropicRequestContentBlock.serializer()), toolResultBlocks.map { block ->
                AnthropicRequestContentBlock.ToolResult(
                    toolUseId = block.toolCallId,
                    content = block.content
                )
            })
        )
        // Assistant message with tool calls
        toolCalls != null -> {
            val blocks = mutableListOf<AnthropicRequestContentBlock>()
            if (content != null) {
                blocks.add(AnthropicRequestContentBlock.Text(content))
            }
            for (tc in toolCalls) {
                blocks.add(AnthropicRequestContentBlock.ToolUse(
                    id = tc.id,
                    name = tc.tool.camelCaseName,
                    input = llmJson.parseToJsonElement(tc.arguments) as? JsonObject ?: JsonObject(emptyMap())
                ))
            }
            AnthropicWireMessage(
                role = WireRole.ASSISTANT,
                content = llmJson.encodeToJsonElement(ListSerializer(AnthropicRequestContentBlock.serializer()), blocks.toList())
            )
        }
        // Simple text message
        else -> AnthropicWireMessage(
            role = role.toWireRole(),
            content = JsonPrimitive(content ?: "")
        )
    }

    override fun parseResponse(responseBody: String): ParsedResponse {
        return try {
            val response = llmJson.decodeFromString<AnthropicResponse>(responseBody)
            val textParts = mutableListOf<String>()
            val toolCalls = mutableListOf<ToolCall>()

            for (block in response.content) {
                when (block) {
                    is AnthropicContentBlock.Text -> textParts.add(block.text)
                    is AnthropicContentBlock.ToolUse -> {
                        val agentTool = AgentTool.fromToolName(block.name)
                        if (agentTool == null) {
                            Log.w("LlmApiAdapter", "Unknown tool name from LLM: ${block.name}")
                        } else {
                            toolCalls.add(ToolCall(
                                id = block.id,
                                tool = agentTool,
                                arguments = block.input.toString()
                            ))
                        }
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
            ParsedResponse.ParseError(application.getString(R.string.llm_parse_error_anthropic, e.message ?: ""))
        }
    }

    override fun extractUsage(responseBody: String): LlmUsage {
        return try {
            val response = llmJson.decodeFromString<AnthropicResponse>(responseBody)
            val usage = response.usage ?: return LlmUsage()
            LlmUsage(
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cacheCreationTokens = usage.cacheCreationTokens,
                cacheReadTokens = usage.cacheReadTokens
            )
        } catch (_: Exception) {
            LlmUsage()
        }
    }

    override fun createToolResultMessages(results: List<ToolResultBlock>): List<ChatMessage> {
        // Anthropic requires all tool results in a single user message
        return listOf(ChatMessage(
            role = ChatMessage.Role.USER,
            toolResultBlocks = results
        ))
    }
}
