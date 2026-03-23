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
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.android.control.event.ABEventBus
import net.bible.service.sword.mydocument.AiDocPagesChangedEvent
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.json.JSONObject

/**
 * Tool for deleting a My Documents page.
 * Always requires user permission.
 */
object DeleteMyDocumentPageTool : Tool {
    @Serializable
    data class Args(
        val pageId: IdType = IdType.empty()
    )

    @Serializable
    data class Result(
        val deleted: Boolean,
        val pageId: IdType,
        val pageTitle: String
    )

    override val agentTool = AgentTool.DELETE_MY_DOCUMENT_PAGE
    override val category = ToolCategory.MY_DOCUMENTS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_delete_my_document_page

    override val description = """
        Delete a page from a My Documents book. This action is irreversible.
        Always requires user permission.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          pageId:
            type: string
            description: "ID of the page to delete (from getMyDocumentPages result)"
        required: [pageId]
    """)

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val pageIdStr = arguments.optString("pageId", "").takeIf { it.isNotBlank() } ?: return null
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = try { dao.pageById(IdType(pageIdStr)) } catch (_: Exception) { null }
        val pageName = page?.title ?: shortId(pageIdStr)
        return BibleApplication.application.getString(R.string.action_delete_my_document_page, pageName)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val pageId = arguments.optString("pageId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(pageId)
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

        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = dao.pageById(args.pageId)
            ?: return ToolResult.error("Page not found: ${args.pageId}", "PAGE_NOT_FOUND")

        return try {
            val title = page.title
            val document = dao.documentById(page.documentId)

            dao.deletePageWithContent(page)

            if (document != null) {
                MyDocumentBookManager.refreshDocument(document.initials)
                ABEventBus.post(AiDocPagesChangedEvent(deletedPageIds = listOf(args.pageId)))
            }

            typedSuccess(Result(
                deleted = true,
                pageId = args.pageId,
                pageTitle = title
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to delete page: ${e.message}", "DELETE_ERROR")
        }
    }
}
