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
 * Tool for deleting a bookmark by ID.
 *
 * Supports both Bible bookmarks and generic bookmarks.
 */
object DeleteBookmarkTool : Tool {
    @Serializable
    data class Args(
        val bookmarkId: IdType = IdType.empty()
    )

    @Serializable
    data class Result(val bookmarkId: IdType, val verseName: String)

    override val agentTool = AgentTool.DELETE_BOOKMARK
    override val category = ToolCategory.BOOKMARKS

    override val description = """
        Delete a bookmark by its ID.
        This permanently removes the bookmark and its associated notes.
        Works for both Bible bookmarks and generic bookmarks.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          bookmarkId:
            type: string
            description: The ID of the bookmark to delete
        required: [bookmarkId]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_delete_bookmark

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        val bookmark = try {
            bookmarkControl.bibleBookmarkById(IdType(bookmarkId))
                ?: bookmarkControl.genericBookmarkById(IdType(bookmarkId))
        } catch (_: Exception) { null }
        val verseName = (bookmark as? BibleBookmarkWithNotes)?.verseRange?.name ?: shortId(bookmarkId)
        return BibleApplication.application.getString(R.string.action_delete_bookmark, verseName)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(bookmarkId)
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

        return try {
            // Try Bible bookmark first, then generic
            val bookmark = bookmarkControl.bibleBookmarkById(args.bookmarkId)
                ?: bookmarkControl.genericBookmarkById(args.bookmarkId)
                ?: return ToolResult.error("Bookmark not found: ${args.bookmarkId}", "BOOKMARK_NOT_FOUND")

            val verseName = (bookmark as? BibleBookmarkWithNotes)?.verseRange?.name ?: args.bookmarkId.toString()

            bookmarkControl.deleteBookmark(bookmark)

            typedSuccess(Result(
                bookmarkId = args.bookmarkId,
                verseName = verseName
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to delete bookmark: ${e.message}", "DELETE_ERROR")
        }
    }
}
