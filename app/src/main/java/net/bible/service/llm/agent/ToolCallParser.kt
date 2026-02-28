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

package net.bible.service.llm.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a tool call from the LLM response.
 *
 * @param id Unique identifier for this tool call (used to match results)
 * @param name Name of the tool to call
 * @param arguments JSON string of arguments
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
) {
    /**
     * Parse arguments as JSONObject.
     */
    fun parseArguments(): JSONObject = if (arguments.isBlank()) {
        JSONObject()
    } else {
        JSONObject(arguments)
    }
}

/**
 * Result of parsing an LLM response message.
 */
sealed class ParsedResponse {

    /**
     * The LLM wants to call one or more tools.
     *
     * @param toolCalls List of tool calls to execute
     * @param content Optional text content alongside tool calls
     */
    data class ToolCalls(
        val toolCalls: List<ToolCall>,
        val content: String? = null
    ) : ParsedResponse()

    /**
     * The LLM returned a final text response (no tool calls).
     *
     * @param content The text content
     */
    data class TextResponse(val content: String) : ParsedResponse()

    /**
     * The response could not be parsed.
     *
     * @param error Error description
     */
    data class ParseError(val error: String) : ParsedResponse()
}

/**
 * Parser for OpenAI API responses with tool calling.
 */
object ToolCallParser {

    /**
     * Parse the message object from an LLM response.
     *
     * OpenAI format for tool calls:
     * ```json
     * {
     *   "role": "assistant",
     *   "content": null,  // or optional text
     *   "tool_calls": [
     *     {
     *       "id": "call_abc123",
     *       "type": "function",
     *       "function": {
     *         "name": "getVerseContent",
     *         "arguments": "{\"book\": \"KJV\", \"verseRef\": \"Matt.5.3\"}"
     *       }
     *     }
     *   ]
     * }
     * ```
     *
     * @param message The message JSONObject from the LLM response
     * @return Parsed response indicating tool calls or text response
     */
    fun parseMessage(message: JSONObject): ParsedResponse {
        return try {
            // Check if there are tool_calls
            if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                val toolCallsArray = message.getJSONArray("tool_calls")
                val toolCalls = parseToolCalls(toolCallsArray)
                val content = if (message.has("content") && !message.isNull("content")) {
                    message.getString("content")
                } else null

                if (toolCalls.isEmpty()) {
                    // No valid tool calls found, treat as text response
                    ParsedResponse.TextResponse(content ?: "")
                } else {
                    ParsedResponse.ToolCalls(toolCalls, content)
                }
            } else {
                // No tool calls, this is a text response
                val content = if (message.has("content") && !message.isNull("content")) {
                    message.getString("content")
                } else ""
                ParsedResponse.TextResponse(content)
            }
        } catch (e: Exception) {
            ParsedResponse.ParseError("Failed to parse message: ${e.message}")
        }
    }

    /**
     * Parse the tool_calls array.
     */
    private fun parseToolCalls(toolCallsArray: JSONArray): List<ToolCall> {
        val result = mutableListOf<ToolCall>()
        for (i in 0 until toolCallsArray.length()) {
            val toolCallObj = toolCallsArray.getJSONObject(i)
            val toolCall = parseToolCall(toolCallObj)
            if (toolCall != null) {
                result.add(toolCall)
            }
        }
        return result
    }

    /**
     * Parse a single tool_call object.
     */
    private fun parseToolCall(toolCallObj: JSONObject): ToolCall? {
        return try {
            val id = toolCallObj.getString("id")
            val type = toolCallObj.optString("type", "function")

            // Only handle function calls for now
            if (type != "function") {
                return null
            }

            val functionObj = toolCallObj.getJSONObject("function")
            val name = functionObj.getString("name")
            val arguments = functionObj.optString("arguments", "{}")

            ToolCall(id, name, arguments)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a tool result message for the conversation.
     *
     * Format:
     * ```json
     * {
     *   "role": "tool",
     *   "tool_call_id": "call_abc123",
     *   "content": "{\"status\":\"success\",\"data\":...}"
     * }
     * ```
     *
     * @param toolCallId The ID of the tool call this is responding to
     * @param content The JSON content of the result
     */
    fun createToolResultMessage(toolCallId: String, content: String): JSONObject {
        return JSONObject().apply {
            put("role", "tool")
            put("tool_call_id", toolCallId)
            put("content", content)
        }
    }

    /**
     * Create an assistant message with tool calls for adding to conversation history.
     *
     * This recreates the assistant's message with tool calls for proper conversation flow.
     *
     * @param toolCalls List of tool calls from the assistant
     * @param content Optional text content from the assistant
     */
    fun createAssistantToolCallMessage(toolCalls: List<ToolCall>, content: String? = null): JSONObject {
        return JSONObject().apply {
            put("role", "assistant")
            if (content != null) {
                put("content", content)
            } else {
                put("content", JSONObject.NULL)
            }
            val toolCallsArray = JSONArray()
            for (toolCall in toolCalls) {
                toolCallsArray.put(JSONObject().apply {
                    put("id", toolCall.id)
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", toolCall.name)
                        put("arguments", toolCall.arguments)
                    })
                })
            }
            put("tool_calls", toolCallsArray)
        }
    }
}
