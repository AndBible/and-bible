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
import net.bible.service.llm.tools.resolveMyDocument
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for finishing the agent execution and opening an existing My Documents page.
 *
 * Use this when the agent has created or edited a My Documents page and wants
 * to show it to the user. The page must already exist.
 */
object FinishWithMyDocumentPageTool : Tool {
    @Serializable
    data class Args(
        val pageId: IdType = IdType.empty(),
        val message: String = ""
    )

    @Serializable
    data class Result(
        val finished: Boolean,
        val documentInitials: String,
        val pageKey: String,
        val message: String
    )

    override val agentTool = AgentTool.FINISH_WITH_MY_DOCUMENT_PAGE
    override val category = ToolCategory.MY_DOCUMENTS
    override val displayNameResId = R.string.tool_finish_with_my_document_page

    override val description = """
        Finish the current task and open a My Documents page.
        Use this when you have created or edited a My Documents page and want to show it to the user.
        The page must already exist — create or edit it first using addMyDocumentPage or editMyDocumentPage.

        Call this tool as your final action when:
        - You've created a new page with addMyDocumentPage
        - You've edited an existing page with editMyDocumentPage
        - The user should see the result in a My Documents page
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          pageId:
            type: string
            description: "ID of the My Documents page to open (from addMyDocumentPage or getMyDocumentPages result)"
          message:
            type: string
            description: "A brief message confirming what was done (shown in the agent log)"
        required: [pageId, message]
    """)

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val pageIdStr = arguments.optString("pageId", "").takeIf { it.isNotBlank() } ?: return null
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = try { dao.pageById(IdType(pageIdStr)) } catch (_: Exception) { null }
        val pageName = page?.title ?: shortId(pageIdStr)
        return BibleApplication.application.getString(R.string.action_open_my_document_page, pageName)
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
        val message = args.message.ifBlank { BibleApplication.application.getString(R.string.llm_default_task_completed) }

        if (args.pageId.isEmpty) {
            return ToolResult.error("pageId is required", "MISSING_PAGE_ID")
        }

        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = dao.pageById(args.pageId)
            ?: return ToolResult.error("Page not found: ${args.pageId}", "PAGE_NOT_FOUND")

        val document = dao.documentById(page.documentId)
            ?: return ToolResult.error("Document not found for page: ${args.pageId}", "DOCUMENT_NOT_FOUND")

        return typedSuccess(Result(
            finished = true,
            documentInitials = document.initials,
            pageKey = page.pageKey,
            message = message
        ))
    }
}
