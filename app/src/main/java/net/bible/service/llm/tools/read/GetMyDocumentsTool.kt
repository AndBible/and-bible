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
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.isAIDocument
import org.json.JSONObject

/**
 * Tool for listing all My Documents books.
 *
 * Returns document metadata including page counts and highlights the AI Documents book
 * with top-level fields for easy access.
 */
object GetMyDocumentsTool : Tool {
    @Serializable
    data class DocumentInfo(
        val id: IdType,
        val name: String,
        val initials: String,
        val description: String? = null,
        val pageCount: Int,
        val isAIDocument: Boolean
    )

    @Serializable
    data class Result(
        val documentCount: Int,
        val aiDocumentId: IdType? = null,
        val aiDocumentInitials: String? = null,
        val documents: List<DocumentInfo>
    )

    override val agentTool = AgentTool.GET_MY_DOCUMENTS
    override val category = ToolCategory.MY_DOCUMENTS
    override val displayNameResId = R.string.tool_get_my_documents

    override val description = """
        List all My Documents books (user-created and AI-generated document collections).
        Returns document metadata including page counts.
        The 'AI Documents' book (where AI-generated pages are stored) is highlighted
        with aiDocumentId and aiDocumentInitials at the top level for easy access.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties: {}
    """)

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return application.getString(R.string.tool_log_my_document_count, data.documentCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val documents = dao.allDocuments()

        val documentInfos = documents.map { doc ->
            DocumentInfo(
                id = doc.id,
                name = doc.name,
                initials = doc.initials,
                description = doc.description,
                pageCount = dao.pageCount(doc.id),
                isAIDocument = doc.isAIDocument
            )
        }

        val aiDoc = documents.find { it.isAIDocument }

        return typedSuccess(Result(
            documentCount = documentInfos.size,
            aiDocumentId = aiDoc?.id,
            aiDocumentInitials = aiDoc?.initials ?: MyDocumentBookManager.AI_DOCUMENTS_INITIALS,
            documents = documentInfos
        ))
    }
}
