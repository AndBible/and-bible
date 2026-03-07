/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for finishing the agent execution and opening a StudyPad.
 *
 * Use this tool when the agent has created or modified a StudyPad and wants
 * to open it as the final result. The StudyPad should already exist (created
 * via createLabel + addStudyPadEntry tools earlier in the session).
 */
object FinishWithStudyPadTool : Tool {
    override val name = "finishWithStudyPad"
    override val displayNameResId = R.string.tool_finish_with_study_pad

    override val description = """
        Finish the current task and open a StudyPad (journal).
        Use this when you have created or modified a StudyPad and want to show it to the user.
        The StudyPad must already exist — create it first using createLabel + addStudyPadEntry tools.

        Call this tool as your final action when:
        - You've created a new StudyPad with content for the user
        - You've added entries to an existing StudyPad
        - The user asked for study notes organized as a StudyPad
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: "ID of the StudyPad (label) to open"
          scrollToEntryId:
            type: string
            description: "Optional ID of a bookmark or text entry to scroll to within the StudyPad"
          message:
            type: string
            description: "A brief message confirming what was done (shown in the agent log)"
        required: [labelId, message]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(labelId)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val labelId = arguments.optString("labelId", "")
        val scrollToEntryId = arguments.optString("scrollToEntryId", "").takeIf { it.isNotBlank() }
        val message = arguments.optString("message", "StudyPad opened")

        if (labelId.isBlank()) {
            return ToolResult.error("labelId is required", "MISSING_LABEL_ID")
        }

        val labelIdType = try {
            IdType.fromString(labelId)
        } catch (e: Exception) {
            return ToolResult.error("Invalid labelId format: $labelId", "INVALID_LABEL_ID")
        }

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        dao.labelById(labelIdType)
            ?: return ToolResult.error("StudyPad not found: $labelId", "LABEL_NOT_FOUND")

        return ToolResult.success {
            put("finished", true)
            put("labelId", labelId)
            if (scrollToEntryId != null) {
                put("scrollToEntryId", scrollToEntryId)
            }
            put("message", message)
        }
    }
}
