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
import net.bible.android.activity.R
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for searching StudyPad content.
 *
 * Searches through all StudyPads for matching text in notes and text entries.
 */
object SearchStudyPadsTool : Tool {
    override val name = "searchStudyPads"
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
        if (result !is ToolResult.Success || result.data !is JSONObject) return null
        val data = result.data as JSONObject
        val count = data.optInt("studyPadCount", -1)
        return if (count >= 0) "$count study pads" else null
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val query = arguments.optString("query", "")

        if (query.isBlank()) {
            return ToolResult.error("Missing required parameter: query")
        }

        return try {
            val searchResults = bookmarkControl.searchStudyPadsByContent(query)

            val results = JSONArray()
            for (result in searchResults) {
                val matchesArray = JSONArray()
                for (match in result.matches) {
                    matchesArray.put(JSONObject().apply {
                        put("entryId", match.entryId.toString())
                        put("entryType", match.entryType.name)
                        put("textSnippet", match.textSnippet)
                    })
                }

                results.put(JSONObject().apply {
                    put("labelId", result.label.id.toString())
                    put("labelName", result.label.name)
                    put("matchCount", result.matchCount)
                    put("matches", matchesArray)
                })
            }

            ToolResult.success {
                put("query", query)
                put("studyPadCount", results.length())
                put("results", results)
            }
        } catch (e: Exception) {
            ToolResult.error("Search failed: ${e.message}", "SEARCH_ERROR")
        }
    }
}
