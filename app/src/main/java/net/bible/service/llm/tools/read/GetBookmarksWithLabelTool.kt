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

package net.bible.service.llm.tools.read

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
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
 * Tool for getting all bookmarks with a specific label.
 *
 * Useful for retrieving the contents of a StudyPad or all bookmarks in a category.
 */
object GetBookmarksWithLabelTool : Tool {
    @Serializable
    data class Args(
        val labelId: IdType = IdType.empty(),
        val maxResults: Int = 100,
        val fields: List<String>? = null,
    )

    @Serializable
    data class BookmarkEntry(
        val id: IdType,
        val type: String,
        val verseRange: String? = null,
        val verseName: String? = null,
        val notes: String? = null,
        val createdAt: Long? = null,
        val book: String? = null,
        val key: String? = null
    )

    @Serializable
    data class Result(
        val labelId: IdType,
        val labelName: String,
        val bookmarkCount: Int,
        val bookmarks: List<BookmarkEntry>
    )

    override val agentTool = AgentTool.GET_BOOKMARKS_WITH_LABEL
    override val category = ToolCategory.BOOKMARKS
    override val displayNameResId = R.string.tool_get_bookmarks_with_label

    override val description = """
        Get all bookmarks that have a specific label assigned.
        A label acts as a StudyPad - a collection of related bookmarks and notes.
        Use getAllLabels first to find available labels and their IDs.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: The ID of the label to query. Get label IDs from getAllLabels.
          maxResults:
            type: integer
            description: "Maximum number of bookmarks to return (default: 100)"
          fields:
            type: array
            items:
              type: string
              enum: [verseRange, verseName, notes, createdAt]
            description: "Fields to include in the response. Default: [verseRange, verseName, createdAt]. Notes can be large, only include if needed."
        required: [labelId]
    """)

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(labelId)
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        return application.getString(R.string.tool_log_bookmark_count, (result.data as Result).bookmarkCount)
    }

    private val defaultFields = setOf("verseRange", "verseName", "createdAt")

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val maxResults = args.maxResults
        val fields = if (!args.fields.isNullOrEmpty()) {
            args.fields.toSet()
        } else {
            defaultFields
        }

        if (args.labelId.isEmpty) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            val label = dao.labelById(args.labelId)
                ?: return ToolResult.error("Label not found: ${args.labelId}", "LABEL_NOT_FOUND")

            // Get Bible bookmarks with this label
            val bibleBookmarks = dao.bookmarksWithLabel(label)
            // Get generic bookmarks with this label
            val genericBookmarks = dao.genericBookmarksWithLabel(label)

            val results = mutableListOf<BookmarkEntry>()

            for (bookmark in bibleBookmarks) {
                if (results.size >= maxResults) break
                results.add(BookmarkEntry(
                    id = bookmark.id,
                    type = "bible",
                    verseRange = if ("verseRange" in fields) bookmark.verseRange.osisRef else null,
                    verseName = if ("verseName" in fields) bookmark.verseRange.name else null,
                    notes = if ("notes" in fields) bookmark.notes else null,
                    createdAt = if ("createdAt" in fields) bookmark.createdAt.time else null
                ))
            }

            for (bookmark in genericBookmarks) {
                if (results.size >= maxResults) break
                results.add(BookmarkEntry(
                    id = bookmark.id,
                    type = "generic",
                    book = bookmark.book?.initials ?: "unknown",
                    key = bookmark.key,
                    notes = if ("notes" in fields) bookmark.notes else null,
                    createdAt = if ("createdAt" in fields) bookmark.createdAt.time else null
                ))
            }

            typedSuccess(Result(
                labelId = args.labelId,
                labelName = label.name,
                bookmarkCount = results.size,
                bookmarks = results
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to get bookmarks: ${e.message}", "READ_ERROR")
        }
    }
}
