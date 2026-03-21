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

import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
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
import org.json.JSONObject

/**
 * Tool for getting the content of a StudyPad with multiple read modes.
 *
 * Supports four modes:
 * - full: all entries with full content (default, backwards compatible)
 * - info: metadata only (entry counts, estimated text length)
 * - index: lightweight overview (type, position, preview)
 * - page: paginated full content with offset/limit
 */
object GetStudyPadContentTool : Tool {
    @Serializable
    data class Args(
        val labelId: IdType = IdType.empty(),
        val mode: String = "full",
        val offset: Int = 0,
        val limit: Int = 20,
    )

    @Serializable
    data class StudyPadEntry(
        val type: String,
        val id: IdType,
        val orderNumber: Int? = null,
        val position: Int? = null,
        val text: String? = null,
        val contentType: String? = null,
        val preview: String? = null,
        val verseRange: String? = null,
        val verseName: String? = null,
        val notes: String? = null,
        val hasNotes: Boolean? = null,
        val book: String? = null,
        val key: String? = null
    )

    @Serializable
    data class InfoResult(
        val labelId: IdType,
        val labelName: String,
        val totalEntries: Int,
        val textEntryCount: Int,
        val bibleBookmarkCount: Int,
        val genericBookmarkCount: Int,
        val estimatedTextLength: Long
    )

    @Serializable
    data class EntriesResult(
        val labelId: IdType,
        val labelName: String,
        val entryCount: Int,
        val entries: List<StudyPadEntry>
    )

    @Serializable
    data class IndexResult(
        val labelId: IdType,
        val labelName: String,
        val totalEntries: Int,
        val entries: List<StudyPadEntry>
    )

    @Serializable
    data class PageResult(
        val labelId: IdType,
        val labelName: String,
        val totalEntries: Int,
        val offset: Int,
        val limit: Int,
        val hasMore: Boolean,
        val entries: List<StudyPadEntry>
    )

    override val agentTool = AgentTool.GET_STUDY_PAD_CONTENT
    override val category = ToolCategory.STUDY_PADS
    override val displayNameResId = R.string.tool_get_study_pad_content

