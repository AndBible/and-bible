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

package net.bible.service.llm.tools

import net.bible.sharedcore.log.Log
import net.bible.service.llm.llmJson
import kotlinx.serialization.json.*

/**
 * Result of a tool execution.
 */
sealed class ToolResult {
    /**
     * Successful tool execution with result data.
     *
     * @param data The result data — a JsonElement for legacy tools, or a @Serializable
     *   object for typed tools. Serialized to JSON via [toJson] for the LLM. Also used directly
     *   by AgentExecutor for finish tools (cast to the concrete Result type).
     * @param jsonOverride Pre-serialized JSON string of [data], set by [typedSuccess] to avoid
     *   reflection-based serialization in [toJson].
     */
    data class Success(val data: Any, internal val jsonOverride: String? = null) : ToolResult()

    /**
     * Failed tool execution.
     *
     * @param message Human-readable error message
     * @param code Optional error code for programmatic handling
     */
    data class Error(val message: String, val code: String? = null) : ToolResult()

    /**
     * Convert the result to a JSON string for returning to the LLM.
     */
    fun toJson(): String = buildJsonObject {
        when (val result = this@ToolResult) {
            is Success -> {
                put("status", "success")
                put("data", result.jsonOverride?.let { llmJson.parseToJsonElement(it) }
                    ?: toJsonElement(result.data))
            }
            is Error -> {
                put("status", "error")
                put("message", result.message)
                result.code?.let { put("code", it) }
            }
        }
    }.toString()

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        is Map<*, *> -> buildJsonObject {
            value.forEach { (key, item) -> put(key.toString(), toJsonElement(item)) }
        }
        else -> {
            Log.w("ToolResult", "Unexpected data type in ToolResult: ${value::class.simpleName}")
            JsonPrimitive(value.toString())
        }
    }

    companion object {
        /**
         * Create a success result with the given data.
         */
        fun success(data: Any): ToolResult = Success(data)

        /**
         * Create a success result with a JSON object.
         */
        fun success(builder: JsonObjectBuilder.() -> Unit): ToolResult = Success(buildJsonObject(builder))

        /**
         * Create an error result.
         */
        fun error(message: String, code: String? = null): ToolResult = Error(message, code)
    }
}

/**
 * Create a typed success result from a @Serializable object.
 * The object is stored as-is for direct access (e.g. by AgentExecutor)
 * and pre-serialized to JSON for [ToolResult.toJson].
 */
inline fun <reified T> typedSuccess(data: T & Any): ToolResult =
    ToolResult.Success(data = data, jsonOverride = llmJson.encodeToString(data))
