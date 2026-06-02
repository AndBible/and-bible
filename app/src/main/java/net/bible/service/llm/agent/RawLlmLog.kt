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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.database.IdType
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.tools.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Per-iteration usage and model data, stored alongside raw API responses.
 */
data class IterationUsageData(
    val usage: LlmUsage,
    val model: String,
    val configuredModelId: IdType? = null
)

/**
 * Captures the raw LLM conversation for debug inspection.
 */
class RawLlmLog {
    private val entries = mutableListOf<RawLogEntry>()
    private val _usageByIteration = mutableMapOf<Int, IterationUsageData>()

    val usageByIteration: Map<Int, IterationUsageData> get() = _usageByIteration

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

    fun addUsageForIteration(iteration: Int, usage: LlmUsage, model: String, configuredModelId: IdType? = null) {
        _usageByIteration[iteration] = IterationUsageData(usage, model, configuredModelId)
    }

    fun getEntries(): List<RawLogEntry> = entries.toList()

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
                    appendLine(prettyFormatJson(entry.arguments))
                    appendLine()
                }
                is RawLogEntry.ToolResultEntry -> {
                    appendLine("=== TOOL_RESULT [${entry.id}] ===")
                    appendLine(prettyFormatJson(entry.result))
                    appendLine()
                }
                is RawLogEntry.ToolDefinitionsEntry -> {
                    appendLine("=== TOOL DEFINITIONS (${entry.toolDefs.size} tools) ===")
                    for (def in entry.toolDefs) {
                        appendLine("--- ${def.name} ---")
                        appendLine("Description: ${def.description}")
                        appendLine("Parameters: ${prettyJson.encodeToString(def.parametersSchema)}")
                        appendLine()
                    }
                }
                is RawLogEntry.RawApiResponse -> {
                    appendLine("=== RAW API RESPONSE (iteration ${entry.iteration}) ===")
                    appendLine(prettyFormatJson(entry.body))
                    appendLine()
                }
            }
        }
    }

    companion object {
        private val prettyJson = Json { prettyPrint = true }

        fun gzipCompress(text: String): ByteArray {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            return bos.toByteArray()
        }

        fun gzipDecompress(data: ByteArray): String =
            GZIPInputStream(ByteArrayInputStream(data)).bufferedReader(Charsets.UTF_8).use { it.readText() }

        /** Regex matching a JSON string value that is at least 80 chars long. */
        private val longStringValueRegex = Regex(""""((?:[^"\\]|\\.){80,})"""")

        /**
         * Pretty-print a JSON string with indentation and unescape long string values
         * so that markdown content with \n becomes readable with actual line breaks.
         */
        private fun prettyFormatJson(json: String): String = try {
            val trimmed = json.trim()
            val formatted = when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> json
            }
            unescapeJsonStringContents(formatted)
        } catch (_: Exception) {
            json
        }

        /**
         * In pretty-printed JSON, replace escaped newlines/tabs inside long string values
         * with actual whitespace characters so markdown content is readable.
         */
        private fun unescapeJsonStringContents(prettyJson: String): String =
            longStringValueRegex.replace(prettyJson) { match ->
                val unescaped = match.value
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                unescaped
            }
    }
}

sealed class RawLogEntry {
    /** Heuristic token estimate: ~4 chars per token. */
    fun estimateTokens(): Int = (charCount() / 4).coerceAtLeast(1)

    protected abstract fun charCount(): Int

    data class Message(val role: String, val content: String?) : RawLogEntry() {
        override fun charCount() = (content?.length ?: 0)
    }
    data class ToolCallEntry(val toolName: String, val id: String, val arguments: String) : RawLogEntry() {
        override fun charCount() = arguments.length
    }
    data class ToolResultEntry(val id: String, val result: String) : RawLogEntry() {
        override fun charCount() = result.length
    }
    data class ToolDefinitionsEntry(val toolDefs: List<ToolDefinition>) : RawLogEntry() {
        override fun charCount() = toolDefs.sumOf { it.name.length + it.description.length + 100 }
    }
    data class RawApiResponse(val iteration: Int, val body: String) : RawLogEntry() {
        override fun charCount() = body.length
    }
}
