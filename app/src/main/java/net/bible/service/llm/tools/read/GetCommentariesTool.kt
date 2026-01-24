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

package net.bible.service.llm.tools.read

import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for getting commentary entries for a verse.
 *
 * Returns commentary content from all installed commentaries.
 */
object GetCommentariesTool : Tool {
    override val name = "getCommentaries"

    override val description = """
        Get commentary entries for a verse reference from all installed commentaries.
        Returns OSIS XML content from each commentary that has content for the specified verse.
        Useful for gathering scholarly insights and interpretations.

        IMPORTANT: Each entry includes 'linkUrl'. When citing commentaries in your response,
        ALWAYS create clickable links. Example: [MHC](sword://MHC/Matt.5.3)
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          verseRef:
            type: string
            description: "OSIS verse reference, e.g., 'Matt.5.3', 'Gen.1.1', 'Rom.8.28'"
          commentaries:
            type: array
            items:
              type: string
            description: Optional list of commentary initials to query. If not specified, queries all installed commentaries.
        required: [verseRef]
    """)

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val verseRef = arguments.optString("verseRef", "")
        val commentariesArray = arguments.optJSONArray("commentaries")

        if (verseRef.isBlank()) {
            return ToolResult.error("Missing required parameter: verseRef")
        }

        // Get commentaries to query
        val commentaries = if (commentariesArray != null && commentariesArray.length() > 0) {
            (0 until commentariesArray.length()).mapNotNull { idx ->
                val initials = commentariesArray.getString(idx)
                SwordDocumentFacade.getDocumentByInitials(initials) as? SwordBook
            }
        } else {
            SwordDocumentFacade.getBooks(BookCategory.COMMENTARY).filterIsInstance<SwordBook>()
        }

        if (commentaries.isEmpty()) {
            return ToolResult.error("No commentaries available", "NO_COMMENTARIES")
        }

        val results = JSONArray()
        val outputter = XMLOutputter(Format.getRawFormat())

        for (commentary in commentaries) {
            try {
                val v11n = commentary.versification
                val key = PassageKeyFactory.instance().getKey(v11n, verseRef)

                // Try to read content - will throw if not available
                val fragment = SwordContentFacade.readOsisFragment(commentary, key)
                val osisXml = outputter.outputString(fragment)

                // Skip empty fragments
                if (osisXml.isBlank() || osisXml == "<div/>") {
                    continue
                }

                val linkUrl = "sword://${commentary.initials}/$verseRef"
                results.put(JSONObject().apply {
                    put("commentary", commentary.initials)
                    put("name", commentary.name)
                    put("linkUrl", linkUrl)
                    put("osisXml", osisXml)
                })
            } catch (e: Exception) {
                // Skip this commentary if there's an error
                continue
            }
        }

        return ToolResult.success {
            put("verseRef", verseRef)
            put("commentaryCount", results.length())
            put("entries", results)
        }
    }
}
