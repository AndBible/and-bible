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

package net.bible.service.llm.tools.write

import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for finishing the agent execution and saving the response as an AI document.
 *
 * This tool allows explicit control over the document title and content,
 * ensuring the title in the table of contents is clean (no markdown links)
 * while the content can include rich formatting with links.
 */
object FinishWithDocumentTool : Tool {
    override val name = "finishWithDocument"

    override val description = """
        Finish the current task and save the response as an AI document.

        Use this tool as your FINAL action when you want to provide a written response.
        This tool explicitly separates the title (for table of contents) from the content.

        The title should be:
        - Plain text (no markdown formatting or links)
        - Short and descriptive (max 60 characters)
        - Example: "Romans 8:28 - God's Promise"

        The content should be:
        - Full markdown with formatting and Bible verse links
        - Include a title as markdown H1 that CAN have links
        - Example: "# [Romans 8:28](sword:///Rom.8.28) - God's Promise\n\nAnalysis..."
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          title:
            type: string
            description: "Plain text title for the document (shown in table of contents, max 60 chars, NO markdown)"
          content:
            type: string
            description: "Full markdown content including a title heading with optional links"
        required: [title, content]
    """)

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val title = arguments.optString("title", "").take(80)
        val content = arguments.optString("content", "")

        if (title.isBlank()) {
            return ToolResult.error("Title is required", "MISSING_TITLE")
        }
        if (content.isBlank()) {
            return ToolResult.error("Content is required", "MISSING_CONTENT")
        }

        return ToolResult.success {
            put("finished", true)
            put("title", title)
            put("content", content)
        }
    }
}
