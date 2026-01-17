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

import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.IndexStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for listing installed documents.
 *
 * Returns information about installed Bibles, commentaries, dictionaries, etc.
 */
object GetInstalledDocumentsTool : Tool {
    override val name = "getInstalledDocuments"

    override val description = """
        Get a list of installed documents (Bibles, commentaries, dictionaries, etc.).
        Use this to find available documents before reading content.
        Can filter by category: BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          category:
            type: string
            enum: [BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS]
            description: Optional category filter. If not specified, returns all documents.
    """)

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val categoryStr = arguments.optString("category", "")

        return try {
            val books = if (categoryStr.isNotBlank()) {
                val category = try {
                    BookCategory.valueOf(categoryStr)
                } catch (e: IllegalArgumentException) {
                    return ToolResult.error("Invalid category: $categoryStr", "INVALID_CATEGORY")
                }
                SwordDocumentFacade.getBooks(category)
            } else {
                Books.installed().books
            }

            val results = JSONArray()
            for (book in books) {
                results.put(JSONObject().apply {
                    put("initials", book.initials)
                    put("name", book.name)
                    put("category", book.bookCategory.name)
                    put("language", book.language?.code ?: "unknown")
                    put("isLocked", book.isLocked)
                    put("isIndexed", book.indexStatus == IndexStatus.DONE)
                    put("abbreviation", book.abbreviation)
                })
            }

            ToolResult.success {
                put("documentCount", results.length())
                put("documents", results)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to get documents: ${e.message}", "READ_ERROR")
        }
    }
}
