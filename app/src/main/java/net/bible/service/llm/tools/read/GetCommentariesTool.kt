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

import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.llm.tools.ContentFormat
import net.bible.service.llm.tools.OsisToPlainText
import net.bible.service.llm.tools.yamlToJson
import java.io.StringReader
import kotlinx.serialization.Serializable
import net.bible.service.common.useSaxBuilder
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool for getting commentary entries for a verse or verse range.
 *
 * Iterates through each verse in the range, fetches commentary content per verse,
 * and deduplicates identical content across consecutive verses (common when a
 * commentary covers a block of verses with a single entry).
 */
object GetCommentariesTool : Tool {
    @Serializable
    data class Args(
        val verseRef: String = "",
        val commentaries: List<String>? = null,
        val format: ContentFormat = ContentFormat.TEXT
    )

    override val agentTool = AgentTool.GET_COMMENTARIES
    override val displayNameResId = R.string.tool_get_commentaries

    override val description = """
        Get commentary entries for a verse or verse range from installed commentaries.
        Returns readable text by default. Use format='xml' for raw OSIS XML (rarely needed for commentaries).
        Supports verse ranges (e.g. 'Matt.5.1-10') — iterates through each verse and deduplicates
        identical content that commentaries repeat across consecutive verses.

        IMPORTANT: Each entry includes 'linkUrl'. When citing commentaries in your response,
        ALWAYS create clickable links. Example: [MHC](sword://MHC/Matt.5.3)
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          verseRef:
            type: string
            description: "OSIS verse reference or range, e.g., 'Matt.5.3', 'Matt.5.1-10', 'Gen.1.1-3', 'Rom.8.28'"
          commentaries:
            type: array
            items:
              type: string
            description: Optional list of commentary initials to query. If not specified, queries all installed commentaries.
          format:
            type: string
            enum: [text, xml]
            description: "Output format: 'text' (default) returns readable text. 'xml' returns raw OSIS XML."
            default: text
        required: [verseRef]
    """)

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val verseRef = arguments.optString("verseRef", "").takeIf { it.isNotBlank() } ?: return null
        return localizeVerseRef(verseRef)
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is JSONObject) return null
        val data = result.data as JSONObject
        val count = data.optInt("commentaryCount", -1)
        return if (count >= 0) "$count commentaries" else null
    }

    private data class ContentBlock(
        val startVerseRef: String,
        var endVerseRef: String,
        val osisXml: String
    )

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }
        val verseRef = args.verseRef

        if (verseRef.isBlank()) {
            return ToolResult.error("Missing required parameter: verseRef")
        }

        val commentaries = if (!args.commentaries.isNullOrEmpty()) {
            args.commentaries.mapNotNull { initials ->
                SwordDocumentFacade.getDocumentByInitials(initials) as? SwordBook
            }
        } else {
            SwordDocumentFacade.getBooks(BookCategory.COMMENTARY).filterIsInstance<SwordBook>()
        }

        if (commentaries.isEmpty()) {
            return ToolResult.error("No commentaries available", "NO_COMMENTARIES")
        }

        val outputter = XMLOutputter(Format.getRawFormat())
        val commentaryResults = JSONArray()

        for (commentary in commentaries) {
            try {
                val v11n = commentary.versification
                val key = PassageKeyFactory.instance().getKey(v11n, verseRef)

                // Collect individual verses from the key
                val verses = collectVerses(key)
                if (verses.isEmpty()) continue

                // Fetch content for each verse and deduplicate consecutive identical entries
                val blocks = mutableListOf<ContentBlock>()
                var currentBlock: ContentBlock? = null

                for (verse in verses) {
                    val osisXml = try {
                        val fragment = SwordContentFacade.readOsisFragment(commentary, verse)
                        val xml = outputter.outputString(fragment)
                        if (xml.isBlank() || xml == "<div/>") null else xml
                    } catch (_: Exception) {
                        null
                    }

                    if (osisXml == null) {
                        // No content for this verse — flush current block
                        if (currentBlock != null) {
                            blocks.add(currentBlock)
                            currentBlock = null
                        }
                        continue
                    }

                    val verseOsisId = verse.osisID

                    if (currentBlock != null && currentBlock.osisXml == osisXml) {
                        // Same content as previous verse — extend the range
                        currentBlock = currentBlock.copy(endVerseRef = verseOsisId)
                    } else {
                        // Different content — flush previous block and start new one
                        if (currentBlock != null) {
                            blocks.add(currentBlock)
                        }
                        currentBlock = ContentBlock(
                            startVerseRef = verseOsisId,
                            endVerseRef = verseOsisId,
                            osisXml = osisXml
                        )
                    }
                }
                // Flush last block
                if (currentBlock != null) {
                    blocks.add(currentBlock)
                }

                if (blocks.isEmpty()) continue

                val useXml = args.format == ContentFormat.XML
                val entries = JSONArray()
                for (block in blocks) {
                    val rangeRef = if (block.startVerseRef == block.endVerseRef) {
                        block.startVerseRef
                    } else {
                        "${block.startVerseRef}-${block.endVerseRef}"
                    }
                    entries.put(JSONObject().apply {
                        put("verseRange", rangeRef)
                        put("linkUrl", "sword://${commentary.initials}/${block.startVerseRef}")
                        if (useXml) {
                            put("osisXml", block.osisXml)
                        } else {
                            val fragment = useSaxBuilder { it.build(StringReader(block.osisXml)).rootElement }
                            put("text", OsisToPlainText.convert(fragment))
                        }
                    })
                }

                commentaryResults.put(JSONObject().apply {
                    put("initials", commentary.initials)
                    put("name", commentary.name)
                    put("entries", entries)
                })
            } catch (_: Exception) {
                continue
            }
        }

        return ToolResult.success {
            put("verseRef", verseRef)
            put("commentaryCount", commentaryResults.length())
            put("commentaries", commentaryResults)
        }
    }

    /** Collects individual [Verse] objects from a [Key], which may be a single verse or a range. */
    private fun collectVerses(key: Key): List<Verse> {
        val verses = mutableListOf<Verse>()
        for (subKey in key) {
            if (subKey is Verse) {
                verses.add(subKey)
            }
        }
        return verses
    }
}
