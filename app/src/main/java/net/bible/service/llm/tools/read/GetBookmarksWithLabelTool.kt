/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for getting all bookmarks with a specific label.
 *
 * Useful for retrieving the contents of a StudyPad or all bookmarks in a category.
 */
object GetBookmarksWithLabelTool : Tool {
    override val name = "getBookmarksWithLabel"
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
        if (result !is ToolResult.Success || result.data !is JSONObject) return null
        val data = result.data as JSONObject
        val count = data.optInt("bookmarkCount", -1)
        return if (count >= 0) "$count bookmarks" else null
    }

    private val defaultFields = setOf("verseRange", "verseName", "createdAt")

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val labelIdStr = arguments.optString("labelId", "")
        val maxResults = arguments.optInt("maxResults", 100)
        val fieldsArray = arguments.optJSONArray("fields")
        val fields = if (fieldsArray != null && fieldsArray.length() > 0) {
            (0 until fieldsArray.length()).map { fieldsArray.getString(it) }.toSet()
        } else {
            defaultFields
        }

        if (labelIdStr.isBlank()) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            val labelId = IdType.fromString(labelIdStr)
            val label = dao.labelById(labelId)
                ?: return ToolResult.error("Label not found: $labelIdStr", "LABEL_NOT_FOUND")

            // Get Bible bookmarks with this label
            val bibleBookmarks = dao.bookmarksWithLabel(label)
            // Get generic bookmarks with this label
            val genericBookmarks = dao.genericBookmarksWithLabel(label)

            val results = JSONArray()
            var count = 0

            for (bookmark in bibleBookmarks) {
                if (count >= maxResults) break
                results.put(JSONObject().apply {
                    put("id", bookmark.id.toString())
                    put("type", "bible")
                    if ("verseRange" in fields) put("verseRange", bookmark.verseRange.osisRef)
                    if ("verseName" in fields) put("verseName", bookmark.verseRange.name)
                    if ("notes" in fields) put("notes", bookmark.notes ?: JSONObject.NULL)
                    if ("createdAt" in fields) put("createdAt", bookmark.createdAt.time)
                })
                count++
            }

            for (bookmark in genericBookmarks) {
                if (count >= maxResults) break
                results.put(JSONObject().apply {
                    put("id", bookmark.id.toString())
                    put("type", "generic")
                    put("book", bookmark.book?.initials ?: "unknown")
                    put("key", bookmark.key)
                    if ("notes" in fields) put("notes", bookmark.notes ?: JSONObject.NULL)
                    if ("createdAt" in fields) put("createdAt", bookmark.createdAt.time)
                })
                count++
            }

            ToolResult.success {
                put("labelId", labelIdStr)
                put("labelName", label.name)
                put("bookmarkCount", results.length())
                put("bookmarks", results)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to get bookmarks: ${e.message}", "READ_ERROR")
        }
    }
}
