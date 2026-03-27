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
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.defaultLabelColor
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.uniqueLabelName
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for creating a new label (category/StudyPad).
 *
 * Labels are used to organize bookmarks and can function as StudyPads.
 */
object CreateLabelTool : Tool {
    @Serializable
    data class Args(
        val name: String = "",
        val color: Int = 0
    )

    @Serializable
    data class Result(val id: IdType, val name: String, val color: Int)

    override val agentTool = AgentTool.CREATE_LABEL
    override val category = ToolCategory.LABELS

    override val description = """
        Create a new label (category/StudyPad).
        Labels are used to organize bookmarks into categories.
        Each label can also function as a StudyPad for collecting related notes.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          name:
            type: string
            description: The name of the label
          color:
            type: integer
            description: "Optional color as ARGB integer. Default: blue highlight color."
        required: [name]
    """)

    override val requiresPermission = true
    override val displayNameResId = R.string.tool_create_label

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val name = arguments.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        return BibleApplication.application.getString(R.string.action_create_label, name)
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val name = arguments.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        return "\"$name\""
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        return (result.data as Result).name.takeIf { it.isNotBlank() }?.let { "\"$it\"" }
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val name = args.name
        val color = if (args.color != 0) args.color else defaultLabelColor

        if (name.isBlank()) {
            return ToolResult.error("Missing required parameter: name")
        }

        return try {
            val existingNames = bookmarkControl.assignableLabels.map { it.name }
            val uniqueName = uniqueLabelName(name.trim(), existingNames)

            // Create label using BookmarkControl (sends UI events)
            val label = Label(
                name = uniqueName,
                color = color,
                new = true
            )
            val savedLabel = bookmarkControl.insertOrUpdateLabel(label)

            typedSuccess(Result(
                id = savedLabel.id,
                name = savedLabel.name,
                color = savedLabel.color
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to create label: ${e.message}", "CREATE_ERROR")
        }
    }
}
