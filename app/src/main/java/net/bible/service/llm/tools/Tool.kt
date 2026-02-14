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

package net.bible.service.llm.tools

import net.bible.service.llm.agent.AgentContext
import org.json.JSONObject

/**
 * Interface for agent tools that can be called by the LLM.
 *
 * Each tool represents a specific action the agent can perform,
 * such as reading verse content, searching, or creating bookmarks.
 */
interface Tool {
    /**
     * Unique name of the tool, used in OpenAI function calling.
     * Should be camelCase, e.g., "getVerseContent", "searchBible".
     */
    val name: String

    /**
     * Description of what this tool does, shown to the LLM.
     * Should be clear and concise to help the LLM decide when to use it.
     */
    val description: String

    /**
     * JSON Schema for the tool's parameters.
     * Used to validate arguments and shown to the LLM.
     *
     * Example:
     * ```json
     * {
     *   "type": "object",
     *   "properties": {
     *     "book": { "type": "string", "description": "Book initials, e.g., KJV" },
     *     "verseRef": { "type": "string", "description": "Verse reference, e.g., Matt.5.3" }
     *   },
     *   "required": ["book", "verseRef"]
     * }
     * ```
     */
    val parametersSchema: JSONObject

    /**
     * Whether this tool requires user permission before execution.
     * Read-only tools typically don't require permission (false).
     * Write tools (creating bookmarks, documents) should require permission (true).
     *
     * Note: Permission handling is implemented in a later phase.
     * For now, all tools execute without explicit permission.
     */
    val requiresPermission: Boolean
        get() = false

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments JSON object containing the tool arguments, matching parametersSchema
     * @param context Agent execution context with information about the current state
     * @return Result of the tool execution
     */
    suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult
}

/**
 * Normalize text content from LLM output.
 *
 * Some LLM providers return literal "\n" (backslash + n) in their JSON tool call
 * arguments instead of actual newline characters. This happens due to double-escaping
 * in the JSON arguments string. This function converts those literal escape sequences
 * back to actual characters.
 */
fun normalizeLlmText(text: String): String = text
    .replace("\\n", "\n")
    .replace("\\t", "\t")
