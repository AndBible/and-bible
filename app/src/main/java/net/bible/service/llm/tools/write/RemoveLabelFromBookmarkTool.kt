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
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for removing a label from a bookmark without deleting either.
 *
 * Supports both Bible bookmarks and generic bookmarks.
 */
object RemoveLabelFromBookmarkTool : Tool {
    @Serializable
    data class Args(
        val bookmarkId: IdType = IdType.empty(),
        val labelId: IdType = IdType.empty()
    )

    @Serializable
    data class Result(val bookmarkId: IdType, val labelId: IdType, val labelName: String)

    override val agentTool = AgentTool.REMOVE_LABEL_FROM_BOOKMARK
    override val category = ToolCategory.LABELS

    override val description = """
        Remove a label from a bookmark without deleting either.
        This only removes the association between the bookmark and the label.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          bookmarkId:
            type: string
            description: The ID of the bookmark
          labelId:
            type: string
            description: The ID of the label to remove
        required: [bookmarkId, labelId]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_remove_label_from_bookmark

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val bookmark = try {
            bookmarkControl.bibleBookmarkById(IdType(bookmarkId))
                ?: bookmarkControl.genericBookmarkById(IdType(bookmarkId))
        } catch (_: Exception) { null }
        val label = try { bookmarkControl.labelById(IdType(labelId)) } catch (_: Exception) { null }
        val verseName = (bookmark as? BibleBookmarkWithNotes)?.verseRange?.name ?: shortId(bookmarkId)
        val labelName = label?.name ?: shortId(labelId)
        return BibleApplication.application.getString(R.string.action_remove_label_from_bookmark, labelName, verseName)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        return "${shortId(bookmarkId)} \u2190 ${shortId(labelId)}"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.bookmarkId.isEmpty) {
            return ToolResult.error("Missing required parameter: bookmarkId")
        }
        if (args.labelId.isEmpty) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            // Check if label exists
            val label = bookmarkControl.labelById(args.labelId)
                ?: return ToolResult.error("Label not found: ${args.labelId}", "LABEL_NOT_FOUND")

            // Try Bible bookmark first, then generic
            val bookmark: BaseBookmarkWithNotes = bookmarkControl.bibleBookmarkById(args.bookmarkId)
                ?: bookmarkControl.genericBookmarkById(args.bookmarkId)
                ?: return ToolResult.error("Bookmark not found: ${args.bookmarkId}", "BOOKMARK_NOT_FOUND")

            // Check if bookmark has this label
            val existingLabels = bookmarkControl.labelsForBookmark(bookmark)
            if (existingLabels.none { it.id == args.labelId }) {
                return ToolResult.error("Bookmark does not have this label", "NOT_LINKED")
            }

            // Remove the label based on bookmark type
            if (bookmarkControl.bibleBookmarkById(args.bookmarkId) != null) {
                bookmarkControl.removeBibleBookmarkLabel(args.bookmarkId, args.labelId)
            } else {
                bookmarkControl.removeGenericBookmarkLabel(args.bookmarkId, args.labelId)
            }

            typedSuccess(Result(
                bookmarkId = args.bookmarkId,
                labelId = args.labelId,
                labelName = label.name
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to remove label from bookmark: ${e.message}", "REMOVE_ERROR")
        }
    }
}
