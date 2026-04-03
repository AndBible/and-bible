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
import net.bible.android.database.mydocument.MyDocument
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.json.JSONObject

/**
 * Tool for creating a new My Documents book.
 * Always requires user permission.
 */
object CreateMyDocumentTool : Tool {
    @Serializable
    data class Args(
        val name: String = "",
        val description: String = ""
    )

    @Serializable
    data class Result(
        val id: IdType,
        val name: String,
        val initials: String,
        val description: String? = null
    )

    override val agentTool = AgentTool.CREATE_MY_DOCUMENT
    override val category = ToolCategory.MY_DOCUMENTS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_create_my_document

    override val description = """
        Create a new My Documents book. Use this to create a new document collection
        for organizing pages. For adding pages to the existing 'AI Documents' book,
        use addMyDocumentPage directly with initials='AIDocuments'.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          name:
            type: string
            description: "Name of the new document"
          description:
            type: string
            description: "Optional description of the document"
        required: [name]
    """)

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val name = arguments.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        return BibleApplication.application.getString(R.string.action_create_my_document, name)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        return arguments.optString("name", "").takeIf { it.isNotBlank() }
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.name.isBlank()) {
            return ToolResult.error("Missing required parameter: name")
        }

        return try {
            val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
            val initials = MyDocumentBookManager.generateInitials(args.name)
            val maxOrder = dao.maxDocumentOrderNumber() ?: -1

            val document = MyDocument(
                name = args.name,
                description = args.description,
                initials = initials,
                orderNumber = maxOrder + 1,
                sourcePromptId = context.promptId
            )
            dao.insert(document)
            MyDocumentBookManager.registerDocument(document)

            typedSuccess(Result(
                id = document.id,
                name = document.name,
                initials = document.initials,
                description = document.description
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to create document: ${e.message}", "CREATE_ERROR")
        }
    }
}
