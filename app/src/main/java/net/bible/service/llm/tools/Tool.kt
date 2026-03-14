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

import kotlinx.serialization.json.JsonObject
import net.bible.service.llm.AgentTool
import net.bible.service.llm.agent.AgentContext
import org.json.JSONObject

/**
 * Interface for agent tools that can be called by the LLM.
 *
 * Each tool represents a specific action the agent can perform,
 * such as reading verse content, searching, or creating bookmarks.
 */
interface Tool {
    /** The AgentTool enum value identifying this tool. */
    val agentTool: AgentTool

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
    val parametersSchema: JsonObject

    /**
     * Whether this tool requires user permission before execution.
     * Read-only tools typically don't require permission (false).
     * Write tools (creating bookmarks, documents) should require permission (true).
     */
    val requiresPermission: Boolean
        get() = false

    /**
     * String resource ID for the user-facing display name of this tool.
     * Used in permission dialogs and settings UI.
     * Return 0 to use the tool's code name as fallback.
     */
    val displayNameResId: Int
        get() = 0

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments JSON object containing the tool arguments, matching parametersSchema
     * @param context Agent execution context with information about the current state
     * @return Result of the tool execution
     */
    suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult

    /**
     * Format tool arguments for human-readable display in the agent log.
     * Return null to fall back to the generic [formatJsonForLog] formatter.
     */
    fun formatArgsForLog(arguments: JSONObject): String? = null

    /**
     * Format tool result for human-readable display in the agent log.
     * Return null to fall back to the generic [formatJsonForLog] formatter.
     */
    fun formatResultForLog(result: ToolResult): String? = null
}

