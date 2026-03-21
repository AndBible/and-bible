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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for searching Bible text by Strong's concordance number.
 *
 * Automatically selects a Strong's-enabled, indexed Bible if none is specified.
 */
object SearchByStrongsNumberTool : Tool {
    @Serializable
    data class Args(
        val strongsNumber: String = "",
        val book: String = "",
        val maxResults: Int = 50,
        val offset: Int = 0,
    )

    @Serializable
    data class StrongsResultEntry(val verseRef: String, val verseName: String)

    @Serializable
    data class Result(
        val strongsNumber: String,
        val searchedBook: String,
        val totalResults: Int,
        val returnedResults: Int,
        val offset: Int,
        val hasMore: Boolean,
        val results: List<StrongsResultEntry>
    )

    override val agentTool = AgentTool.SEARCH_BY_STRONGS
    override val category = ToolCategory.BIBLE_SEARCH
    override val displayNameResId = R.string.tool_search_by_strongs

    private data class VerseResult(val book: String, val osisRef: String, val verseName: String)
    private data class CachedSearch(val strongsNumber: String, val bookInitials: String, val results: List<VerseResult>)

    private var cachedSearch: CachedSearch? = null

    override val description = """
        Search for verses containing a specific Strong's concordance number.
        Strong's numbers identify original Hebrew/Greek words.
        Use H prefix for Hebrew (e.g. H7225 for "reshith"/beginning) and G prefix for Greek (e.g. G26 for "agape"/love).
        Returns matching verse references from a Strong's-enabled, indexed Bible.
        If no book is specified, automatically selects the best available Strong's-enabled Bible.
        Supports pagination via offset parameter.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          strongsNumber:
            type: string
            description: "Strong's concordance number with H (Hebrew) or G (Greek) prefix, e.g. H7225, G26"
          book:
            type: string
            description: "Optional Bible module initials to search in. If not specified, automatically selects a Strong's-enabled Bible."
          maxResults:
            type: integer
            description: "Maximum number of results to return per page (default: 50)"
          offset:
            type: integer
            description: "Number of results to skip for pagination (default: 0)"
        required: [strongsNumber]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val strongs = arguments.optString("strongsNumber", "").takeIf { it.isNotBlank() } ?: return null
        val book = arguments.optString("book", "").takeIf { it.isNotBlank() }
        return if (book != null) "$strongs ($book)" else strongs
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return application.getString(R.string.tool_log_search_results, data.returnedResults, data.totalResults)
    }

    private val STRONGS_PATTERN = Regex("^([HhGg])0*(\\d+)$")

    /**
     * Normalize a Strong's number to the lowercase Lucene query form (e.g. "h7225").
     * Returns null if the input is not a valid Strong's number.
     */
    private fun normalizeStrongsNumber(input: String): String? {
        val match = STRONGS_PATTERN.matchEntire(input.trim()) ?: return null
        val prefix = match.groupValues[1].lowercase()
        val number = match.groupValues[2]
        return "$prefix$number"
    }

    private fun findStrongsBible(bookInitials: String?): SwordBook? {
        if (!bookInitials.isNullOrBlank()) {
            return Books.installed().getBook(bookInitials) as? SwordBook
        }
        // Prefer indexed Strong's Bibles; fall back to unindexed if none indexed
        return AiDocumentFilter.filterAllowed(
            Books.installed().books.filterIsInstance<SwordBook>()
        ).filter { it.hasFeature(FeatureType.STRONGS_NUMBERS) }
            .sortedByDescending { it.indexStatus == IndexStatus.DONE }
            .firstOrNull()
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.strongsNumber.isBlank()) {
            return ToolResult.error("Missing required parameter: strongsNumber")
        }

        val normalized = normalizeStrongsNumber(args.strongsNumber)
            ?: return ToolResult.error(
                "Invalid Strong's number: '${args.strongsNumber}'. Use H prefix for Hebrew (e.g. H7225) or G prefix for Greek (e.g. G26).",
                "INVALID_STRONGS"
            )

        val maxResults = args.maxResults.coerceIn(1, 500)
        val offset = args.offset.coerceAtLeast(0)

        val bible = findStrongsBible(args.book.takeIf { it.isNotBlank() })
            ?: return ToolResult.error(
                "No indexed Bible with Strong's numbers found. Please install and index a Bible with Strong's support (e.g. KJV).",
                "NO_STRONGS_BIBLE"
            )

        if (!bible.hasFeature(FeatureType.STRONGS_NUMBERS)) {
            return ToolResult.error(
                "Bible '${bible.initials}' does not contain Strong's numbers.",
                "NO_STRONGS"
            )
        }

        if (bible.indexStatus != IndexStatus.DONE) {
            return ToolResult.error(
                "Bible '${bible.initials}' is not indexed. Please index it first to enable Strong's search.",
                "NOT_INDEXED"
            )
        }

        val query = "strong:$normalized"

        return try {
            withContext(Dispatchers.IO) {
                val allResults = cachedSearch
                    ?.takeIf { it.strongsNumber == normalized && it.bookInitials == bible.initials }
                    ?.results
                    ?: performSearch(bible, query).also {
                        cachedSearch = CachedSearch(normalized, bible.initials, it)
                    }

                val page = allResults.drop(offset).take(maxResults)

                typedSuccess(Result(
                    strongsNumber = args.strongsNumber.uppercase(),
                    searchedBook = bible.initials,
                    totalResults = allResults.size,
                    returnedResults = page.size,
                    offset = offset,
                    hasMore = offset + page.size < allResults.size,
                    results = page.map { StrongsResultEntry(it.osisRef, it.verseName) }
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Strong's search failed: ${e.message}", "SEARCH_ERROR")
        }
    }

    private fun performSearch(bible: SwordBook, query: String): List<VerseResult> {
        return SwordContentFacade.search(bible, query).map { key ->
            VerseResult(bible.initials, key.osisRef, key.name)
        }
    }
}
