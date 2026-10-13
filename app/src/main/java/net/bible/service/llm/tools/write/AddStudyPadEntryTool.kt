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

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.bible.service.llm.LlmStrings
import org.koin.core.context.GlobalContext
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.TextContentType
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
import kotlinx.serialization.json.JsonObject
import net.bible.service.llm.tools.*

/**
 * Tool for adding a text entry to a StudyPad.
 *
 * StudyPads (labels) can contain both bookmark references and standalone text entries.
 */
object AddStudyPadEntryTool : Tool, KoinComponent {
    private val strings: LlmStrings by inject()
    @Serializable
    data class Args(
        val labelId: IdType = IdType.empty(),
        val text: String = "",
        val contentType: TextContentType = TextContentType.MARKDOWN,
        val orderNumber: Int = 0
    )

    @Serializable
    data class Result(
        val entryId: IdType,
        val labelId: IdType,
        val labelName: String,
        val textLength: Int,
        val contentType: String,
        val orderNumber: Int
    )

    override val agentTool = AgentTool.ADD_STUDY_PAD_ENTRY
    override val category = ToolCategory.STUDY_PADS

    override val description = """
        Add a text entry to a StudyPad (label).
        StudyPads can contain both bookmarks and standalone text notes.
        This creates a new text entry at the end of the StudyPad.
        The entry will be marked as AI-generated.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: The ID of the label/StudyPad to add the entry to
          text:
            type: string
            description: The text content of the entry
          contentType:
            type: string
            enum: [HTML, MARKDOWN]
            description: "Content type. Default: MARKDOWN for AI-generated content."
          orderNumber:
            type: integer
            description: "Optional position in the StudyPad. Default: add to end."
        required: [labelId, text]
    """)

    override val requiresPermission = true

    private val bookmarkControl get() = GlobalContext.get().get<net.bible.android.control.bookmark.BookmarkControl>()

    override suspend fun formatActionDescription(arguments: JsonObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val label = try { bookmarkControl.labelById(IdType(labelId)) } catch (_: Exception) { null }
        val labelName = label?.name ?: shortId(labelId)
        return strings.actionAddEntryToStudypad(labelName)
    }

    override fun formatArgsForLog(arguments: JsonObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val contentType = arguments.optString("contentType", "MARKDOWN")
        return "${shortId(labelId)} ($contentType)"
    }

    override suspend fun execute(arguments: JsonObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val text = normalizeLlmText(args.text)
        val orderNumber = args.orderNumber.takeIf { it != 0 }

        if (args.labelId.isEmpty) {
            return ToolResult.error("Missing required parameter: labelId")
        }
        if (text.isBlank()) {
            return ToolResult.error("Missing required parameter: text")
        }

        return try {
            // Check if label exists
            val label = bookmarkControl.labelById(args.labelId)
                ?: return ToolResult.error("Label not found: ${args.labelId}", "LABEL_NOT_FOUND")

            // Create entry using BookmarkControl (sends UI events)
            // If orderNumber is null, BookmarkControl adds to end
            val entry = bookmarkControl.createStudyPadEntryWithText(
                labelId = args.labelId,
                orderNumber = orderNumber,
                text = text,
                contentType = args.contentType,
                sourcePromptId = context.promptId
            )

            typedSuccess(Result(
                entryId = entry.id,
                labelId = args.labelId,
                labelName = label.name,
                textLength = text.length,
                contentType = args.contentType.name,
                orderNumber = entry.orderNumber
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to add StudyPad entry: ${e.message}", "ADD_ERROR")
        }
    }
}