    override val description = """
        Get the content of a StudyPad (label).
        StudyPads contain ordered entries: text notes and bookmark references.
        Supports multiple read modes:
        - 'info': metadata only (entry counts, estimated size). Use first to check StudyPad size.
        - 'index': lightweight overview with type, position, and ~80-char preview for each entry.
        - 'page': paginated full content (use offset/limit to read in chunks).
        - 'full': all entries with full content (default). May be large for big StudyPads.
        Use 'info' first, then depending on size, 'full' or 'index' / 'page' to read selectively.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: The ID of the label/StudyPad. Get label IDs from getAllLabels.
          mode:
            type: string
            enum: [full, info, index, page]
            description: >
              'info': metadata only (entry counts, size).
              'index': lightweight overview (type, position, preview).
              'page': paginated full content (use offset/limit).
              'full': all entries with full content (default).
          offset:
            type: integer
            description: Start position (0-based). Only used in 'page' mode.
          limit:
            type: integer
            description: Max entries to return. Only used in 'page' mode. Default 20.
        required: [labelId]
    """)

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val mode = arguments.optString("mode", "full")
        return "${shortId(labelId)} ($mode)"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.labelId.isEmpty) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            val label = dao.labelById(args.labelId)
                ?: return ToolResult.error("Label not found: ${args.labelId}", "LABEL_NOT_FOUND")

            when (args.mode) {
                "info" -> executeInfo(args.labelId, label)
                "index" -> executeIndex(args.labelId, label)
                "page" -> executePage(args.labelId, label, args)
                else -> executeFull(args.labelId, label)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to get StudyPad content: ${e.message}", "READ_ERROR")
        }
    }

    // --- Full mode ---

    private fun loadAllEntries(labelId: IdType): List<StudyPadEntry> {
        val textEntries = dao.studyPadTextEntriesByLabelId(labelId)
        val bibleBookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId)
        val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId)

        data class OrderedEntry(val orderNumber: Int, val entry: StudyPadEntry)
        val orderedEntries = mutableListOf<OrderedEntry>()

        for (entry in textEntries) {
            orderedEntries.add(OrderedEntry(entry.orderNumber, StudyPadEntry(
                type = "text",
                id = entry.id,
                orderNumber = entry.orderNumber,
                text = entry.text,
                contentType = entry.contentType?.name ?: "HTML"
            )))
        }

        for (btl in bibleBookmarkToLabels) {
            val bookmark = dao.bibleBookmarkById(btl.bookmarkId) ?: continue
            orderedEntries.add(OrderedEntry(btl.orderNumber, StudyPadEntry(
                type = "bibleBookmark",
                id = bookmark.id,
                orderNumber = btl.orderNumber,
                verseRange = bookmark.verseRange.osisRef,
                verseName = bookmark.verseRange.name,
                notes = bookmark.notes
            )))
        }

        for (btl in genericBookmarkToLabels) {
            val bookmark = dao.genericBookmarkById(btl.bookmarkId) ?: continue
            orderedEntries.add(OrderedEntry(btl.orderNumber, StudyPadEntry(
                type = "genericBookmark",
                id = bookmark.id,
                orderNumber = btl.orderNumber,
                book = bookmark.book?.initials ?: "unknown",
                key = bookmark.key,
                notes = bookmark.notes
            )))
        }

        orderedEntries.sortBy { it.orderNumber }
        return orderedEntries.map { it.entry }
    }

    private fun executeFull(labelId: IdType, label: BookmarkEntities.Label): ToolResult {
        val entries = loadAllEntries(labelId)
        return typedSuccess(EntriesResult(
            labelId = labelId,
            labelName = label.name,
            entryCount = entries.size,
            entries = entries
        ))
    }

    // --- Info mode ---

    private fun executeInfo(labelId: IdType, label: BookmarkEntities.Label): ToolResult {
        val textEntryCount = dao.countStudyPadTextEntities(labelId)
        val bibleBookmarkCount = dao.countBookmarkEntities(labelId)
        val genericBookmarkCount = dao.countGenericBookmarkEntities(labelId)
        val totalEntries = textEntryCount + bibleBookmarkCount + genericBookmarkCount

        val textLength = dao.estimateStudyPadTextLength(labelId)
        val bibleNotesLength = dao.estimateBibleBookmarkNotesLength(labelId)
        val genericNotesLength = dao.estimateGenericBookmarkNotesLength(labelId)

        return typedSuccess(InfoResult(
            labelId = labelId,
            labelName = label.name,
            totalEntries = totalEntries,
            textEntryCount = textEntryCount,
            bibleBookmarkCount = bibleBookmarkCount,
            genericBookmarkCount = genericBookmarkCount,
            estimatedTextLength = textLength + bibleNotesLength + genericNotesLength
        ))
    }

    // --- Index mode ---

    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    private fun makePreview(text: String?, maxLength: Int = 80): String {
        if (text.isNullOrBlank()) return ""
        val stripped = text.replace(HTML_TAG_REGEX, "").trim()
        return if (stripped.length <= maxLength) stripped
        else stripped.substring(0, maxLength) + "..."
    }

    private sealed class EntryStub(val orderNumber: Int) {
        class Text(orderNumber: Int, val id: IdType) : EntryStub(orderNumber)
        class BibleBookmark(orderNumber: Int, val bookmarkId: IdType) : EntryStub(orderNumber)
        class GenericBookmark(orderNumber: Int, val bookmarkId: IdType) : EntryStub(orderNumber)
    }

    private fun loadEntryStubs(labelId: IdType): List<EntryStub> {
        val stubs = mutableListOf<EntryStub>()
        dao.studyPadTextEntryStubs(labelId).mapTo(stubs) { EntryStub.Text(it.orderNumber, it.id) }
        dao.bibleBookmarkToLabelStubs(labelId).mapTo(stubs) { EntryStub.BibleBookmark(it.orderNumber, it.bookmarkId) }
        dao.genericBookmarkToLabelStubs(labelId).mapTo(stubs) { EntryStub.GenericBookmark(it.orderNumber, it.bookmarkId) }
        stubs.sortBy { it.orderNumber }
        return stubs
    }

    private fun executeIndex(labelId: IdType, label: BookmarkEntities.Label): ToolResult {
        val stubs = loadEntryStubs(labelId)

        val textIds = stubs.filterIsInstance<EntryStub.Text>().map { it.id }
        val bibleIds = stubs.filterIsInstance<EntryStub.BibleBookmark>().map { it.bookmarkId }
        val genericIds = stubs.filterIsInstance<EntryStub.GenericBookmark>().map { it.bookmarkId }

        val textsById = if (textIds.isNotEmpty()) dao.studyPadTextEntriesByIds(textIds).associateBy { it.id } else emptyMap()
        val bibleById = if (bibleIds.isNotEmpty()) dao.bibleBookmarksByIds(bibleIds).associateBy { it.id } else emptyMap()
        val genericById = if (genericIds.isNotEmpty()) dao.genericBookmarksByIds(genericIds).associateBy { it.id } else emptyMap()

        val entries = stubs.mapIndexedNotNull { position, stub ->
            when (stub) {
                is EntryStub.Text -> {
                    val entry = textsById[stub.id] ?: return@mapIndexedNotNull null
                    StudyPadEntry(
                        type = "text",
                        id = entry.id,
                        position = position,
                        preview = makePreview(entry.text)
                    )
                }
                is EntryStub.BibleBookmark -> {
                    val bookmark = bibleById[stub.bookmarkId] ?: return@mapIndexedNotNull null
                    StudyPadEntry(
                        type = "bibleBookmark",
                        id = bookmark.id,
                        position = position,
                        verseRange = bookmark.verseRange.osisRef,
                        verseName = bookmark.verseRange.name,
                        hasNotes = bookmark.notes != null
                    )
                }
                is EntryStub.GenericBookmark -> {
                    val bookmark = genericById[stub.bookmarkId] ?: return@mapIndexedNotNull null
                    StudyPadEntry(
                        type = "genericBookmark",
                        id = bookmark.id,
                        position = position,
                        book = bookmark.book?.initials ?: "unknown",
                        key = bookmark.key,
                        hasNotes = bookmark.notes != null
                    )
                }
            }
        }

        return typedSuccess(IndexResult(
            labelId = labelId,
            labelName = label.name,
            totalEntries = stubs.size,
            entries = entries
        ))
    }

    // --- Page mode ---

    private fun loadFullEntriesForStubs(stubs: List<EntryStub>): List<StudyPadEntry> {
        val textIds = stubs.filterIsInstance<EntryStub.Text>().map { it.id }
        val bibleIds = stubs.filterIsInstance<EntryStub.BibleBookmark>().map { it.bookmarkId }
        val genericIds = stubs.filterIsInstance<EntryStub.GenericBookmark>().map { it.bookmarkId }

        val textsById = if (textIds.isNotEmpty()) dao.studyPadTextEntriesByIds(textIds).associateBy { it.id } else emptyMap()
        val bibleById = if (bibleIds.isNotEmpty()) dao.bibleBookmarksByIds(bibleIds).associateBy { it.id } else emptyMap()
        val genericById = if (genericIds.isNotEmpty()) dao.genericBookmarksByIds(genericIds).associateBy { it.id } else emptyMap()

        return stubs.mapNotNull { stub ->
            when (stub) {
                is EntryStub.Text -> {
                    val entry = textsById[stub.id] ?: return@mapNotNull null
                    StudyPadEntry(
                        type = "text",
                        id = entry.id,
                        orderNumber = entry.orderNumber,
                        text = entry.text,
                        contentType = entry.contentType?.name ?: "HTML"
                    )
                }
                is EntryStub.BibleBookmark -> {
                    val bookmark = bibleById[stub.bookmarkId] ?: return@mapNotNull null
                    StudyPadEntry(
                        type = "bibleBookmark",
                        id = bookmark.id,
                        orderNumber = stub.orderNumber,
                        verseRange = bookmark.verseRange.osisRef,
                        verseName = bookmark.verseRange.name,
                        notes = bookmark.notes
                    )
                }
                is EntryStub.GenericBookmark -> {
                    val bookmark = genericById[stub.bookmarkId] ?: return@mapNotNull null
                    StudyPadEntry(
                        type = "genericBookmark",
                        id = bookmark.id,
                        orderNumber = stub.orderNumber,
                        book = bookmark.book?.initials ?: "unknown",
                        key = bookmark.key,
                        notes = bookmark.notes
                    )
                }
            }
        }
    }

    private fun executePage(labelId: IdType, label: BookmarkEntities.Label, args: Args): ToolResult {
        val offset = args.offset
        val limit = args.limit

        val allStubs = loadEntryStubs(labelId)
        val totalEntries = allStubs.size
        val pageStubs = allStubs.drop(offset).take(limit)
        val entries = loadFullEntriesForStubs(pageStubs)

        return typedSuccess(PageResult(
            labelId = labelId,
            labelName = label.name,
            totalEntries = totalEntries,
            offset = offset,
            limit = limit,
            hasMore = offset + limit < totalEntries,
            entries = entries
        ))
    }
}
