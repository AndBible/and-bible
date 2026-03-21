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

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
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
    @Serializable
    data class Args(val verseRef: String = "")

    @Serializable
    data class LabelRef(val id: IdType, val name: String)

    @Serializable
    data class BookmarkInfo(
        val id: IdType,
        val verseRange: String,
        val verseName: String,
        val notes: String?,
        val createdAt: Long,
        val lastUpdatedOn: Long,
        val labels: List<LabelRef>
    )

    @Serializable
    data class Result(
        val verseRef: String,
        val bookmarkCount: Int,
        val bookmarks: List<BookmarkInfo>
    )

    override val agentTool = AgentTool.GET_BOOKMARKS_FOR_VERSE
    override val category = ToolCategory.BOOKMARKS
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
        if (result !is ToolResult.Success || result.data !is Result) return null
        return application.getString(R.string.tool_log_bookmark_count, (result.data as Result).bookmarkCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val verseRef = args.verseRef

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

            val results = bookmarks.map { bookmark ->
                val labels = labelsMap[bookmark.id] ?: emptyList()
                BookmarkInfo(
                    id = bookmark.id,
                    verseRange = bookmark.verseRange.osisRef,
                    verseName = bookmark.verseRange.name,
                    notes = bookmark.notes,
                    createdAt = bookmark.createdAt.time,
                    lastUpdatedOn = bookmark.lastUpdatedOn.time,
                    labels = labels.map { LabelRef(it.id, it.name) }
                )
            }

            typedSuccess(Result(
                verseRef = verseRef,
                bookmarkCount = results.size,
                bookmarks = results
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to get bookmarks: ${e.message}", "READ_ERROR")
        }
    }
}
