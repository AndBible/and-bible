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
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for getting the full content of a StudyPad.
 *
 * Returns all entries in a StudyPad including text entries and bookmark references.
 */
object GetStudyPadContentTool : Tool {
    override val name = "getStudyPadContent"
    override val displayNameResId = R.string.tool_get_study_pad_content

    override val description = """
        Get the full content of a StudyPad (label).
        StudyPads contain ordered entries: text notes and bookmark references.
        Returns all entries in their display order.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: The ID of the label/StudyPad. Get label IDs from getAllLabels.
        required: [labelId]
    """)

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val labelIdStr = arguments.optString("labelId", "")

        if (labelIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            val labelId = IdType.fromString(labelIdStr)
            val label = dao.labelById(labelId)
                ?: return ToolResult.error("Label not found: $labelIdStr", "LABEL_NOT_FOUND")

            // Get StudyPad text entries
            val textEntries = dao.studyPadTextEntriesByLabelId(labelId)

            // Get bookmarks with this label (ordered by StudyPad order)
            val bibleBookmarkToLabels = dao.getBookmarkToLabelsForLabel(labelId)
            val genericBookmarkToLabels = dao.getGenericBookmarkToLabelsForLabel(labelId)

            // Build combined entries list with order numbers
            data class OrderedEntry(val orderNumber: Int, val entry: JSONObject)
            val orderedEntries = mutableListOf<OrderedEntry>()

            // Add text entries
            for (entry in textEntries) {
                orderedEntries.add(OrderedEntry(entry.orderNumber, JSONObject().apply {
                    put("type", "text")
                    put("id", entry.id.toString())
                    put("orderNumber", entry.orderNumber)
                    put("text", entry.text)
                    put("contentType", entry.contentType?.name ?: "HTML")
                }))
            }

            // Add Bible bookmark entries
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

            // Add generic bookmark entries
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

            // Sort by order number
            orderedEntries.sortBy { it.orderNumber }

            val results = JSONArray()
            for (entry in orderedEntries) {
                results.put(entry.entry)
            }

            ToolResult.success {
                put("labelId", labelIdStr)
                put("labelName", label.name)
                put("entryCount", results.length())
                put("entries", results)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to get StudyPad content: ${e.message}", "READ_ERROR")
        }
    }
}
