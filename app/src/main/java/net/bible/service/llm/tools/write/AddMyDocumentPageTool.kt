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

package net.bible.service.llm.tools.write

import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.resolveMyDocument
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.isAIDocument
import org.json.JSONObject
import java.util.Locale

/**
 * Tool for adding a new page to a My Documents book.
 *
 * Permission is required except when adding to the AI Documents book,
 * which is the standard destination for AI-generated content.
 */
object AddMyDocumentPageTool : Tool {
    @Serializable
    data class Args(
        val documentId: IdType = IdType.empty(),
        val initials: String = "",
        val title: String = "",
        val content: String = "",
        val contentType: MyDocumentContentType = MyDocumentContentType.MARKDOWN
    )

    @Serializable
    data class Result(
        val pageId: IdType,
        val documentId: IdType,
        val initials: String,
        val title: String,
        val pageKey: String,
        val contentType: String,
        val orderNumber: Int
    )

    override val agentTool = AgentTool.ADD_MY_DOCUMENT_PAGE
    override val category = ToolCategory.MY_DOCUMENTS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_add_my_document_page

    override val description = """
        Add a new page to a My Documents book.
        Identify the target document by documentId or initials.
        Use initials='AIDocuments' to add to the 'AI Documents' book (no permission needed).
        Content type defaults to MARKDOWN.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          documentId:
            type: string
            description: "ID of the target document"
          initials:
            type: string
            description: "Document initials (alternative to documentId), e.g. 'AIDocuments'"
          title:
            type: string
            description: "Page title"
          content:
            type: string
            description: "Page content (markdown by default)"
          contentType:
            type: string
            enum: [MARKDOWN, HTML, OSIS]
            description: "Content type. Default: MARKDOWN."
            default: MARKDOWN
        required: [title, content]
    """)

    override fun requiresPermissionForCall(arguments: JSONObject, context: AgentContext): Boolean {
        val initials = arguments.optString("initials", "")
        if (initials == MyDocumentBookManager.AI_DOCUMENTS_INITIALS) return false

        val documentIdStr = arguments.optString("documentId", "")
        if (documentIdStr.isNotBlank()) {
            val doc = try { resolveMyDocument(IdType(documentIdStr), null) } catch (_: Exception) { null }
            if (doc?.isAIDocument == true) return false
        }
        return true
    }

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val title = arguments.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        val initials = arguments.optString("initials", "").takeIf { it.isNotBlank() }
        val docName = initials ?: arguments.optString("documentId", "").take(8)
        return BibleApplication.application.getString(R.string.action_add_my_document_page, title, docName)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val title = arguments.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        val initials = arguments.optString("initials", "").takeIf { it.isNotBlank() } ?: ""
        return if (initials.isNotBlank()) "$title → $initials" else title
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.title.isBlank()) {
            return ToolResult.error("Missing required parameter: title")
        }
        if (args.content.isBlank()) {
            return ToolResult.error("Missing required parameter: content")
        }

        val documentId = args.documentId.takeIf { !it.isEmpty }
        val initials = args.initials.takeIf { it.isNotBlank() }

        // Resolve document — special handling for AI Documents
        val document = if (initials == MyDocumentBookManager.AI_DOCUMENTS_INITIALS || documentId == null && initials == null) {
            // Default to AI Documents if no document specified, or if explicitly targeting it
            MyDocumentBookManager.getOrCreateAIDocument()
        } else {
            resolveMyDocument(documentId, initials)
                ?: return ToolResult.error("Document not found", "DOCUMENT_NOT_FOUND")
        }

        return try {
            val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
            val content = normalizeLlmText(args.content)
            val pageId = IdType()
            val page = MyDocumentPage(
                id = pageId,
                documentId = document.id,
                title = args.title,
                pageKey = "page_$pageId",
                contentType = args.contentType,
                orderNumber = (dao.maxOrderNumber(document.id) ?: -1) + 1,
                sourcePromptId = context.promptId,
                languageCode = Locale.getDefault().language
            )
            dao.insertPageWithContent(page, content)
            MyDocumentBookManager.refreshDocument(document.initials)
            // No AiDocPagesChangedEvent here: insertPageWithContent doesn't create
            // AiPageCacheEntry, so no marker is generated. The event is posted by
            // savePageFromAiResponse() which does create the cache entry.

            typedSuccess(Result(
                pageId = pageId,
                documentId = document.id,
                initials = document.initials,
                title = args.title,
                pageKey = page.pageKey,
                contentType = args.contentType.name,
                orderNumber = page.orderNumber
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to add page: ${e.message}", "ADD_ERROR")
        }
    }
}
