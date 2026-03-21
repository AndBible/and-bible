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
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for deleting a label by ID.
 *
 * Optionally deletes bookmarks that would become orphaned (have no remaining labels).
 * Prevents deletion of special internal labels (AI, Speak, Unlabeled, etc.).
 */
object DeleteLabelTool : Tool {
    @Serializable
    data class Args(
        val labelId: IdType = IdType.empty(),
        val deleteOrphanedBookmarks: Boolean = false
    )

    @Serializable
    data class Result(val labelId: IdType, val labelName: String, val deletedOrphanedBookmarks: Boolean)

    override val agentTool = AgentTool.DELETE_LABEL
    override val category = ToolCategory.LABELS

    override val description = """
        Delete a label by its ID.
        This permanently removes the label. Bookmarks with this label will lose the association.
        Set deleteOrphanedBookmarks to true to also delete bookmarks that would have no remaining labels.
        Cannot delete special internal labels (AI, Speak, Unlabeled, etc.).
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          labelId:
            type: string
            description: The ID of the label to delete
          deleteOrphanedBookmarks:
            type: boolean
            description: "If true, also delete bookmarks that would have no remaining labels after this label is removed. Default: false"
        required: [labelId]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_delete_label

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val labelId = arguments.optString("labelId", "").takeIf { it.isNotBlank() } ?: return null
        val label = try { bookmarkControl.labelById(IdType(labelId)) } catch (_: Exception) { null }
        val labelName = label?.name ?: shortId(labelId)
        return BibleApplication.application.getString(R.string.action_delete_label, labelName)
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

        if (args.labelId.isEmpty) {
            return ToolResult.error("Missing required parameter: labelId")
        }

        return try {
            val label = bookmarkControl.labelById(args.labelId)
                ?: return ToolResult.error("Label not found: ${args.labelId}", "LABEL_NOT_FOUND")

            if (label.isSpecialLabel) {
                return ToolResult.error("Cannot delete special internal label: ${label.name}", "SPECIAL_LABEL")
            }

            bookmarkControl.deleteLabels(listOf(args.labelId), args.deleteOrphanedBookmarks)

            typedSuccess(Result(
                labelId = args.labelId,
                labelName = label.name,
                deletedOrphanedBookmarks = args.deleteOrphanedBookmarks
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to delete label: ${e.message}", "DELETE_ERROR")
        }
    }
}
