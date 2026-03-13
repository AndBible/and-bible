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
import net.bible.android.database.bookmarks.TextContentType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for adding a note to an existing bookmark.
 *
 * If the bookmark already has a note, this will fail - use updateBookmarkNote instead.
 */
object AddBookmarkNoteTool : Tool {
    override val name = "addBookmarkNote"

    override val description = """
        Add a note to an existing bookmark that doesn't have a note yet.
        If the bookmark already has a note, use updateBookmarkNote instead.
        The note will be marked as AI-generated.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          bookmarkId:
            type: string
            description: The ID of the bookmark to add the note to
          note:
            type: string
            description: The note text to add
          contentType:
            type: string
            enum: [HTML, MARKDOWN]
            description: "Content type of the note. Default: MARKDOWN for AI-generated content."
        required: [bookmarkId, note]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_add_bookmark_note

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(bookmarkId)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val bookmarkIdStr = arguments.optString("bookmarkId", "")
        val note = normalizeLlmText(arguments.optString("note", ""))
        val contentTypeStr = arguments.optString("contentType", "MARKDOWN")

        if (bookmarkIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: bookmarkId")
        }
        if (note.isBlank()) {
            return ToolResult.error("Missing required parameter: note")
        }

        return try {
            val bookmarkId = IdType.fromString(bookmarkIdStr)

            // Check if bookmark exists
            val bookmark = bookmarkControl.bibleBookmarkById(bookmarkId)
                ?: return ToolResult.error("Bookmark not found: $bookmarkIdStr", "BOOKMARK_NOT_FOUND")

            // Check if note already exists
            if (bookmark.notes != null) {
                return ToolResult.error(
                    "Bookmark already has a note. Use updateBookmarkNote to modify it.",
                    "NOTE_EXISTS"
                )
            }

            val contentType = try {
                TextContentType.valueOf(contentTypeStr)
            } catch (e: IllegalArgumentException) {
                TextContentType.MARKDOWN
            }

            // Set note fields directly on bookmark (noteEntity is computed from these)
            bookmark.notes = note
            bookmark.notesContentType = contentType
            bookmark.notesSourcePromptId = context.promptId

            // Save using BookmarkControl (sends UI events)
            bookmarkControl.addOrUpdateBibleBookmark(bookmark, updateNotes = true)

            ToolResult.success {
                put("bookmarkId", bookmarkIdStr)
                put("noteLength", note.length)
                put("contentType", contentType.name)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to add note: ${e.message}", "ADD_ERROR")
        }
    }
}
