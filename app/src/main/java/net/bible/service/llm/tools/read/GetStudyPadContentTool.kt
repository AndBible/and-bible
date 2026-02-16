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

package net.bible.service.llm.tools.read

import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONArray
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
    override val name = "getStudyPadContent"
    override val displayNameResId = R.string.tool_get_study_pad_content

    override val description = """
        Get the content of a StudyPad (label).
        StudyPads contain ordered entries: text notes and bookmark references.
        Supports multiple read modes:
        - 'info': metadata only (entry counts, estimated size). Use first to check StudyPad size.
        - 'index': lightweight overview with type, position, and ~80-char preview for each entry.
        - 'page': paginated full content (use offset/limit to read in chunks).
        - 'full': all entries with full content (default). May be large for big StudyPads.
        For large StudyPads, use 'info' first, then 'index' or 'page' to read selectively.
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

    private data class OrderedEntry(val orderNumber: Int, val entry: JSONObject)

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val labelIdStr = arguments.optString("labelId", "")

        if (labelIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        val mode = arguments.optString("mode", "full")

        return try {
            val labelId = IdType.fromString(labelIdStr)
            val label = dao.labelById(labelId)
                ?: return ToolResult.error("Label not found: $labelIdStr", "LABEL_NOT_FOUND")

            when (mode) {
                "info" -> executeInfo(labelIdStr, labelId, label)
                "index" -> executeIndex(labelIdStr, labelId, label)
                "page" -> executePage(labelIdStr, labelId, label, arguments)
                else -> executeFull(labelIdStr, labelId, label)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to get StudyPad content: ${e.message}", "READ_ERROR")
        }
    }

    private fun loadAllEntries(labelId: IdType): List<OrderedEntry> {
        val textEntries = dao.studyPadTextEntriesByLabelId(labelId)
        val bibleBookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId)
        val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId)

        val orderedEntries = mutableListOf<OrderedEntry>()

        for (entry in textEntries) {
            orderedEntries.add(OrderedEntry(entry.orderNumber, JSONObject().apply {
                put("type", "text")
                put("id", entry.id.toString())
                put("orderNumber", entry.orderNumber)
                put("text", entry.text)
                put("contentType", entry.contentType?.name ?: "HTML")
            }))
        }

        for (btl in bibleBookmarkToLabels) {
            val bookmark = dao.bibleBookmarkById(btl.bookmarkId) ?: continue
            orderedEntries.add(OrderedEntry(btl.orderNumber, JSONObject().apply {
                put("type", "bibleBookmark")
                put("id", bookmark.id.toString())
                put("orderNumber", btl.orderNumber)
                put("verseRange", bookmark.verseRange.osisRef)
                put("verseName", bookmark.verseRange.name)
                put("notes", bookmark.notes ?: JSONObject.NULL)
            }))
        }

        for (btl in genericBookmarkToLabels) {
            val bookmark = dao.genericBookmarkById(btl.bookmarkId) ?: continue
            orderedEntries.add(OrderedEntry(btl.orderNumber, JSONObject().apply {
                put("type", "genericBookmark")
                put("id", bookmark.id.toString())
                put("orderNumber", btl.orderNumber)
                put("book", bookmark.book?.initials ?: "unknown")
                put("key", bookmark.key)
                put("notes", bookmark.notes ?: JSONObject.NULL)
            }))
        }

        orderedEntries.sortBy { it.orderNumber }
        return orderedEntries
    }

    private fun executeFull(
        labelIdStr: String,
        labelId: IdType,
        label: BookmarkEntities.Label
    ): ToolResult {
        val orderedEntries = loadAllEntries(labelId)
        val results = JSONArray()
        for (entry in orderedEntries) {
            results.put(entry.entry)
        }
        return ToolResult.success {
            put("labelId", labelIdStr)
            put("labelName", label.name)
            put("entryCount", results.length())
            put("entries", results)
        }
    }

    private fun executeInfo(
        labelIdStr: String,
        labelId: IdType,
        label: BookmarkEntities.Label
    ): ToolResult {
        val textEntryCount = dao.countStudyPadTextEntities(labelId)
        val bibleBookmarkCount = dao.countBookmarkEntities(labelId)
        val genericBookmarkCount = dao.countGenericBookmarkEntities(labelId)
        val totalEntries = textEntryCount + bibleBookmarkCount + genericBookmarkCount

        val textLength = dao.estimateStudyPadTextLength(labelId)
        val bibleNotesLength = dao.estimateBibleBookmarkNotesLength(labelId)
        val genericNotesLength = dao.estimateGenericBookmarkNotesLength(labelId)

        return ToolResult.success {
            put("labelId", labelIdStr)
            put("labelName", label.name)
            put("totalEntries", totalEntries)
            put("textEntryCount", textEntryCount)
            put("bibleBookmarkCount", bibleBookmarkCount)
            put("genericBookmarkCount", genericBookmarkCount)
            put("estimatedTextLength", textLength + bibleNotesLength + genericNotesLength)
        }
    }

    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    private fun makePreview(text: String?, maxLength: Int = 80): String {
        if (text.isNullOrBlank()) return ""
        val stripped = text.replace(HTML_TAG_REGEX, "").trim()
        return if (stripped.length <= maxLength) stripped
        else stripped.substring(0, maxLength) + "..."
    }

    private fun executeIndex(
        labelIdStr: String,
        labelId: IdType,
        label: BookmarkEntities.Label
    ): ToolResult {
        val orderedEntries = loadAllEntries(labelId)
        val indexEntries = JSONArray()

        for ((position, orderedEntry) in orderedEntries.withIndex()) {
            val entry = orderedEntry.entry
            val indexEntry = JSONObject().apply {
                put("position", position)
                put("type", entry.getString("type"))
                put("id", entry.getString("id"))
            }

            when (entry.getString("type")) {
                "text" -> {
                    indexEntry.put("preview", makePreview(entry.optString("text")))
                }
                "bibleBookmark" -> {
                    indexEntry.put("verseRange", entry.getString("verseRange"))
                    indexEntry.put("verseName", entry.getString("verseName"))
                    indexEntry.put("hasNotes", entry.opt("notes") != JSONObject.NULL)
                }
                "genericBookmark" -> {
                    indexEntry.put("book", entry.getString("book"))
                    indexEntry.put("key", entry.getString("key"))
                    indexEntry.put("hasNotes", entry.opt("notes") != JSONObject.NULL)
                }
            }

            indexEntries.put(indexEntry)
        }

        return ToolResult.success {
            put("labelId", labelIdStr)
            put("labelName", label.name)
            put("totalEntries", orderedEntries.size)
            put("entries", indexEntries)
        }
    }

    private fun executePage(
        labelIdStr: String,
        labelId: IdType,
        label: BookmarkEntities.Label,
        arguments: JSONObject
    ): ToolResult {
        val offset = arguments.optInt("offset", 0)
        val limit = arguments.optInt("limit", 20)

        val allEntries = loadAllEntries(labelId)
        val totalEntries = allEntries.size

        val pageEntries = allEntries
            .drop(offset)
            .take(limit)

        val results = JSONArray()
        for (entry in pageEntries) {
            results.put(entry.entry)
        }

        return ToolResult.success {
            put("labelId", labelIdStr)
            put("labelName", label.name)
            put("totalEntries", totalEntries)
            put("offset", offset)
            put("limit", limit)
            put("hasMore", offset + limit < totalEntries)
            put("entries", results)
        }
    }
}
