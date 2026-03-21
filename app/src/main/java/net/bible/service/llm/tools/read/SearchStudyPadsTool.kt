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

import net.bible.android.BibleApplication
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Tool for searching StudyPad content.
 *
 * Searches through all StudyPads for matching text in notes and text entries.
 */
object SearchStudyPadsTool : Tool {
    @Serializable
    data class Args(val query: String = "")

    @Serializable
    data class MatchInfo(val entryId: IdType, val entryType: String, val textSnippet: String)

    @Serializable
    data class StudyPadMatch(
        val labelId: IdType,
        val labelName: String,
        val matchCount: Int,
        val matches: List<MatchInfo>
    )

    @Serializable
    data class Result(val query: String, val studyPadCount: Int, val results: List<StudyPadMatch>)

    override val agentTool = AgentTool.SEARCH_STUDY_PADS
    override val category = ToolCategory.STUDY_PADS
    override val displayNameResId = R.string.tool_search_study_pads

    override val description = """
        Search for text across all StudyPads.
        Searches in StudyPad text entries and bookmark notes.
        Returns matching StudyPads with text snippets showing where matches were found.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          query:
            type: string
            description: The text to search for
        required: [query]
    """)

    private val bookmarkControl get() = BibleApplication.application.applicationComponent.bookmarkControl()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val query = arguments.optString("query", "").takeIf { it.isNotBlank() } ?: return null
        return "\"$query\""
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        return application.getString(R.string.tool_log_study_pad_count, (result.data as Result).studyPadCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val query = args.query

        if (query.isBlank()) {
            return ToolResult.error("Missing required parameter: query")
        }

        return try {
            val searchResults = bookmarkControl.searchStudyPadsByContent(query)

            val results = searchResults.map { sr ->
                StudyPadMatch(
                    labelId = sr.label.id,
                    labelName = sr.label.name,
                    matchCount = sr.matchCount,
                    matches = sr.matches.map { m -> MatchInfo(m.entryId, m.entryType.name, m.textSnippet) }
                )
            }

            typedSuccess(Result(query = query, studyPadCount = results.size, results = results))
        } catch (e: Exception) {
            ToolResult.error("Search failed: ${e.message}", "SEARCH_ERROR")
        }
    }
}
