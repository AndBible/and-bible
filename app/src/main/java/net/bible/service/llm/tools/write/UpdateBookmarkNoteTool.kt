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
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for updating an existing bookmark note.
 */
object UpdateBookmarkNoteTool : Tool {
    @Serializable
    data class Args(
        val bookmarkId: IdType = IdType.empty(),
        val note: String = ""
    )

    @Serializable
    data class Result(val bookmarkId: IdType, val noteLength: Int, val previousNoteLength: Int)

    override val agentTool = AgentTool.UPDATE_BOOKMARK_NOTE
    override val category = ToolCategory.BOOKMARKS

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
    override val displayNameResId = R.string.tool_update_bookmark_note

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val bookmarkId = arguments.optString("bookmarkId", "").takeIf { it.isNotBlank() } ?: return null
        val bookmark = try { bookmarkControl.bibleBookmarkById(IdType(bookmarkId)) } catch (_: Exception) { null }
        val verseName = bookmark?.verseRange?.name ?: shortId(bookmarkId)
        return BibleApplication.application.getString(R.string.action_update_note_on_bookmark_at, verseName)
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
        val note = normalizeLlmText(args.note)

        if (args.bookmarkId.isEmpty) {
            return ToolResult.error("Missing required parameter: bookmarkId")
        }
        if (note.isBlank()) {
            return ToolResult.error("Missing required parameter: note")
        }

        return try {
            // Check if bookmark exists
            val bookmark = bookmarkControl.bibleBookmarkById(args.bookmarkId)
                ?: return ToolResult.error("Bookmark not found: ${args.bookmarkId}", "BOOKMARK_NOT_FOUND")

            val previousNoteLength = bookmark.notes?.length ?: 0

            // Update note fields directly on bookmark (sets provenance and content type)
            bookmark.notes = note
            bookmark.notesContentType = TextContentType.MARKDOWN
            bookmark.notesSourcePromptId = context.promptId

            // Ensure AI label is present on the bookmark
            val existingLabelIds = bookmarkControl.labelsForBookmark(bookmark).map { it.id }.toSet()
            val aiLabelId = bookmarkControl.aiLabel.id
            val labels = if (aiLabelId !in existingLabelIds) existingLabelIds + aiLabelId else null

            bookmarkControl.addOrUpdateBibleBookmark(bookmark, labels = labels, updateNotes = true)

            typedSuccess(Result(
                bookmarkId = args.bookmarkId,
                noteLength = note.length,
                previousNoteLength = previousNoteLength
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to update note: ${e.message}", "UPDATE_ERROR")
        }
    }
}
