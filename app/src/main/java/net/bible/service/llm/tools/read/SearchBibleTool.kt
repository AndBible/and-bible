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
import net.bible.android.activity.R
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
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
    override val name = "searchBible"
    override val displayNameResId = R.string.tool_search_bible

    override val description = """
        Search for words or phrases in Bible translations.
        Returns a list of verses that match the search query.
        The search uses a full-text index and supports basic search operations.
        Note: Only indexed books can be searched.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          query:
            type: string
            description: Search query - a word or phrase to search for
          books:
            type: array
            items:
              type: string
            description: Optional list of book initials to search in. If not specified, searches the first available indexed Bible.
          maxResults:
            type: integer
            description: "Maximum number of results to return (default: 50)"
        required: [query]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val query = arguments.optString("query", "").takeIf { it.isNotBlank() } ?: return null
        val books = arguments.optJSONArray("books")
        val maxResults = arguments.optInt("maxResults", 50)
        val booksPart = if (books != null && books.length() > 0) {
            " (${(0 until books.length()).joinToString(", ") { books.getString(it) }}, max $maxResults)"
        } else {
            " (max $maxResults)"
        }
        return "\"$query\"$booksPart"
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is JSONObject) return null
        val data = result.data as JSONObject
        val count = data.optInt("totalResults", -1)
        return if (count >= 0) "$count results" else null
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val query = arguments.optString("query", "")
        val booksArray = arguments.optJSONArray("books")
        val maxResults = arguments.optInt("maxResults", 50).coerceIn(1, 500)

        if (query.isBlank()) {
            return ToolResult.error("Missing required parameter: query")
        }

        // Get books to search
        val bookInitials = if (booksArray != null && booksArray.length() > 0) {
            (0 until booksArray.length()).map { booksArray.getString(it) }
        } else {
            // Find first indexed Bible
            val indexedBible = Books.installed().books
                .filterIsInstance<SwordBook>()
                .firstOrNull { it.indexStatus == IndexStatus.DONE }

            if (indexedBible == null) {
                return ToolResult.error("No indexed Bible found. Please index a Bible first.", "NO_INDEX")
            }
            listOf(indexedBible.initials)
        }

        return try {
            withContext(Dispatchers.IO) {
                val results = JSONArray()
                var totalFound = 0

                for (bookInitial in bookInitials) {
                    val book = Books.installed().getBook(bookInitial) as? SwordBook ?: continue

                    if (book.indexStatus != IndexStatus.DONE) {
                        continue
                    }

                    // Perform search using SwordContentFacade
                    val searchResults = SwordContentFacade.search(book, query)

                    for (key in searchResults) {
                        if (totalFound >= maxResults) break

                        results.put(JSONObject().apply {
                            put("book", bookInitial)
                            put("verseRef", key.osisRef)
                            put("verseName", key.name)
                        })
                        totalFound++
                    }

                    if (totalFound >= maxResults) break
                }

                ToolResult.success {
                    put("query", query)
                    put("totalResults", totalFound)
                    put("results", results)
                }
            }
        } catch (e: Exception) {
            ToolResult.error("Search failed: ${e.message}", "SEARCH_ERROR")
        }
    }
}
