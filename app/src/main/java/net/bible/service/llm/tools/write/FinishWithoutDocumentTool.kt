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

package net.bible.service.llm.tools.write

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for finishing the agent execution without creating an AI document.
 *
 * Use this tool when the task is complete and no document output is needed,
 * for example after creating a bookmark or performing an action that doesn't
 * require a written response.
 */
object FinishWithoutDocumentTool : Tool {
    @Serializable
    data class Args(val message: String = "")

    @Serializable
    data class Result(
        val finished: Boolean,
        val message: String,
        val marker: String
    )

    override val agentTool = AgentTool.FINISH_WITHOUT_DOCUMENT
    override val category = ToolCategory.WINDOWS
    override val displayNameResId = R.string.tool_finish_without_document

    override val description = """
        Finish the current task without creating an AI document.
        Use this when you have completed an action (like creating a bookmark, adding a label, etc.)
        and there is no need to generate a document with your response.

        Call this tool as your final action when:
        - You've successfully completed a task that doesn't need a written explanation
        - The user asked for a simple action (bookmark, label, note) not an analysis
        - You want to confirm the action was completed without opening a new document
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          message:
            type: string
            description: "A brief message confirming what was done (shown in the agent log)"
        required: [message]
    """)

    /** Marker to indicate this tool was called - checked by AgentExecutor */
    const val FINISH_WITHOUT_DOCUMENT_MARKER = "__FINISH_WITHOUT_DOCUMENT__"

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val message = arguments.optString("message", "").takeIf { it.isNotBlank() } ?: return null
        val truncated = if (message.length > 60) "${message.take(60)}..." else message
        return BibleApplication.application.getString(R.string.action_finish, truncated)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val message = arguments.optString("message", "").takeIf { it.isNotBlank() } ?: return null
        return if (message.length > 60) "\"${message.take(60)}...\"" else "\"$message\""
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val message = args.message.ifBlank { BibleApplication.application.getString(R.string.llm_default_task_completed) }

        return typedSuccess(Result(finished = true, message = message, marker = FINISH_WITHOUT_DOCUMENT_MARKER))
    }
}
