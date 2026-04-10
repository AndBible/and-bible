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

import android.net.Uri
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.json.JSONObject

/**
 * Tool for listing the table of contents / navigation keys of a GenBook module.
 *
 * Works with all GENERAL_BOOK modules including MyDocuments and external GenBooks.
 * Supports pagination for large books.
 */
object GetGenBookKeysTool : Tool {
    @Serializable
    data class Args(
        val book: String = "",
        val offset: Int = 0,
        val limit: Int = 100
    )

    @Serializable
    data class KeyInfo(
        val name: String,
        val osisRef: String,
        val linkUrl: String
    )

    @Serializable
    data class Result(
        val book: String,
        val bookName: String,
        val totalCount: Int,
        val offset: Int,
        val returnedCount: Int,
        val hasMore: Boolean,
        val keys: List<KeyInfo>
    )

    override val agentTool = AgentTool.GET_GENBOOK_KEYS
    override val category = ToolCategory.GENERAL_BOOKS
    override val displayNameResId = R.string.tool_get_genbook_keys

    override val description = """
        List table of contents / navigation keys for a general book (reference material, handbook, My Document, etc.).
        Returns key names, osisRef values, and linkUrl for each entry.
        Use getInstalledDocuments with category=GENERAL_BOOK to find available general books first.
        Use getGenBookContent to read the content of a specific key.
        For large books, use offset and limit for pagination.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          book:
            type: string
            description: "Book initials (e.g., 'Westminster', 'AIDocuments'). Use getInstalledDocuments with category=GENERAL_BOOK to find available books."
          offset:
            type: integer
            description: "Number of keys to skip (for pagination). Default: 0."
            default: 0
          limit:
            type: integer
            description: "Maximum number of keys to return. Default: 100, max: 500."
            default: 100
        required: [book]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        return arguments.optString("book", "").takeIf { it.isNotBlank() }
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return application.getString(R.string.tool_log_genbook_key_count, data.returnedCount, data.totalCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.book.isBlank()) {
            return ToolResult.error("Missing required parameter: book")
        }

        val book = SwordDocumentFacade.getDocumentByInitials(args.book)
            ?: return ToolResult.error("Book not found: ${args.book}", "BOOK_NOT_FOUND")

        if (!AiDocumentFilter.isAllowed(args.book)) {
            return ToolResult.error("Document excluded by user settings: ${args.book}", "DOCUMENT_EXCLUDED")
        }

        if (book.bookCategory != BookCategory.GENERAL_BOOK) {
            return ToolResult.error("Book is not a general book: ${args.book}", "INVALID_BOOK_TYPE")
        }

        return try {
            val allKeys = collectKeys(book)
            val totalCount = allKeys.size
            val clampedLimit = args.limit.coerceIn(1, 500)
            val clampedOffset = args.offset.coerceIn(0, totalCount)

            val pageKeys = allKeys.drop(clampedOffset).take(clampedLimit)

            val encodedInitials = Uri.encode(args.book)
            val keyInfos = pageKeys.map { key ->
                val ref = key.osisRef ?: key.name
                KeyInfo(
                    name = key.name,
                    osisRef = ref,
                    linkUrl = "sword://$encodedInitials/${Uri.encode(ref)}"
                )
            }

            typedSuccess(Result(
                book = args.book,
                bookName = book.name,
                totalCount = totalCount,
                offset = clampedOffset,
                returnedCount = keyInfos.size,
                hasMore = clampedOffset + keyInfos.size < totalCount,
                keys = keyInfos
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to list keys: ${e.message}", "READ_ERROR")
        }
    }

    private fun collectKeys(book: org.crosswire.jsword.book.Book): List<Key> {
        return if (book.isEpub) {
            book.epubBackend?.tocKeys ?: emptyList()
        } else {
            val keys = mutableListOf<Key>()
            for (key in book.globalKeyList) {
                if (key.name.isNotBlank()) {
                    keys.add(key)
                }
            }
            keys
        }
    }
}
