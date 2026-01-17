/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.defaultLabelColor
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for creating a new label (category/StudyPad).
 *
 * Labels are used to organize bookmarks and can function as StudyPads.
 */
object CreateLabelTool : Tool {
    override val name = "createLabel"

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

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val name = arguments.optString("name", "")
        val color = if (arguments.has("color")) arguments.getInt("color") else defaultLabelColor

        if (name.isBlank()) {
            return ToolResult.error("Missing required parameter: name")
        }

        return try {
            // Check if label with same name exists
            val existingLabels = bookmarkControl.assignableLabels
            if (existingLabels.any { it.name.equals(name.trim(), ignoreCase = true) }) {
                return ToolResult.error("Label with name '$name' already exists", "LABEL_EXISTS")
            }

            // Create label using BookmarkControl (sends UI events)
            val label = Label(
                name = name.trim(),
                color = color,
                new = true
            )
            val savedLabel = bookmarkControl.insertOrUpdateLabel(label)

            ToolResult.success {
                put("id", savedLabel.id.toString())
                put("name", savedLabel.name)
                put("color", savedLabel.color)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to create label: ${e.message}", "CREATE_ERROR")
        }
    }
}
