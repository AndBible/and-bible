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
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.llm.tools.yamlToJson
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.passage.VerseRange
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for getting bookmarks associated with a verse or verse range.
 *
 * Returns bookmark information including notes and labels.
 */
object GetBookmarksForVerseTool : Tool {
    override val name = "getBookmarksForVerse"
    override val displayNameResId = R.string.tool_get_bookmarks_for_verse

    override val description = """
        Get all bookmarks associated with a verse or verse range.
        Returns bookmark details including notes and associated labels.
        Useful for finding user annotations and notes for specific passages.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          verseRef:
            type: string
            description: "OSIS verse reference, e.g., 'Matt.5.3', 'Gen.1.1-3'. Can be a single verse or range."
        required: [verseRef]
    """)

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val verseRef = arguments.optString("verseRef", "").takeIf { it.isNotBlank() } ?: return null
        return localizeVerseRef(verseRef)
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is JSONObject) return null
        val data = result.data as JSONObject
        val count = data.optInt("bookmarkCount", -1)
        return if (count >= 0) "$count bookmarks" else null
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val verseRef = arguments.optString("verseRef", "")

        if (verseRef.isBlank()) {
            return ToolResult.error("Missing required parameter: verseRef")
        }

        return try {
            // Parse the verse reference using KJVA versification
            val key = PassageKeyFactory.instance().getKey(KJVA, verseRef)
            val verseRange = key.getRangeAt(0, RestrictionType.NONE) as? VerseRange
                ?: return ToolResult.error("Invalid verse reference: $verseRef", "INVALID_REFERENCE")

            // Get bookmarks for the verse range
            val bookmarks = dao.bookmarksForVerseRange(verseRange)

            // Batch-fetch labels: 3 queries instead of N+1
            val bookmarkIds = bookmarks.map { it.id }
            val btlList = dao.getBookmarkToLabelsForBookmarks(bookmarkIds)
            val labelIds = btlList.map { it.labelId }.distinct()
            val labelsById = dao.labelsById(labelIds).associateBy { it.id }
            val labelsMap = btlList.groupBy({ it.bookmarkId }, { labelsById[it.labelId] })
                .mapValues { it.value.filterNotNull() }

            val results = JSONArray()
            for (bookmark in bookmarks) {
                val labels = labelsMap[bookmark.id] ?: emptyList()

                results.put(JSONObject().apply {
                    put("id", bookmark.id.toString())
                    put("verseRange", bookmark.verseRange.osisRef)
                    put("verseName", bookmark.verseRange.name)
                    put("notes", bookmark.notes ?: JSONObject.NULL)
                    put("createdAt", bookmark.createdAt.time)
                    put("lastUpdatedOn", bookmark.lastUpdatedOn.time)
                    put("labels", JSONArray().apply {
                        for (label in labels) {
                            put(JSONObject().apply {
                                put("id", label.id.toString())
                                put("name", label.name)
                            })
                        }
                    })
                })
            }

            ToolResult.success {
                put("verseRef", verseRef)
                put("bookmarkCount", results.length())
                put("bookmarks", results)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to get bookmarks: ${e.message}", "READ_ERROR")
        }
    }
}
