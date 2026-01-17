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

import net.bible.android.BibleApplication
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for updating an existing bookmark note.
 */
object UpdateBookmarkNoteTool : Tool {
    override val name = "updateBookmarkNote"

    override val description = """
        Update the note text of an existing bookmark.
        This replaces the entire note content. For appending, get the current note first and combine.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          bookmarkId:
            type: string
            description: The ID of the bookmark to update
          note:
            type: string
            description: The new note text
        required: [bookmarkId, note]
    """)

    override val requiresPermission = true

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val bookmarkIdStr = arguments.optString("bookmarkId", "")
        val note = arguments.optString("note", "")

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

            val previousNoteLength = bookmark.notes?.length ?: 0

            // Update note using BookmarkControl (sends UI events)
            bookmarkControl.saveBibleBookmarkNote(bookmarkId, note)

            ToolResult.success {
                put("bookmarkId", bookmarkIdStr)
                put("noteLength", note.length)
                put("previousNoteLength", previousNoteLength)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to update note: ${e.message}", "UPDATE_ERROR")
        }
    }
}
