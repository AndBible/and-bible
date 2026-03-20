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

import com.google.gson.GsonBuilder
import net.bible.service.llm.tools.ToolDefinition

/**
 * Captures the raw LLM conversation for debug inspection.
 * Only populated when the "ai_debug_tools" experimental feature is enabled.
 */
class RawLlmLog {
    private val entries = mutableListOf<RawLogEntry>()

    fun addMessage(role: String, content: String?) {
        entries.add(RawLogEntry.Message(role, content))
    }

    fun addToolCall(toolName: String, toolCallId: String, arguments: String) {
        entries.add(RawLogEntry.ToolCallEntry(toolName, toolCallId, arguments))
    }

    fun addToolResult(toolCallId: String, result: String) {
        entries.add(RawLogEntry.ToolResultEntry(toolCallId, result))
    }

    fun addToolDefinitions(toolDefs: List<ToolDefinition>) {
        entries.add(RawLogEntry.ToolDefinitionsEntry(toolDefs))
    }

    fun addRawApiResponse(iteration: Int, responseBody: String) {
        entries.add(RawLogEntry.RawApiResponse(iteration, responseBody))
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun format(): String = buildString {
        for (entry in entries) {
            when (entry) {
                is RawLogEntry.Message -> {
                    appendLine("=== ${entry.role} ===")
                    appendLine(entry.content ?: "(empty)")
                    appendLine()
                }
                is RawLogEntry.ToolCallEntry -> {
                    appendLine("=== TOOL_CALL: ${entry.toolName} [${entry.id}] ===")
                    appendLine(entry.arguments)
                    appendLine()
                }
                is RawLogEntry.ToolResultEntry -> {
                    appendLine("=== TOOL_RESULT [${entry.id}] ===")
                    appendLine(entry.result)
                    appendLine()
                }
                is RawLogEntry.ToolDefinitionsEntry -> {
                    appendLine("=== TOOL DEFINITIONS (${entry.toolDefs.size} tools) ===")
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    for (def in entry.toolDefs) {
                        appendLine("--- ${def.name} ---")
                        appendLine("Description: ${def.description}")
                        appendLine("Parameters: ${gson.toJson(def.parametersSchema)}")
                        appendLine()
                    }
                }
                is RawLogEntry.RawApiResponse -> {
                    appendLine("=== RAW API RESPONSE (iteration ${entry.iteration}) ===")
                    appendLine(entry.body)
                    appendLine()
                }
            }
        }
    }
}

sealed class RawLogEntry {
    data class Message(val role: String, val content: String?) : RawLogEntry()
    data class ToolCallEntry(val toolName: String, val id: String, val arguments: String) : RawLogEntry()
    data class ToolResultEntry(val id: String, val result: String) : RawLogEntry()
    data class ToolDefinitionsEntry(val toolDefs: List<ToolDefinition>) : RawLogEntry()
    data class RawApiResponse(val iteration: Int, val body: String) : RawLogEntry()
}
