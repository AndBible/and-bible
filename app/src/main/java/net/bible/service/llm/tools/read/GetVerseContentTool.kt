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

import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.sword.ContentFormat
import net.bible.service.sword.OsisToPlainText
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.json.JSONObject

/**
 * Tool for reading verse content from Bible documents.
 *
 * Returns OSIS XML content for the specified verse reference.
 */
object GetVerseContentTool : Tool {
    @Serializable
    data class Args(
        val book: String = "",
        val verseRef: String = "",
        val format: ContentFormat = ContentFormat.TEXT
    )

    @Serializable
    data class Result(val book: String, val verseRef: String, val text: String? = null, val osisXml: String? = null)

    override val agentTool = AgentTool.GET_VERSE_CONTENT
    override val category = ToolCategory.BIBLE_SEARCH
    override val displayNameResId = R.string.tool_get_verse_content

    override val description = """
        Get verse content from a Bible translation. Returns readable text by default.
        Use format='xml' for raw OSIS XML with Strong's numbers and morphology (for word studies).
        Use OSIS format references like 'Matt.5.3', 'Gen.1.1-3', 'Gen.1' for entire chapter, or 'Gen.1-Gen.3' for multiple chapters.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          book:
            type: string
            description: "Book initials, e.g., 'KJV', 'ESV', 'NASB'. Use getInstalledDocuments to find available books."
          verseRef:
            type: string
            description: "OSIS verse reference, e.g., 'Matt.5.3', 'Gen.1.1-3', 'Rom.8.28-30'."
          format:
            type: string
            enum: [text, xml]
            description: "Output format: 'text' (default) returns readable text with light annotations (headings, footnotes). 'xml' returns raw OSIS XML with Strong's numbers, morphology, etc. Use 'xml' only for word studies or Strong's analysis."
            default: text
        required: [book, verseRef]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val book = arguments.optString("book", "")
        val verseRef = arguments.optString("verseRef", "")
        if (book.isBlank() || verseRef.isBlank()) return null
        return "$book: ${localizeVerseRef(verseRef)}"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val bookInitials = args.book
        val verseRef = args.verseRef

        if (bookInitials.isBlank()) {
            return ToolResult.error("Missing required parameter: book")
        }
        if (verseRef.isBlank()) {
            return ToolResult.error("Missing required parameter: verseRef")
        }

        val book = Books.installed().getBook(bookInitials) ?: return ToolResult.error(
            "Book not found: $bookInitials",
            "BOOK_NOT_FOUND"
        )

        if (!AiDocumentFilter.isAllowed(bookInitials)) {
            return ToolResult.error("Document excluded by user settings: $bookInitials", "DOCUMENT_EXCLUDED")
        }

        if (book !is SwordBook) {
            return ToolResult.error("Book is not a Bible: $bookInitials", "INVALID_BOOK_TYPE")
        }

        return try {
            val v11n = book.versification
            val key = PassageKeyFactory.instance().getKey(v11n, verseRef)
            val fragment = SwordContentFacade.readOsisFragment(book, key)

            if (args.format == ContentFormat.XML) {
                val outputter = XMLOutputter(Format.getRawFormat())
                typedSuccess(Result(
                    book = bookInitials,
                    verseRef = verseRef,
                    osisXml = outputter.outputString(fragment)
                ))
            } else {
                typedSuccess(Result(
                    book = bookInitials,
                    verseRef = verseRef,
                    text = OsisToPlainText.convert(fragment)
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to read verse content: ${e.message}", "READ_ERROR")
        }
    }
}
