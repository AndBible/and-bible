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
import net.bible.android.control.bookmark.BookmarkToLabelAddedOrUpdatedEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.TextContentType
import net.bible.android.database.bookmarks.defaultLabelColor
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.uniqueLabelName
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.json.JSONObject

/**
 * Tool for creating a complete StudyPad in a single call.
 *
 * Creates a label and populates it with an ordered sequence of text entries
 * and Bible bookmark references, allowing the LLM to build a structured
 * study document in one operation.
 */
object CreateStudyPadTool : Tool {
    @Serializable
    enum class ItemType {
        @SerialName("text") TEXT,
        @SerialName("bookmark") BOOKMARK
    }

    @Serializable
    data class ItemArgs(
        val type: ItemType = ItemType.TEXT,
        val text: String? = null,
        val verseRef: String? = null,
        val indentLevel: Int = 0,
        val contentType: TextContentType = TextContentType.MARKDOWN
    )

    @Serializable
    data class Args(
        val name: String = "",
        val color: Int = 0,
        val items: List<ItemArgs> = emptyList()
    )

    @Serializable
    data class ItemError(
        val index: Int,
        val type: ItemType,
        val message: String
    )

    @Serializable
    data class Result(
        val labelId: IdType,
        val labelName: String,
        val itemsCreated: Int,
        val textEntries: Int,
        val bookmarkEntries: Int,
        val errors: List<ItemError> = emptyList()
    )

    override val agentTool = AgentTool.CREATE_STUDY_PAD
    override val category = ToolCategory.STUDY_PADS

    override val description = """
        Create a complete StudyPad with text entries and Bible verse bookmarks in a single call.
        This is the preferred way to create a StudyPad — it creates the label and all items at once.
        Items are displayed in the order they appear in the array.
        Each item is either a text entry (markdown/HTML) or a bookmark to a Bible verse (with optional note).
        After creating the StudyPad, call finishWithStudyPad with the returned labelId to open it.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          name:
            type: string
            description: Name for the StudyPad
          color:
            type: integer
            description: "Optional ARGB color integer. Default: blue highlight color."
          items:
            type: array
            description: "Ordered list of items. Each is either a text entry or a bookmark."
            items:
              type: object
              properties:
                type:
                  type: string
                  enum: [text, bookmark]
                  description: "'text' for a markdown/HTML entry, 'bookmark' for a Bible verse reference"
                text:
                  type: string
                  description: "Text content (required for type=text, optional note for type=bookmark)"
                verseRef:
                  type: string
                  description: "OSIS verse reference, e.g. 'Matt.5.3' or 'Gen.1.1-3' (required for type=bookmark)"
                indentLevel:
                  type: integer
                  description: "Indent level for hierarchy (0-3). Default: 0"
                contentType:
                  type: string
                  enum: [HTML, MARKDOWN]
                  description: "Content type for text or bookmark note. Default: MARKDOWN"
              required: [type]
        required: [name, items]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_create_study_pad

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val name = arguments.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        val items = arguments.optJSONArray("items")
        val count = items?.length() ?: 0
        return BibleApplication.application.getString(R.string.action_create_studypad, name, count)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val name = arguments.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        val items = arguments.optJSONArray("items")
        val count = items?.length() ?: 0
        return "\"$name\" ($count items)"
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val r = result.data
        val parts = mutableListOf("\"${r.labelName}\"")
        if (r.textEntries > 0) parts.add("${r.textEntries} text")
        if (r.bookmarkEntries > 0) parts.add("${r.bookmarkEntries} bookmarks")
        if (r.errors.isNotEmpty()) parts.add("${r.errors.size} errors")
        return parts.joinToString(", ")
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        val name = args.name.trim()
        if (name.isBlank()) {
            return ToolResult.error("Missing required parameter: name")
        }
        if (args.items.isEmpty()) {
            return ToolResult.error("Items array must not be empty", "EMPTY_ITEMS")
        }

        // Generate unique name if needed
        val existingNames = bookmarkControl.assignableLabels.map { it.name }
        val uniqueName = uniqueLabelName(name, existingNames)

        // Create the label (insertOrUpdateLabel sends LabelAddedOrUpdatedEvent)
        val color = if (args.color != 0) args.color else defaultLabelColor
        val label = try {
            bookmarkControl.insertOrUpdateLabel(Label(name = uniqueName, color = color, new = true))
        } catch (e: Exception) {
            return ToolResult.error("Failed to create label: ${e.message}", "LABEL_CREATE_ERROR")
        }

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val aiLabelId = bookmarkControl.aiLabel.id
        val errors = mutableListOf<ItemError>()
        var textCount = 0
        var bookmarkCount = 0

        for ((index, item) in args.items.withIndex()) {
            try {
                when (item.type) {
                    ItemType.TEXT -> {
                        val text = normalizeLlmText(item.text ?: "")
                        if (text.isBlank()) {
                            errors.add(ItemError(index, ItemType.TEXT, "Empty text content"))
                            continue
                        }
                        bookmarkControl.createStudyPadEntryWithText(
                            labelId = label.id,
                            orderNumber = index,
                            text = text,
                            contentType = item.contentType,
                            sourcePromptId = context.promptId,
                            indentLevel = item.indentLevel
                        )
                        textCount++
                    }
                    ItemType.BOOKMARK -> {
                        val verseRef = item.verseRef
                        if (verseRef.isNullOrBlank()) {
                            errors.add(ItemError(index, ItemType.BOOKMARK, "Missing verseRef"))
                            continue
                        }

                        // Parse verse reference
                        val key = PassageKeyFactory.instance().getKey(KJVA, verseRef)
                        val verseRange = key.getRangeAt(0, RestrictionType.NONE)
                            ?: throw IllegalArgumentException("Invalid verse reference: $verseRef")

                        // Resolve the active Bible translation so study pad shows correct version
                        val swordBook = context.activeDocumentInitials?.let {
                            Books.installed().getBook(it) as? SwordBook
                        }

                        // Create bookmark with AI label only (not the StudyPad label)
                        val bookmark = BibleBookmarkWithNotes(
                            verseRange = verseRange,
                            textRange = null,
                            wholeVerse = true,
                            book = swordBook
                        )
                        bookmark.sourcePromptId = context.promptId

                        // Set note if provided
                        val noteText = item.text?.let { normalizeLlmText(it) }
                        if (!noteText.isNullOrBlank()) {
                            bookmark.notes = noteText
                            bookmark.notesContentType = item.contentType
                            bookmark.notesSourcePromptId = context.promptId
                        }

                        // Save bookmark with AI label
                        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(
                            bookmark,
                            labels = setOf(aiLabelId),
                            updateNotes = true
                        )

                        // Link to StudyPad label with exact orderNumber and indentLevel
                        val btl = BibleBookmarkToLabel(
                            bookmarkId = savedBookmark.id,
                            labelId = label.id,
                            orderNumber = index,
                            indentLevel = item.indentLevel
                        )
                        dao.insert(btl)
                        ABEventBus.post(BookmarkToLabelAddedOrUpdatedEvent(btl))

                        // Set StudyPad label as primary (instead of AI label)
                        savedBookmark.primaryLabelId = label.id
                        dao.update(savedBookmark.bookmarkEntity)

                        bookmarkCount++
                    }
                }
            } catch (e: Exception) {
                errors.add(ItemError(index, item.type, e.message ?: "Unknown error"))
            }
        }

        return typedSuccess(Result(
            labelId = label.id,
            labelName = label.name,
            itemsCreated = textCount + bookmarkCount,
            textEntries = textCount,
            bookmarkEntries = bookmarkCount,
            errors = errors
        ))
    }
}
