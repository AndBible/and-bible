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
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for adding a label to a bookmark.
 *
 * Associates a bookmark with a label (category/StudyPad).
 */
object AddLabelToBookmarkTool : Tool {
    override val name = "addLabelToBookmark"

    override val description = """
        Add a label to an existing bookmark.
        This associates the bookmark with the label/StudyPad.
        A bookmark can have multiple labels.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          bookmarkId:
            type: string
            description: The ID of the bookmark
          labelId:
            type: string
            description: The ID of the label to add
        required: [bookmarkId, labelId]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_add_label_to_bookmark

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        return "${shortId(bookmarkId)} \u2192 ${shortId(labelId)}"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val bookmarkIdStr = arguments.optString("bookmarkId", "")
        val labelIdStr = arguments.optString("labelId", "")

        if (bookmarkIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: bookmarkId")
        }
        if (labelIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            val bookmarkId = IdType.fromString(bookmarkIdStr)
            val labelId = IdType.fromString(labelIdStr)

            // Check if bookmark exists
            val bookmark = bookmarkControl.bibleBookmarkById(bookmarkId)
                ?: return ToolResult.error("Bookmark not found: $bookmarkIdStr", "BOOKMARK_NOT_FOUND")

            // Check if label exists
            val label = bookmarkControl.labelById(labelId)
                ?: return ToolResult.error("Label not found: $labelIdStr", "LABEL_NOT_FOUND")

            // Check if already linked
            val existingLabels = bookmarkControl.labelsForBookmark(bookmark)
            if (existingLabels.any { it.id == labelId }) {
                return ToolResult.error("Bookmark already has this label", "ALREADY_LINKED")
            }

            // Add the label using BookmarkControl (sends UI events)
            val currentLabelIds = existingLabels.map { it.id }.toMutableSet()
            currentLabelIds.add(labelId)
            bookmarkControl.addOrUpdateBibleBookmark(bookmark, labels = currentLabelIds)

            ToolResult.success {
                put("bookmarkId", bookmarkIdStr)
                put("labelId", labelIdStr)
                put("labelName", label.name)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to add label: ${e.message}", "ADD_ERROR")
        }
    }
}
