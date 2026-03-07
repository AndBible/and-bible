/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.TextContentType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for adding a text entry to a StudyPad.
 *
 * StudyPads (labels) can contain both bookmark references and standalone text entries.
 */
object AddStudyPadEntryTool : Tool {
    override val name = "addStudyPadEntry"

    override val description = """
        Add a text entry to a StudyPad (label).
        StudyPads can contain both bookmarks and standalone text notes.
        This creates a new text entry at the end of the StudyPad.
        The entry will be marked as AI-generated.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: The ID of the label/StudyPad to add the entry to
          text:
            type: string
            description: The text content of the entry
          contentType:
            type: string
            enum: [HTML, MARKDOWN]
            description: "Content type. Default: MARKDOWN for AI-generated content."
          orderNumber:
            type: integer
            description: "Optional position in the StudyPad. Default: add to end."
        required: [labelId, text]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_add_study_pad_entry

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val contentType = arguments.optString("contentType", "MARKDOWN")
        return "${shortId(labelId)} ($contentType)"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val labelIdStr = arguments.optString("labelId", "")
        val text = normalizeLlmText(arguments.optString("text", ""))
        val contentTypeStr = arguments.optString("contentType", "MARKDOWN")
        val orderNumber = if (arguments.has("orderNumber")) arguments.getInt("orderNumber") else null

        if (labelIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: labelId")
        }
        if (text.isBlank()) {
            return ToolResult.error("Missing required parameter: text")
        }

        return try {
            val labelId = IdType.fromString(labelIdStr)

            // Check if label exists
            val label = bookmarkControl.labelById(labelId)
                ?: return ToolResult.error("Label not found: $labelIdStr", "LABEL_NOT_FOUND")

            val contentType = try {
                TextContentType.valueOf(contentTypeStr)
            } catch (e: IllegalArgumentException) {
                TextContentType.MARKDOWN
            }

            // Create entry using BookmarkControl (sends UI events)
            // If orderNumber is null, BookmarkControl adds to end
            val entry = bookmarkControl.createStudyPadEntryWithText(
                labelId = labelId,
                orderNumber = orderNumber,
                text = text,
                contentType = contentType,
                sourcePromptId = context.promptId
            )

            ToolResult.success {
                put("entryId", entry.id.toString())
                put("labelId", labelIdStr)
                put("labelName", label.name)
                put("textLength", text.length)
                put("contentType", contentType.name)
                put("orderNumber", entry.orderNumber)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to add StudyPad entry: ${e.message}", "ADD_ERROR")
        }
    }
}
