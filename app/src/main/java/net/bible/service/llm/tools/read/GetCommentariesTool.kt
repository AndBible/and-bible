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

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import net.bible.android.activity.databinding.DialogCommentaryFilterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
import net.bible.service.llm.tools.AiDocumentFilter
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

        val commentaries = AiDocumentFilter.filterAllowed(if (!args.commentaries.isNullOrEmpty()) {
            args.commentaries.mapNotNull { initials ->
                SwordDocumentFacade.getDocumentByInitials(initials) as? SwordBook
            }
        } else {
            SwordDocumentFacade.getBooks(BookCategory.COMMENTARY).filterIsInstance<SwordBook>()
        })

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

        val filterResult = filterByResponseSizeLimit(commentaryResults)
            ?: return ToolResult.error("User cancelled commentary selection", "USER_CANCELLED")

        return ToolResult.success {
            put("verseRef", verseRef)
            put("commentaryCount", filterResult.results.length())
            put("commentaries", filterResult.results)
            if (filterResult.excludedCommentaries.isNotEmpty()) {
                put("note", "The user chose to exclude the following commentaries from this response " +
                    "to save context space. Do NOT re-fetch them individually: " +
                    filterResult.excludedCommentaries.joinToString(", "))
            }
        }
    }

    /**
     * If commentaryMaxResponseChars is set and the total response exceeds it,
     * shows a selection dialog so the user can choose which commentaries to include.
     * Returns the original array if no limit is set, limit is not exceeded, or user cancels.
     */
    private data class CommentaryInfo(
        val index: Int,
        val initials: String,
        val name: String,
        val verseRanges: String,
        val sizeChars: Int
    )

    private data class FilterResult(
        val results: JSONArray,
        val excludedCommentaries: List<String>
    )

    /** Approximate token count from character count (rough average: 1 token ≈ 4 chars). */
    private fun estimateTokens(chars: Int): Int = (chars / 4).coerceAtLeast(1)

    /** Extract verse ranges covered by a commentary result object. */
    private fun extractVerseRanges(commentaryObj: JSONObject): String {
        val entries = commentaryObj.optJSONArray("entries") ?: return ""
        val ranges = (0 until entries.length()).mapNotNull { i ->
            entries.getJSONObject(i).optString("verseRange", "").takeIf { it.isNotBlank() }
        }
        return when {
            ranges.isEmpty() -> ""
            ranges.size == 1 -> ranges[0]
            else -> "${ranges.first()}–${ranges.last()}"
        }
    }

    /**
     * If commentaryMaxResponseChars is set and the total response exceeds it,
     * shows a selection dialog so the user can choose which commentaries to include.
     * Returns null if the user cancels (meaning: abort the tool call entirely).
     */
    private suspend fun filterByResponseSizeLimit(commentaryResults: JSONArray): FilterResult? {
        val thresholdTokens = CommonUtils.settings.commentaryMaxResponseTokens
        if (thresholdTokens <= 0 || commentaryResults.length() == 0) {
            return FilterResult(commentaryResults, emptyList())
        }

        val infos = (0 until commentaryResults.length()).map { i ->
            val obj = commentaryResults.getJSONObject(i)
            CommentaryInfo(
                index = i,
                initials = obj.getString("initials"),
                name = obj.getString("name"),
                verseRanges = extractVerseRanges(obj),
                sizeChars = obj.toString().length
            )
        }
        val totalTokens = estimateTokens(infos.sumOf { it.sizeChars })
        if (totalTokens <= thresholdTokens) return FilterResult(commentaryResults, emptyList())

        val activity = CurrentActivityHolder.currentActivity
            ?: return FilterResult(commentaryResults, emptyList())

        // Sort by size descending for display
        val sorted = infos.sortedByDescending { it.sizeChars }

        // Show selection dialog — returns null if user cancels
        val selected = withContext(Dispatchers.Main) {
            showFilterDialog(activity, sorted, thresholdTokens)
        } ?: return null  // User cancelled

        // Build filtered JSONArray preserving original order
        val selectedInitials = selected.map { it.initials }.toSet()
        val excluded = infos.filter { it.initials !in selectedInitials }.map { it.initials }
        val filtered = JSONArray()
        for (i in 0 until commentaryResults.length()) {
            val obj = commentaryResults.getJSONObject(i)
            if (obj.getString("initials") in selectedInitials) {
                filtered.put(obj)
            }
        }
        return FilterResult(filtered, excluded)
    }

    /**
     * Shows a custom dialog for selecting commentaries with a live token total.
     * Returns the selected items, or null if the user cancelled.
     */
    private suspend fun showFilterDialog(
        context: Context,
        items: List<CommentaryInfo>,
        thresholdTokens: Int
    ): List<CommentaryInfo>? = suspendCoroutine { continuation ->
        val binding = DialogCommentaryFilterBinding.inflate(LayoutInflater.from(context))
        val checkedItems = BooleanArray(items.size) { true }
        val itemTokens = items.map { estimateTokens(it.sizeChars) }

        fun updateTotal() {
            val selectedTokens = itemTokens.filterIndexed { i, _ -> checkedItems[i] }.sum()
            binding.totalTokens.text = context.getString(
                R.string.commentary_filter_total_tokens,
                "%,d".format(selectedTokens),
                "%,d".format(thresholdTokens)
            )
        }

        binding.description.text = context.getString(
            R.string.commentary_filter_dialog_message,
            "%,d".format(thresholdTokens)
        )
        updateTotal()

        val itemNames = items.map { info ->
            val tokens = estimateTokens(info.sizeChars)
            val rangeStr = if (info.verseRanges.isNotEmpty()) " [${info.verseRanges}]" else ""
            "${info.name}$rangeStr (~%,d tokens)".format(tokens)
        }.toTypedArray()

        val adapter = android.widget.ArrayAdapter(
            context, android.R.layout.simple_list_item_multiple_choice, itemNames
        )
        binding.commentaryList.adapter = adapter
        binding.commentaryList.choiceMode = android.widget.AbsListView.CHOICE_MODE_MULTIPLE
        for (i in items.indices) {
            binding.commentaryList.setItemChecked(i, true)
        }
        binding.commentaryList.setOnItemClickListener { _, _, position, _ ->
            checkedItems[position] = binding.commentaryList.isItemChecked(position)
            updateTotal()
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.commentary_filter_dialog_title)
            .setView(binding.root)
            .setCancelable(true)
            .setOnCancelListener { continuation.resume(null) }
            .create()

        binding.btnOk.setOnClickListener {
            dialog.dismiss()
            val selected = items.filterIndexed { index, _ -> checkedItems[index] }
            continuation.resume(selected)
        }
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
            continuation.resume(null)
        }

        dialog.show()
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
