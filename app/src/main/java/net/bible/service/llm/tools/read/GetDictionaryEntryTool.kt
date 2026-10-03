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

import android.net.Uri
import net.bible.android.BibleApplication
import net.bible.android.control.link.isGreekDef
import net.bible.android.control.link.isHebrewDef
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.sword.ContentFormat
import net.bible.service.sword.OsisToPlainText
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import kotlinx.serialization.Serializable
import net.bible.service.llm.tools.AiDocumentFilter
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
    @Serializable
    data class Args(
        val dictionary: String = "",
        val key: String = "",
        val format: ContentFormat = ContentFormat.TEXT
    )

    @Serializable
    data class Result(val dictionary: String, val dictionaryName: String, val key: String, val linkUrl: String, val text: String? = null, val osisXml: String? = null)

    override val agentTool = AgentTool.GET_DICTIONARY_ENTRY
    override val category = ToolCategory.BIBLE_SEARCH
    override val displayNameResId = R.string.tool_get_dictionary_entry

    override val description = """
        Look up an entry in a Bible dictionary, including Strong's dictionaries. Returns readable text by default.
        Use format='xml' for raw OSIS XML (useful for Strong's to see original language markup).
        Useful for looking up definitions of biblical terms, places, people, and Strong's numbers.
        For Strong's numbers, use H prefix for Hebrew (e.g., 'H430' for Elohim) or G prefix for Greek (e.g., 'G2316' for Theos).

        IMPORTANT: The result includes a 'linkUrl' field (already properly URL-encoded).
        When referencing dictionary entries in your response, ALWAYS use the linkUrl value
        directly in clickable links. Example: [G2316](strongs://G2316)

        CRITICAL: Convert ALL Strong's number references to clickable links in your response:
        - With prefix: G1234 → [G1234](strongs://G1234), H5678 → [H5678](strongs://H5678)
        - Without prefix (in dictionary content): If you're working with a Greek dictionary,
          bare numbers like "575" or "4724" refer to Greek entries → [G575](strongs://G575)
          Similarly for Hebrew dictionary → [H575](strongs://H575)
        - Example: "Derived from 575 and 4724" → "Derived from [G575](strongs://G575) and [G4724](strongs://G4724)"
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
          format:
            type: string
            enum: [text, xml]
            description: "Output format: 'text' (default) returns readable text. 'xml' returns raw OSIS XML with original language markup."
            default: text
        required: [dictionary, key]
    """)

    private val linkControl get() = BibleApplication.application.applicationComponent.linkControl()

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val dictionary = arguments.optString("dictionary", "").takeIf { it.isNotBlank() } ?: return null
        val key = arguments.optString("key", "").takeIf { it.isNotBlank() } ?: return null
        return "$dictionary: $key"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val dictionaryInitials = args.dictionary
        val key = args.key

        if (dictionaryInitials.isBlank()) {
            return ToolResult.error("Missing required parameter: dictionary")
        }
        if (key.isBlank()) {
            return ToolResult.error("Missing required parameter: key")
        }

        val dictionary = SwordDocumentFacade.getDocumentByInitials(dictionaryInitials)
            ?: return ToolResult.error("Dictionary not found: $dictionaryInitials", "DICT_NOT_FOUND")

        if (!AiDocumentFilter.isAllowed(dictionaryInitials)) {
            return ToolResult.error("Document excluded by user settings: $dictionaryInitials", "DOCUMENT_EXCLUDED")
        }

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
            val linkUrl = when {
                dictionary.isGreekDef || dictionary.isHebrewDef -> "strongs://$key"
                else -> "sword://${Uri.encode(dictionaryInitials)}/${Uri.encode(key)}"
            }

            if (args.format == ContentFormat.XML) {
                val outputter = XMLOutputter(Format.getRawFormat())
                typedSuccess(Result(
                    dictionary = dictionaryInitials,
                    dictionaryName = dictionary.name,
                    key = key,
                    linkUrl = linkUrl,
                    osisXml = outputter.outputString(fragment)
                ))
            } else {
                typedSuccess(Result(
                    dictionary = dictionaryInitials,
                    dictionaryName = dictionary.name,
                    key = key,
                    linkUrl = linkUrl,
                    text = OsisToPlainText.convert(fragment)
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to read dictionary entry: ${e.message}", "READ_ERROR")
        }
    }
}
