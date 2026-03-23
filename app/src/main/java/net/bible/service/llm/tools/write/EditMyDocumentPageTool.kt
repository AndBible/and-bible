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
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.normalizeLlmText
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.android.control.event.ABEventBus
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.json.JSONObject

/**
 * Tool for editing an existing My Documents page (title and/or content).
 *
 * Permission is required except for pages created in the same agent session
 * (tracked via [AgentContext.createdPageIds]).
 */
object EditMyDocumentPageTool : Tool {
    @Serializable
    data class Args(
        val pageId: IdType = IdType.empty(),
        val title: String? = null,
        val content: String? = null,
        val orderNumber: Int? = null
    )

    @Serializable
    data class Result(
        val pageId: IdType,
        val documentId: IdType,
        val title: String,
        val contentType: String,
        val orderNumber: Int
    )

    override val agentTool = AgentTool.EDIT_MY_DOCUMENT_PAGE
    override val category = ToolCategory.MY_DOCUMENTS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_edit_my_document_page

    override val description = """
        Edit an existing My Documents page. Can update the title, content, and/or order number.
        At least one of title, content, or orderNumber must be provided.
        Pages created in the same session can be edited without permission.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          pageId:
            type: string
            description: "ID of the page to edit (from getMyDocumentPages result)"
          title:
            type: string
            description: "New title for the page (optional)"
          content:
            type: string
            description: "New content for the page (optional)"
          orderNumber:
            type: integer
            description: "New position/order number for the page (optional, 0-based)"
        required: [pageId]
    """)

    override fun requiresPermissionForCall(arguments: JSONObject, context: AgentContext): Boolean {
        val pageIdStr = arguments.optString("pageId", "")
        if (pageIdStr.isBlank()) return true
        return try {
            IdType(pageIdStr) !in context.createdPageIds
        } catch (_: Exception) {
            true
        }
    }

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val pageIdStr = arguments.optString("pageId", "").takeIf { it.isNotBlank() } ?: return null
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = try { dao.pageById(IdType(pageIdStr)) } catch (_: Exception) { null }
        val pageName = page?.title ?: shortId(pageIdStr)
        return BibleApplication.application.getString(R.string.action_edit_my_document_page, pageName)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val pageId = arguments.optString("pageId", "").takeIf { it.isNotBlank() } ?: return null
        val hasTitle = arguments.optString("title", "").isNotBlank()
        val hasContent = arguments.optString("content", "").isNotBlank()
        val parts = mutableListOf(shortId(pageId))
        if (hasTitle) parts.add("title")
        if (hasContent) parts.add("content")
        return parts.joinToString(", ")
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.pageId.isEmpty) {
            return ToolResult.error("Missing required parameter: pageId")
        }
        if (args.title == null && args.content == null && args.orderNumber == null) {
            return ToolResult.error("At least one of title, content, or orderNumber must be provided")
        }

        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = dao.pageById(args.pageId)
            ?: return ToolResult.error("Page not found: ${args.pageId}", "PAGE_NOT_FOUND")

        return try {
            if (args.title != null) {
                page.title = args.title
            }
            if (args.orderNumber != null) {
                page.orderNumber = args.orderNumber
            }
            page.updatedAt = System.currentTimeMillis()
            dao.update(page)

            if (args.content != null) {
                val content = normalizeLlmText(args.content)
                dao.insertOrUpdateContent(MyDocumentPageContent(pageId = args.pageId, content = content))
            }

            val document = dao.documentById(page.documentId)
            val cacheEntry = dao.getCacheEntry(args.pageId)
            if (document != null) {
                MyDocumentBookManager.refreshDocument(document.initials)
                val start = cacheEntry?.kjvOrdinalStart
                val end = cacheEntry?.kjvOrdinalEnd
                if (start != null && end != null) {
                    val markers = dao.aiDocMarkersForRange(start, end)
                    ABEventBus.post(AiDocPagesChangedEvent(markers))
                }
            }

            typedSuccess(Result(
                pageId = args.pageId,
                documentId = page.documentId,
                title = page.title,
                contentType = page.contentType.name,
                orderNumber = page.orderNumber
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to edit page: ${e.message}", "EDIT_ERROR")
        }
    }
}
