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
import net.bible.android.database.bookmarks.BookmarkEntities.TextRange
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.TextContentType
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
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
        val labelIds: List<IdType>? = null,
        val primaryLabelId: IdType? = null,
        val bookInitials: String? = null,
        val startOffset: Int? = null,
        val endOffset: Int? = null
    )

    @Serializable
    data class Result(
        val id: IdType,
        val verseRef: String,
        val verseName: String,
        val hasNote: Boolean,
        val labelCount: Int
    )

    override val agentTool = AgentTool.CREATE_BOOKMARK
    override val category = ToolCategory.BOOKMARKS

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
          primaryLabelId:
            type: string
            description: "Optional label ID to set as primary. Must be one of the labelIds. Defaults to the first label in labelIds."
          bookInitials:
            type: string
            description: "Bible module initials (e.g., 'KJV', 'ESV'). Required for sub-verse bookmarks. Defaults to the active document if omitted."
          startOffset:
            type: integer
            description: "Character offset from the start of the verse text where the bookmark begins. 0 = start of verse. Offsets are specific to the bookInitials translation. Both startOffset and endOffset must be provided together for a sub-verse bookmark."
          endOffset:
            type: integer
            description: "Character offset from the start of the verse text where the bookmark ends. Offsets are specific to the bookInitials translation. Both startOffset and endOffset must be provided together for a sub-verse bookmark."
        required: [verseRef]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_create_bookmark

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val app = BibleApplication.application
        val verseRef = arguments.optString("verseRef", "").takeIf { it.isNotBlank() } ?: return null
        val verseName = localizeVerseRef(verseRef)
        val extras = mutableListOf<String>()
        if (arguments.has("note") && !arguments.isNull("note")) extras.add(app.getString(R.string.action_with_note))
        val labelIds = arguments.optJSONArray("labelIds")
        if (labelIds != null && labelIds.length() > 0) {
            val names = (0 until labelIds.length()).mapNotNull { i ->
                try { bookmarkControl.labelById(IdType(labelIds.getString(i)))?.name } catch (_: Exception) { null }
            }
            if (names.isNotEmpty()) extras.add(app.getString(R.string.action_labels, names.joinToString(", ")))
        }
        return if (extras.isEmpty()) {
            app.getString(R.string.action_create_bookmark_at, verseName)
        } else {
            app.getString(R.string.action_create_bookmark_at_with_extras, verseName, extras.joinToString(", "))
        }
    }

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
        if (result !is ToolResult.Success || result.data !is Result) return null
        return (result.data as Result).verseName.takeIf { it.isNotBlank() }
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

            // Resolve the Bible document (needed for sub-verse bookmarks with offsets)
            val bookInitials = args.bookInitials ?: context.activeDocumentInitials
            val swordBook = bookInitials?.let {
                Books.installed().getBook(it) as? SwordBook
            }

            // Create bookmark, with optional sub-verse offsets
            val hasOffsets = args.startOffset != null && args.endOffset != null
            val bookmark = BibleBookmarkWithNotes(
                verseRange = verseRange,
                textRange = if (hasOffsets) TextRange(args.startOffset!!, args.endOffset!!) else null,
                wholeVerse = !hasOffsets,
                book = swordBook
            )

            // Set AI source
            bookmark.sourcePromptId = context.promptId

            // Set note if provided
            if (note != null) {
                bookmark.notes = note
                bookmark.notesContentType = args.noteContentType
                bookmark.notesSourcePromptId = context.promptId
            }

            // Parse label IDs and always include AI label
            val aiLabelId = bookmarkControl.aiLabel.id
            val labelIds = if (!labelIdsList.isNullOrEmpty()) {
                labelIdsList.toSet() + aiLabelId
            } else {
                setOf(aiLabelId)
            }

            // Set primary label: explicit param > first user label > default (AI label via addOrUpdateBookmark)
            val desiredPrimary = args.primaryLabelId ?: labelIdsList?.firstOrNull()
            if (desiredPrimary != null && desiredPrimary in labelIds) {
                bookmark.primaryLabelId = desiredPrimary
            }

            // Save bookmark using BookmarkControl (sends UI events)
            val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(
                bookmark,
                labels = labelIds,
                updateNotes = true
            )

            typedSuccess(Result(
                id = savedBookmark.id,
                verseRef = verseRange.osisRef,
                verseName = verseRange.name,
                hasNote = note != null,
                labelCount = labelIds.size
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to create bookmark: ${e.message}", "CREATE_ERROR")
        }
    }
}
