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
import net.bible.service.llm.tools.stripMarkdownFromTitle
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for finishing the agent execution and saving the response as an AI document.
 *
 * This tool allows explicit control over the document title and content,
 * ensuring the title in the table of contents is clean (no markdown links)
 * while the content can include rich formatting with links.
 */
object SetDocumentTitleTool : Tool {
    @Serializable
    data class Args(val title: String = "")

    @Serializable
    data class Result(
        val finished: Boolean,
        val title: String
    )

    override val agentTool = AgentTool.SET_DOCUMENT_TITLE
    override val category = ToolCategory.MY_DOCUMENTS
    override val displayNameResId = R.string.tool_set_document_title

    override val description = """
        Set the title for your AI document and finish the task.

        You MUST use this tool to give your document a proper title.

        **How to use:**
        1. Output your complete markdown content as text in the same response
        2. Use this tool to set a short, plain text title (no markdown, no links)

        **CRITICAL:**
        - The title must be plain text only — NO markdown, NO links, NO formatting
        - Output content as TEXT in the same response, not as a tool argument
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          title:
            type: string
            description: "Plain text title for the document (shown in table of contents, max 60 chars, NO markdown)"
        required: [title]
    """)

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val title = arguments.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        return BibleApplication.application.getString(R.string.action_set_document_title, title)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val title = arguments.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        return "\"$title\""
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val title = stripMarkdownFromTitle(args.title).take(80)

        if (title.isBlank()) {
            return ToolResult.error("Title is required", "MISSING_TITLE")
        }

        return typedSuccess(Result(finished = true, title = title))
    }
}
