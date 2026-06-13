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
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import net.bible.android.activity.databinding.DialogCommentaryFilterBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.common.CommonUtils
import net.bible.service.common.useSaxBuilder
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.agent.AgentPermissionWaitingEvent
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.sword.ContentFormat
import net.bible.service.sword.OsisToPlainText
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.localizeVerseRef
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import net.bible.android.control.page.renderCommentaryFragmentXml
import org.json.JSONObject
import java.io.StringReader
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    @Serializable
    data class CommentaryEntry(
        val verseRange: String,
        val linkUrl: String,
        val text: String? = null,
        val osisXml: String? = null
    )

    @Serializable
    data class CommentaryResult(
        val initials: String,
        val name: String,
        val abbreviation: String,
        val entries: List<CommentaryEntry>
    )

    @Serializable
    data class Result(
        val verseRef: String,
        val commentaryCount: Int,
        val commentaries: List<CommentaryResult>,
        val note: String? = null
    )

    override val agentTool = AgentTool.GET_COMMENTARIES
    override val category = ToolCategory.BIBLE_SEARCH
    override val displayNameResId = R.string.tool_get_commentaries

    override val description = """
        Get commentary entries for a verse or verse range from installed commentaries.
        Returns readable text by default. Use format='xml' for raw OSIS XML (rarely needed for commentaries).
        Supports verse ranges (e.g. 'Matt.5.1-10') — iterates through each verse and deduplicates
        identical content that commentaries repeat across consecutive verses.

        Each entry includes 'linkUrl' (already URL-encoded base path for the entry).
        To cite content, append an anchor fragment (#oN or #oN-M) to linkUrl as described
        in the system instructions. Commentary text includes anchor markers like [§N]
        at sentence boundaries — use these ordinal numbers in the anchor fragment.
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
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return application.getString(R.string.tool_log_commentary_count, data.commentaryCount)
    }

    /** A single verse's rendered content (plain text or OSIS XML), or null if it has no commentary. */
    internal data class RenderedVerse(val osisId: String, val content: String?)

    /** A run of consecutive verses that share identical rendered content. */
    internal data class RenderedBlock(
        val startVerseRef: String,
        val endVerseRef: String,
        val content: String
    )

    /**
     * Merges consecutive verses that share identical rendered content into a single block.
     *
     * Verses with null content (no commentary entry) act as separators: they flush the
     * current block and are dropped. Deduplication compares the final rendered content
     * (plain text or OSIS XML, depending on the requested format) rather than the raw OSIS
     * fragment, so semantically identical entries whose underlying XML differs only in
     * per-verse metadata (e.g. across a chapter boundary) still collapse into one block.
     */
    internal fun deduplicateConsecutiveBlocks(verses: List<RenderedVerse>): List<RenderedBlock> {
        val blocks = mutableListOf<RenderedBlock>()
        var current: RenderedBlock? = null
        for (verse in verses) {
            val content = verse.content
            if (content == null) {
                current?.let { blocks.add(it) }
                current = null
                continue
            }
            val cur = current
            current = if (cur != null && cur.content == content) {
                cur.copy(endVerseRef = verse.osisId)
            } else {
                if (cur != null) blocks.add(cur)
                RenderedBlock(verse.osisId, verse.osisId, content)
            }
        }
        current?.let { blocks.add(it) }
        return blocks
    }

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

        val commentaryResults = mutableListOf<CommentaryResult>()

        for (commentary in commentaries) {
            try {
                val v11n = commentary.versification
                val key = PassageKeyFactory.instance().getKey(v11n, verseRef)

                val verses = collectVerses(key)
                if (verses.isEmpty()) continue

                // Render each verse to its final form (text or XML), then merge consecutive
                // verses that share identical content. Dedup compares rendered content — not
                // the raw OSIS fragment — so a single commentary entry spanning a chapter
                // boundary collapses into one block instead of being emitted once per chapter.
                val useXml = args.format == ContentFormat.XML
                val renderedVerses = verses.map { verse ->
                    val content = try {
                        val xml = renderCommentaryFragmentXml(commentary, verse)
                        when {
                            xml == null -> null
                            useXml -> xml
                            else -> OsisToPlainText.convert(
                                useSaxBuilder { it.build(StringReader(xml)).rootElement },
                                injectAnchors = true
                            )
                        }
                    } catch (_: Exception) {
                        null
                    }
                    RenderedVerse(verse.osisID, content)
                }

                val blocks = deduplicateConsecutiveBlocks(renderedVerses)
                if (blocks.isEmpty()) continue

                val entries = blocks.map { block ->
                    val rangeRef = if (block.startVerseRef == block.endVerseRef) {
                        block.startVerseRef
                    } else {
                        "${block.startVerseRef}-${block.endVerseRef}"
                    }
                    val linkUrl = "sword://${Uri.encode(commentary.initials)}/${Uri.encode(block.startVerseRef)}"
                    if (useXml) {
                        CommentaryEntry(verseRange = rangeRef, linkUrl = linkUrl, osisXml = block.content)
                    } else {
                        CommentaryEntry(verseRange = rangeRef, linkUrl = linkUrl, text = block.content)
                    }
                }

                commentaryResults.add(CommentaryResult(
                    initials = commentary.initials,
                    name = commentary.name,
                    abbreviation = commentary.abbreviation,
                    entries = entries
                ))
            } catch (_: Exception) {
                continue
            }
        }

        val filterResult = filterByResponseSizeLimit(commentaryResults, context)
            ?: return ToolResult.error("User cancelled commentary selection", "USER_CANCELLED")

        // Note: avoid listing excluded commentary names — LLMs sometimes treat such lists
        // as a menu and re-fetch the excluded items despite negative instructions.
        // Prefer positive, name-free guidance.
        val note = when {
            filterResult.excludedCommentaries.isEmpty() -> null
            filterResult.results.isEmpty() ->
                "The user has chosen not to include any commentaries in this response. " +
                    "Answer based on your own knowledge of the passage."
            else ->
                "The user limited which commentaries are included to keep the response " +
                    "size manageable. Use only the commentaries provided above."
        }
        return typedSuccess(Result(
            verseRef = verseRef,
            commentaryCount = filterResult.results.size,
            commentaries = filterResult.results,
            note = note
        ))
    }

    // --- Response size limit filtering ---

    private data class CommentaryInfoForFilter(
        val initials: String,
        val name: String,
        val verseRanges: String,
        val estimatedChars: Int
    )

    private data class FilterResult(
        val results: List<CommentaryResult>,
        val excludedCommentaries: List<String>
    )

    /** Approximate token count from character count (rough average: 1 token ≈ 4 chars). */
    private fun estimateTokens(chars: Int): Int = (chars / 4).coerceAtLeast(1)

    /** Estimate serialized size of a commentary result in characters. */
    private fun estimateSize(result: CommentaryResult): Int {
        var size = result.initials.length + result.name.length + 50 // JSON overhead
        for (entry in result.entries) {
            size += entry.verseRange.length + entry.linkUrl.length + 30
            size += entry.text?.length ?: entry.osisXml?.length ?: 0
        }
        return size
    }

    /** Extract a summary verse range string from a commentary result's entries. */
    private fun extractVerseRanges(result: CommentaryResult): String {
        val ranges = result.entries.map { it.verseRange }.filter { it.isNotBlank() }
        return when {
            ranges.isEmpty() -> ""
            ranges.size == 1 -> ranges[0]
            else -> "${ranges.first()}–${ranges.last()}"
        }
    }

    /**
     * If commentaryMaxResponseTokens is set and the total response exceeds it,
     * shows a selection dialog so the user can choose which commentaries to include.
     * Returns null if the user cancels (meaning: abort the tool call entirely).
     */
    private suspend fun filterByResponseSizeLimit(commentaryResults: List<CommentaryResult>, context: AgentContext): FilterResult? {
        val thresholdTokens = CommonUtils.aiSettings.commentaryMaxResponseTokens
        if (thresholdTokens <= 0 || commentaryResults.isEmpty()) {
            return FilterResult(commentaryResults, emptyList())
        }

        val infos = commentaryResults.map { result ->
            CommentaryInfoForFilter(
                initials = result.initials,
                name = result.name,
                verseRanges = extractVerseRanges(result),
                estimatedChars = estimateSize(result)
            )
        }
        val totalTokens = estimateTokens(infos.sumOf { it.estimatedChars })
        if (totalTokens <= thresholdTokens) return FilterResult(commentaryResults, emptyList())

        var activity = CurrentActivityHolder.currentActivity
        if (activity == null) {
            Log.d("GetCommentariesTool", "No current activity for commentary filter dialog, waiting...")
            val workspaceId = context.workspaceId
            if (workspaceId != null) {
                ABEventBus.post(AgentPermissionWaitingEvent(workspaceId, waiting = true))
            }
            while (activity == null) {
                delay(500)
                activity = CurrentActivityHolder.currentActivity
            }
            if (workspaceId != null) {
                ABEventBus.post(AgentPermissionWaitingEvent(workspaceId, waiting = false))
            }
        }

        // Sort by size descending for display
        val sorted = infos.sortedByDescending { it.estimatedChars }

        val selected = withContext(Dispatchers.Main) {
            showFilterDialog(activity, sorted, thresholdTokens)
        } ?: return null  // User cancelled

        val selectedInitials = selected.map { it.initials }.toSet()
        val excluded = infos.filter { it.initials !in selectedInitials }.map { it.initials }
        val filtered = commentaryResults.filter { it.initials in selectedInitials }
        return FilterResult(filtered, excluded)
    }

    /**
     * Shows a custom dialog for selecting commentaries with a live token total.
     * Returns the selected items, or null if the user cancelled.
     */
    private suspend fun showFilterDialog(
        context: Context,
        items: List<CommentaryInfoForFilter>,
        thresholdTokens: Int
    ): List<CommentaryInfoForFilter>? = suspendCoroutine { continuation ->
        val binding = DialogCommentaryFilterBinding.inflate(LayoutInflater.from(context))
        val previouslyDeselected = CommonUtils.aiSettings.commentaryDeselected
        val checkedItems = BooleanArray(items.size) { items[it].initials !in previouslyDeselected }
        val itemTokens = items.map { estimateTokens(it.estimatedChars) }

        fun updateTotal() {
            val selectedTokens = itemTokens.filterIndexed { i, _ -> checkedItems[i] }.sum()
            binding.totalTokens.text = context.getString(
                R.string.commentary_filter_total_tokens,
                "%,d".format(selectedTokens),
                "%,d".format(thresholdTokens)
            )
        }

        fun syncListView() {
            for (i in items.indices) {
                binding.commentaryList.setItemChecked(i, checkedItems[i])
            }
            updateTotal()
        }

        binding.description.text = context.getString(
            R.string.commentary_filter_dialog_message,
            "%,d".format(thresholdTokens)
        )

        val itemNames = items.map { info ->
            val tokens = estimateTokens(info.estimatedChars)
            val rangeStr = if (info.verseRanges.isNotEmpty()) " [${info.verseRanges}]" else ""
            context.getString(R.string.commentary_filter_item, info.name, rangeStr, "%,d".format(tokens))
        }.toTypedArray()

        val adapter = android.widget.ArrayAdapter(
            context, android.R.layout.simple_list_item_multiple_choice, itemNames
        )
        binding.commentaryList.adapter = adapter
        binding.commentaryList.choiceMode = android.widget.AbsListView.CHOICE_MODE_MULTIPLE
        syncListView()

        binding.commentaryList.setOnItemClickListener { _, _, position, _ ->
            checkedItems[position] = binding.commentaryList.isItemChecked(position)
            updateTotal()
        }

        binding.btnSelectAll.setOnClickListener {
            checkedItems.fill(true)
            syncListView()
        }
        binding.btnSelectNone.setOnClickListener {
            checkedItems.fill(false)
            syncListView()
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.commentary_filter_dialog_title)
            .setView(binding.root)
            .setCancelable(true)
            .setOnCancelListener { continuation.resume(null) }
            .create()

        binding.btnOk.setOnClickListener {
            dialog.dismiss()
            val dialogInitials = items.map { it.initials }.toSet()
            val newDeselected = items.filterIndexed { i, _ -> !checkedItems[i] }.map { it.initials }.toSet()
            // Preserve deselections for commentaries not present on this device
            val preserved = previouslyDeselected - dialogInitials
            CommonUtils.aiSettings.commentaryDeselected = preserved + newDeselected
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
