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
import net.bible.android.database.IdType
import net.bible.android.activity.R
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for listing all available labels.
 *
 * Labels are used to organize bookmarks and create StudyPads.
 */
object GetAllLabelsTool : Tool {
    @Serializable
    data class LabelInfo(
        val id: IdType,
        val name: String,
        val color: Int,
        val isFavourite: Boolean
    )

    @Serializable
    data class Result(val labelCount: Int, val labels: List<LabelInfo>)

    override val agentTool = AgentTool.GET_ALL_LABELS
    override val category = ToolCategory.LABELS
    override val displayNameResId = R.string.tool_get_all_labels

    override val description = """
        Get all available labels (tags/categories).
        Labels are used to organize bookmarks and create StudyPads.
        Each label can be used as a StudyPad for collecting related bookmarks and notes.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties: {}
    """)

    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        return application.getString(R.string.tool_log_label_count, (result.data as Result).labelCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        return try {
            val labels = dao.allLabelsSortedByName()

            val results = labels
                .filter { !it.isSpecialLabel }
                .map { LabelInfo(
                    id = it.id,
                    name = it.name,
                    color = it.color,
                    isFavourite = it.favourite
                ) }

            typedSuccess(Result(labelCount = results.size, labels = results))
        } catch (e: Exception) {
            ToolResult.error("Failed to get labels: ${e.message}", "READ_ERROR")
        }
    }
}
