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

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.IdType
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
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for updating the text content of a StudyPad journal text entry.
 */
object UpdateStudyPadTextEntryTool : Tool {
    @Serializable
    data class Args(
        val entryId: IdType = IdType.empty(),
        val text: String = ""
    )

    @Serializable
    data class Result(val entryId: IdType, val textLength: Int)

    override val agentTool = AgentTool.UPDATE_STUDYPAD_TEXT_ENTRY
    override val category = ToolCategory.STUDY_PADS

    override val description = """
        Update the text content of an existing StudyPad journal text entry.
        This replaces the entire text content.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          entryId:
            type: string
            description: The ID of the StudyPad text entry to update
          text:
            type: string
            description: The new text content
        required: [entryId, text]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_update_studypad_text_entry

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val entryId = arguments.optString("entryId", "").takeIf { it.isNotBlank() } ?: return null
        return BibleApplication.application.getString(R.string.action_update_studypad_text_entry, shortId(entryId))
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val entryId = arguments.optString("entryId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(entryId)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val text = normalizeLlmText(args.text)

        if (args.entryId.isEmpty) {
            return ToolResult.error("Missing required parameter: entryId")
        }
        if (text.isBlank()) {
            return ToolResult.error("Missing required parameter: text")
        }

        return try {
            bookmarkControl.updateStudyPadTextEntryText(args.entryId, text)

            typedSuccess(Result(
                entryId = args.entryId,
                textLength = text.length
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to update StudyPad text entry: ${e.message}", "UPDATE_ERROR")
        }
    }
}
