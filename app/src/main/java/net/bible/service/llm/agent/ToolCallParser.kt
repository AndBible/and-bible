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

package net.bible.service.llm.agent

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
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

/** Parses OpenAI-format API responses with tool calling. */
object ToolCallParser {

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
            Log.w("ToolCallParser", "Failed to parse tool call: ${e.message}")
            null
        }
    }

    fun createToolResultMessage(toolCallId: String, content: String): JSONObject {
        return JSONObject().apply {
            put("role", "tool")
            put("tool_call_id", toolCallId)
            put("content", content)
        }
    }

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
