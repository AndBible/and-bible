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
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.index.IndexStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for listing installed documents.
 *
 * Returns information about installed Bibles, commentaries, dictionaries, etc.
 */
object GetInstalledDocumentsTool : Tool {
    @Serializable
    data class Args(val category: String = "")

    @Serializable
    data class DocumentInfo(
        val initials: String, val name: String, val category: String, val language: String,
        val isLocked: Boolean, val isIndexed: Boolean, val abbreviation: String,
        val hasStrongsNumbers: Boolean? = null
    )

    @Serializable
    data class Result(val documentCount: Int, val documents: List<DocumentInfo>)

    override val agentTool = AgentTool.GET_INSTALLED_DOCUMENTS
    override val category = ToolCategory.BIBLE_SEARCH
    override val displayNameResId = R.string.tool_get_installed_documents

    override val description = """
        Get a list of installed documents (Bibles, commentaries, dictionaries, etc.).
        Use this to find available documents before reading content.
        Can filter by category: BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS.
        Each document includes an isIndexed field — only indexed documents can be searched.
        Bible documents also include a hasStrongsNumbers field indicating whether the module
        contains Strong's concordance number annotations (useful for original language word studies).
        A Bible must be both indexed and have Strong's numbers to support Strong's number search.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          category:
            type: string
            enum: [BIBLE, COMMENTARY, DICTIONARY, GENERAL_BOOK, MAPS]
            description: Optional category filter. If not specified, returns all documents.
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val category = arguments.optString("category", "").takeIf { it.isNotBlank() }
        return category // null if no category filter (shows all)
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        return application.getString(R.string.tool_log_document_count, (result.data as Result).documentCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val categoryStr = args.category

        return try {
            val books = AiDocumentFilter.filterAllowed(if (categoryStr.isNotBlank()) {
                val category = try {
                    BookCategory.valueOf(categoryStr)
                } catch (e: IllegalArgumentException) {
                    return ToolResult.error("Invalid category: $categoryStr", "INVALID_CATEGORY")
                }
                SwordDocumentFacade.getBooks(category)
            } else {
                Books.installed().books
            })

            val results = books.map { book ->
                DocumentInfo(
                    initials = book.initials, name = book.name, category = book.bookCategory.name,
                    language = book.language?.code ?: "unknown", isLocked = book.isLocked,
                    isIndexed = book.indexStatus == IndexStatus.DONE, abbreviation = book.abbreviation,
                    hasStrongsNumbers = if (book.bookCategory == BookCategory.BIBLE)
                        book.hasFeature(FeatureType.STRONGS_NUMBERS) else null
                )
            }

            typedSuccess(Result(documentCount = results.size, documents = results))
        } catch (e: Exception) {
            ToolResult.error("Failed to get documents: ${e.message}", "READ_ERROR")
        }
    }
}
