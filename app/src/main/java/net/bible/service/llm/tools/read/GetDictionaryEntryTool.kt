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

import net.bible.android.BibleApplication
import net.bible.android.control.link.isGreekDef
import net.bible.android.control.link.isHebrewDef
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.json.JSONObject

/**
 * Tool for looking up dictionary entries.
 *
 * Returns OSIS XML content from a dictionary.
 */
object GetDictionaryEntryTool : Tool {
    override val name = "getDictionaryEntry"

    override val description = """
        Look up an entry in a Bible dictionary, including Strong's dictionaries.
        Returns the OSIS XML content for the dictionary entry.
        Useful for looking up definitions of biblical terms, places, people, and Strong's numbers.
        For Strong's numbers, use H prefix for Hebrew (e.g., 'H430' for Elohim) or G prefix for Greek (e.g., 'G2316' for Theos).

        IMPORTANT: The result includes a 'linkUrl' field. When referencing dictionary entries in your response,
        ALWAYS create clickable links using this URL. Example: [G2316](sword://StrongsGreek/G2316)

        When writing your response, convert ALL Strong's number references (G1234, H5678 format) to clickable links:
        [G1234](sword://StrongsGreek/G1234), [H5678](sword://StrongsHebrew/H5678)
        This includes references found within dictionary content itself.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          dictionary:
            type: string
            description: "Dictionary initials, e.g., 'StrongsHebrew', 'StrongsGreek', 'Eastons'. Use getInstalledDocuments to find available dictionaries."
          key:
            type: string
            description: "The dictionary key/term to look up. For Strong's dictionaries use format like 'H430', 'G2316'. For regular dictionaries use terms like 'Moses', 'Jerusalem'."
        required: [dictionary, key]
    """)

    private val linkControl get() = BibleApplication.application.applicationComponent.linkControl()

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val dictionaryInitials = arguments.optString("dictionary", "")
        val key = arguments.optString("key", "")

        if (dictionaryInitials.isBlank()) {
            return ToolResult.error("Missing required parameter: dictionary")
        }
        if (key.isBlank()) {
            return ToolResult.error("Missing required parameter: key")
        }

        val dictionary = SwordDocumentFacade.getDocumentByInitials(dictionaryInitials)
            ?: return ToolResult.error("Dictionary not found: $dictionaryInitials", "DICT_NOT_FOUND")

        if (dictionary.bookCategory != BookCategory.DICTIONARY) {
            return ToolResult.error("Book is not a dictionary: $dictionaryInitials", "INVALID_BOOK_TYPE")
        }

        return try {
            // Use LinkControl for Strong's dictionaries to handle various key formats
            val dictKey = if (dictionary.isHebrewDef || dictionary.isGreekDef) {
                linkControl.getStrongsKey(dictionary, key)?.key
                    ?: return ToolResult.error("Strong's key not found: $key", "KEY_NOT_FOUND")
            } else {
                dictionary.getKey(key)
                    ?: return ToolResult.error("Key not found in dictionary: $key", "KEY_NOT_FOUND")
            }

            val fragment = SwordContentFacade.readOsisFragment(dictionary, dictKey)
            val outputter = XMLOutputter(Format.getRawFormat())
            val osisXml = outputter.outputString(fragment)

            val linkUrl = "sword://$dictionaryInitials/$key"

            ToolResult.success {
                put("dictionary", dictionaryInitials)
                put("dictionaryName", dictionary.name)
                put("key", key)
                put("linkUrl", linkUrl)
                put("osisXml", osisXml)
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to read dictionary entry: ${e.message}", "READ_ERROR")
        }
    }
}
