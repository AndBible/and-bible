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
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for finishing the agent execution and opening a StudyPad.
 *
 * Use this tool when the agent has created or modified a StudyPad and wants
 * to open it as the final result. The StudyPad should already exist (created
 * via createLabel + addStudyPadEntry tools earlier in the session).
 */
object FinishWithStudyPadTool : Tool {
    @Serializable
    data class Args(
        val labelId: IdType = IdType.empty(),
        val scrollToEntryId: IdType = IdType.empty(),
        val message: String = ""
    )

    @Serializable
    data class Result(
        val finished: Boolean,
        val labelId: String,
        val scrollToEntryId: String? = null,
        val message: String
    )

    override val agentTool = AgentTool.FINISH_WITH_STUDY_PAD
    override val category = ToolCategory.STUDY_PADS
    override val displayNameResId = R.string.tool_finish_with_study_pad

    override val description = """
        Finish the current task and open a StudyPad.
        Use this when you have created or modified a StudyPad and want to show it to the user.
        The StudyPad must already exist — create it first using createLabel + addStudyPadEntry tools.
        You can also add bookmarks to a StudyPad by assigning the StudyPad's label to a bookmark using addBookmarkLabels.

        Call this tool as your final action when:
        - You've created a new StudyPad with content for the user
        - You've added entries to an existing StudyPad
        - You've added bookmarks to a StudyPad via label assignment
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

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val label = try { dao.labelById(IdType(labelId)) } catch (_: Exception) { null }
        val labelName = label?.name ?: shortId(labelId)
        return BibleApplication.application.getString(R.string.action_open_studypad, labelName)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        return shortId(labelId)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val message = args.message.ifBlank { BibleApplication.application.getString(R.string.llm_default_studypad_opened) }

        if (args.labelId.isEmpty) {
            return ToolResult.error(
                message = "labelId is required",
                code = "MISSING_LABEL_ID"
            )
        }

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        dao.labelById(args.labelId)
            ?: return ToolResult.error(
                message = "StudyPad not found: ${args.labelId}",
                code = "LABEL_NOT_FOUND"
            )

        return typedSuccess(Result(
            finished = true,
            labelId = args.labelId.toString(),
            scrollToEntryId = if (!args.scrollToEntryId.isEmpty) args.scrollToEntryId.toString() else null,
            message = message
        ))
    }
}
