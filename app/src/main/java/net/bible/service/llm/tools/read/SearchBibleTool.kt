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
import kotlinx.serialization.Serializable
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.IndexStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for searching Bible text.
 *
 * Uses the Lucene index to search for words or phrases.
 */
object SearchBibleTool : Tool {
    @Serializable
    data class Args(
        val query: String = "",
        val books: List<String> = emptyList(),
        val maxResults: Int = 50,
        val offset: Int = 0,
    )

    @Serializable
    data class SearchResultEntry(
        val book: String,
        val verseRef: String,
        val verseName: String
    )

    @Serializable
    data class Result(
        val query: String,
        val totalResults: Int,
        val returnedResults: Int,
        val offset: Int,
        val hasMore: Boolean,
        val results: List<SearchResultEntry>
    )

    override val agentTool = AgentTool.SEARCH_BIBLE
    override val category = ToolCategory.BIBLE_SEARCH
    override val displayNameResId = R.string.tool_search_bible

    private data class VerseResult(val book: String, val osisRef: String, val verseName: String)
    private data class CachedSearch(val query: String, val bookInitials: List<String>, val results: List<VerseResult>)

    private var cachedSearch: CachedSearch? = null

    override val description = """
        Search for words or phrases in Bible translations using a Lucene full-text index.
        Returns a list of verses matching the query. Only indexed books can be searched.
        getInstalledDocuments isIndexed tells which documents can be used for searching.
        Supports pagination via offset parameter.

        IMPORTANT: This is a keyword index, NOT a semantic/thematic search. Queries must use
        words that literally appear in the text. Multi-word queries use OR logic by default
        (matching ANY word), which returns many irrelevant results. Use exact phrases ("...")
        or AND/+operators for precision. The search language must match the indexed Bible's language.

        For thematic studies, prefer using your Bible knowledge to identify relevant passages
        by reference, then retrieve them with getVerseContent. Use searchBible only when you
        need to find specific words or phrases in the text.

        Query syntax:
        - Single word: love
        - Exact phrase: "the Lord is my shepherd"
        - Boolean: love AND truth, mercy OR grace, love NOT hate
        - Prefix wildcard: redeem* (matches redeem, redeemed, redeemer, etc.)
        - Required/excluded: +faith -works
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          query:
            type: string
            description: "Search query. Supports Lucene syntax: single words, \"exact phrases\" in quotes, boolean operators (AND, OR, NOT), prefix wildcards (redeem*), and required/excluded terms (+word, -word)."
          books:
            type: array
            items:
              type: string
            description: Optional list of book initials to search in. If not specified, searches the first available indexed Bible.
          maxResults:
            type: integer
            description: "Maximum number of results to return per page (default: 50)"
          offset:
            type: integer
            description: "Number of results to skip for pagination (default: 0). Use with maxResults to page through results."
        required: [query]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val query = arguments.optString("query", "").takeIf { it.isNotBlank() } ?: return null
        val books = arguments.optJSONArray("books")
        val maxResults = arguments.optInt("maxResults", 50)
        val offset = arguments.optInt("offset", 0)
        val parts = mutableListOf<String>()
        if (books != null && books.length() > 0) {
            parts.add((0 until books.length()).joinToString(", ") { books.getString(it) })
        }
        if (offset > 0) parts.add("offset $offset")
        parts.add("max $maxResults")
        return "\"$query\" (${parts.joinToString(", ")})"
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return application.getString(R.string.tool_log_search_results, data.returnedResults, data.totalResults)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val query = args.query
        val maxResults = args.maxResults.coerceIn(1, 500)
        val offset = args.offset.coerceAtLeast(0)

        if (query.isBlank()) {
            return ToolResult.error("Missing required parameter: query")
        }

        // Get books to search
        val bookInitials = args.books.ifEmpty {
            // Find first indexed Bible
            val indexedBible = AiDocumentFilter.filterAllowed(
                Books.installed().books.filterIsInstance<SwordBook>()
            ).firstOrNull { it.indexStatus == IndexStatus.DONE }

            if (indexedBible == null) {
                return ToolResult.error("No indexed Bible found. Please index a Bible first.", "NO_INDEX")
            }
            listOf(indexedBible.initials)
        }

        // Validate explicitly-requested books are indexed
        if (args.books.isNotEmpty()) {
            val notIndexed = mutableListOf<String>()
            val notFound = mutableListOf<String>()
            for (initials in bookInitials) {
                val book = Books.installed().getBook(initials) as? SwordBook
                if (book == null) notFound.add(initials)
                else if (book.indexStatus != IndexStatus.DONE) notIndexed.add(initials)
            }
            if (notFound.size + notIndexed.size == bookInitials.size) {
                val parts = mutableListOf<String>()
                if (notIndexed.isNotEmpty()) {
                    parts.add("${notIndexed.joinToString(", ")} ${if (notIndexed.size == 1) "is" else "are"} not indexed. Please index first to enable search.")
                }
                if (notFound.isNotEmpty()) {
                    parts.add("${notFound.joinToString(", ")} not found.")
                }
                return ToolResult.error(parts.joinToString(" "), "NOT_INDEXED")
            }
        }

        return try {
            withContext(Dispatchers.IO) {
                val allResults = cachedSearch?.takeIf { it.query == query && it.bookInitials == bookInitials }?.results
                    ?: performSearch(query, bookInitials).also {
                        cachedSearch = CachedSearch(query, bookInitials, it)
                    }

                val page = allResults.drop(offset).take(maxResults)

                typedSuccess(Result(
                    query = query,
                    totalResults = allResults.size,
                    returnedResults = page.size,
                    offset = offset,
                    hasMore = offset + page.size < allResults.size,
                    results = page.map { SearchResultEntry(it.book, it.osisRef, it.verseName) }
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Search failed: ${e.message}", "SEARCH_ERROR")
        }
    }

    private fun performSearch(query: String, bookInitials: List<String>): List<VerseResult> {
        val results = mutableListOf<VerseResult>()
        for (bookInitial in bookInitials) {
            val book = Books.installed().getBook(bookInitial) as? SwordBook ?: continue
            if (book.indexStatus != IndexStatus.DONE) continue

            for (key in SwordContentFacade.search(book, query)) {
                results.add(VerseResult(bookInitial, key.osisRef, key.name))
            }
        }
        return results
    }
}
