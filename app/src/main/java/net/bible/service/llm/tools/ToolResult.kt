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

import android.util.Log
import net.bible.service.llm.llmJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Result of a tool execution.
 */
sealed class ToolResult {
    /**
     * Successful tool execution with result data.
     *
     * @param data The result data — a JSONObject/JSONArray for legacy tools, or a @Serializable
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
    fun toJson(): String = when (this) {
        is Success -> {
            val result = JSONObject()
            result.put("status", "success")
            if (jsonOverride != null) {
                result.put("data", JSONObject(jsonOverride))
            } else when (data) {
                is JSONObject -> result.put("data", data)
                is JSONArray -> result.put("data", data)
                is String -> result.put("data", data)
                is Number -> result.put("data", data)
                is Boolean -> result.put("data", data)
                is List<*> -> result.put("data", JSONArray(data))
                is Map<*, *> -> result.put("data", JSONObject(data))
                else -> {
                    Log.w("ToolResult", "Unexpected data type in ToolResult: ${data::class.simpleName}")
                    result.put("data", data.toString())
                }
            }
            result.toString()
        }
        is Error -> {
            val result = JSONObject()
            result.put("status", "error")
            result.put("message", message)
            code?.let { result.put("code", it) }
            result.toString()
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
        fun success(builder: JSONObject.() -> Unit): ToolResult {
            val json = JSONObject()
            json.builder()
            return Success(json)
        }

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
