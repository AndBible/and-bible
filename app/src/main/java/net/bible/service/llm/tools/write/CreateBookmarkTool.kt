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
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.TextContentType
import net.bible.service.llm.AgentTool
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.VerseRange
import org.json.JSONObject

/**
 * Tool for creating a new bookmark.
 *
 * Creates a bookmark at the specified verse reference with optional note and labels.
 * The sourcePromptId is automatically set to link the bookmark to the AI prompt.
 */
object CreateBookmarkTool : Tool {
    @Serializable
    data class Args(
        val verseRef: String = "",
        val note: String? = null,
        val noteContentType: TextContentType = TextContentType.MARKDOWN,
        val labelIds: List<IdType>? = null
    )

    override val agentTool = AgentTool.CREATE_BOOKMARK

    override val description = """
        Create a new bookmark at a verse or verse range.
        Bookmarks can include notes and be assigned to labels (categories/StudyPads).
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          verseRef:
            type: string
            description: "OSIS verse reference, e.g., 'Matt.5.3' or 'Gen.1.1-3'"
          note:
            type: string
            description: Optional note text to attach to the bookmark
          noteContentType:
            type: string
            enum: [HTML, MARKDOWN]
            description: "Content type of the note. Default: MARKDOWN"
          labelIds:
            type: array
            items:
              type: string
            description: Optional list of label IDs to assign to the bookmark. Get IDs from getAllLabels.
        required: [verseRef]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_create_bookmark

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val verseRef = arguments.optString("verseRef", "").takeIf { it.isNotBlank() } ?: return null
        val verseName = localizeVerseRef(verseRef)
        val extras = mutableListOf<String>()
        if (arguments.has("note") && !arguments.isNull("note")) extras.add("+note")
        val labelIds = arguments.optJSONArray("labelIds")
        if (labelIds != null && labelIds.length() > 0) extras.add("+${labelIds.length()} labels")
        return if (extras.isEmpty()) verseName else "$verseName (${extras.joinToString(", ")})"
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is JSONObject) return null
        val data = result.data as JSONObject
        return data.optString("verseName", "").takeIf { it.isNotBlank() }
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val verseRef = args.verseRef
        val note = args.note?.let { normalizeLlmText(it) }
        val labelIdsList = args.labelIds

        if (verseRef.isBlank()) {
            return ToolResult.error("Missing required parameter: verseRef")
        }

        return try {
            // Parse verse reference
            val key = PassageKeyFactory.instance().getKey(KJVA, verseRef)
            val verseRange = key.getRangeAt(0, RestrictionType.NONE)
                ?: return ToolResult.error("Invalid verse reference: $verseRef", "INVALID_REFERENCE")

            // Create bookmark (always whole verse for LLM-created bookmarks)
            val bookmark = BibleBookmarkWithNotes(
                verseRange = verseRange,
                textRange = null,
                wholeVerse = true,
                book = null
            )

            // Set AI source
            bookmark.sourcePromptId = context.promptId

            // Set note if provided
            if (note != null) {
                bookmark.notes = note
                bookmark.notesContentType = args.noteContentType
                bookmark.notesSourcePromptId = context.promptId
            }

            // Parse label IDs
            val labelIds = if (!labelIdsList.isNullOrEmpty()) {
                labelIdsList.toSet()
            } else {
                null
            }

            // Save bookmark using BookmarkControl (sends UI events)
            val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(
                bookmark,
                labels = labelIds,
                updateNotes = true
            )

            ToolResult.success {
                put("id", savedBookmark.id.toString())
                put("verseRef", verseRange.osisRef)
                put("verseName", verseRange.name)
                put("hasNote", note != null)
                put("labelCount", labelIds?.size ?: 0)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to create bookmark: ${e.message}", "CREATE_ERROR")
        }
    }
}
