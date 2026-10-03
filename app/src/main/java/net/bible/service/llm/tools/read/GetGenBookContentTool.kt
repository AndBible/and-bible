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
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.sword.ContentFormat
import net.bible.service.sword.OsisToPlainText
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.json.JSONObject

/**
 * Tool for reading the content of a specific entry in a GenBook module.
 *
 * Works with all GENERAL_BOOK modules including MyDocuments and external GenBooks.
 * Returns content with ordinal anchor markers ([§N]) for precise referencing.
 */
object GetGenBookContentTool : Tool {
    @Serializable
    data class Args(
        val book: String = "",
        val key: String = "",
        val format: ContentFormat = ContentFormat.TEXT
    )

    @Serializable
    data class Result(
        val book: String,
        val bookName: String,
        val key: String,
        val keyName: String,
        val linkUrl: String,
        val text: String? = null,
        val osisXml: String? = null
    )

    override val agentTool = AgentTool.GET_GENBOOK_CONTENT
    override val category = ToolCategory.GENERAL_BOOKS
    override val displayNameResId = R.string.tool_get_genbook_content

    override val description = """
        Read the content of a specific entry in a general book (reference material, handbook, My Document, etc.).
        Use getGenBookKeys first to find available keys and their osisRef values.
        Returns readable text by default with anchor markers like [§5] at sentence boundaries,
        or raw OSIS XML with format='xml'.

        Each result includes a 'linkUrl' field (already URL-encoded).
        Text content includes anchor markers [§N] — use these for precise citations
        by appending #oN or #oN-M to the linkUrl (e.g., sword://Westminster/Chapter1#o5-10).
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          book:
            type: string
            description: "Book initials (e.g., 'Westminster', 'AIDocuments'). Use getInstalledDocuments with category=GENERAL_BOOK to find available books."
          key:
            type: string
            description: "The osisRef of the entry to read (from getGenBookKeys result)."
          format:
            type: string
            enum: [text, xml]
            description: "Output format: 'text' (default) returns readable text with anchor markers. 'xml' returns raw OSIS XML."
            default: text
        required: [book, key]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val book = arguments.optString("book", "").takeIf { it.isNotBlank() } ?: return null
        val key = arguments.optString("key", "").takeIf { it.isNotBlank() } ?: return null
        return "$book: $key"
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
        if (args.key.isBlank()) {
            return ToolResult.error("Missing required parameter: key")
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
            val resolvedKey = book.getKey(args.key)
                ?: return ToolResult.error("Key not found: ${args.key}", "KEY_NOT_FOUND")

            val fragment = SwordContentFacade.readOsisFragment(book, resolvedKey)
            val linkUrl = "sword://${Uri.encode(args.book)}/${Uri.encode(args.key)}"

            if (args.format == ContentFormat.XML) {
                val outputter = XMLOutputter(Format.getRawFormat())
                typedSuccess(Result(
                    book = args.book,
                    bookName = book.name,
                    key = args.key,
                    keyName = resolvedKey.name,
                    linkUrl = linkUrl,
                    osisXml = outputter.outputString(fragment)
                ))
            } else {
                typedSuccess(Result(
                    book = args.book,
                    bookName = book.name,
                    key = args.key,
                    keyName = resolvedKey.name,
                    linkUrl = linkUrl,
                    text = OsisToPlainText.convert(fragment, injectAnchors = true)
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to read content: ${e.message}", "READ_ERROR")
        }
    }
}
