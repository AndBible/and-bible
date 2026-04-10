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

import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.resolveMyDocument
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for listing pages of a My Documents book, optionally with content.
 */
object GetMyDocumentPagesTool : Tool {
    @Serializable
    data class Args(
        val documentId: IdType = IdType.empty(),
        val initials: String = "",
        val includeContent: Boolean = false
    )

    @Serializable
    data class PageInfo(
        val id: IdType,
        val title: String,
        val pageKey: String,
        val contentType: String,
        val orderNumber: Int,
        val content: String? = null
    )

    @Serializable
    data class Result(
        val documentId: IdType,
        val documentName: String,
        val initials: String,
        val pageCount: Int,
        val pages: List<PageInfo>
    )

    override val agentTool = AgentTool.GET_MY_DOCUMENT_PAGES
    override val category = ToolCategory.MY_DOCUMENTS
    override val displayNameResId = R.string.tool_get_my_document_pages

    override val description = """
        List pages of a My Documents book. Identify the document by documentId or initials.
        Set includeContent=true to also return raw page content (default: titles only).
        Use getMyDocuments first to find document IDs and initials.

        NOTE: This tool returns raw source content (Markdown/HTML/OSIS) without anchor markers.
        Use this when you need raw content for editing (with editMyDocumentPage).
        To READ formatted content with ordinal anchors ([§N]) for citation, use getGenBookContent instead
        — My Documents are general books and work with getGenBookKeys/getGenBookContent.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          documentId:
            type: string
            description: "ID of the document (from getMyDocuments result)"
          initials:
            type: string
            description: "Document initials (alternative to documentId), e.g. 'AIDocuments'"
          includeContent:
            type: boolean
            description: "If true, include page content in the response. Default: false (titles only)."
            default: false
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val initials = arguments.optString("initials", "").takeIf { it.isNotBlank() }
        val documentId = arguments.optString("documentId", "").takeIf { it.isNotBlank() }
        return initials ?: documentId?.take(8)
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return application.getString(R.string.tool_log_my_document_page_count, data.pageCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        val documentId = args.documentId.takeIf { !it.isEmpty }
        val initials = args.initials.takeIf { it.isNotBlank() }

        if (documentId == null && initials == null) {
            return ToolResult.error("Provide either documentId or initials", "MISSING_IDENTIFIER")
        }

        val document = resolveMyDocument(documentId, initials)
            ?: return ToolResult.error("Document not found", "DOCUMENT_NOT_FOUND")

        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

        val pages = if (args.includeContent) {
            dao.pagesWithContentForDocument(document.id).map { page ->
                PageInfo(
                    id = page.id,
                    title = page.title,
                    pageKey = page.pageKey,
                    contentType = page.contentType.name,
                    orderNumber = page.orderNumber,
                    content = page.content
                )
            }
        } else {
            dao.pagesForDocument(document.id).map { page ->
                PageInfo(
                    id = page.id,
                    title = page.title,
                    pageKey = page.pageKey,
                    contentType = page.contentType.name,
                    orderNumber = page.orderNumber
                )
            }
        }

        return typedSuccess(Result(
            documentId = document.id,
            documentName = document.name,
            initials = document.initials,
            pageCount = pages.size,
            pages = pages
        ))
    }
}
