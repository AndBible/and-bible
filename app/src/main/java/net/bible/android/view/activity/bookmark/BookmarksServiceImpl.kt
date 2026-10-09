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
package net.bible.android.view.activity.bookmark

import android.text.format.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.common.toV11n
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities.BaseBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.service.common.CommonUtils
import net.bible.service.common.displayName
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.bookmark.BookmarksService
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import org.crosswire.jsword.versification.Versification

private const val BOOKMARK_SORT_ORDER_PREF = "BookmarkSortOrder"
private const val BOOKMARK_SHOW_NOTES_PREF = "bookmark_show_notes"

/**
 * Android [BookmarksService]: Room/JSword-backed row loading for the Bookmarks list, mirroring
 * classic `Bookmarks.loadBookmarkList` (Bookmarks.kt:252-293) field-for-field against
 * `BookmarkItemAdapter`'s per-row rendering (verse/key name, date, highlighted content, notes,
 * label chips, speak icon).
 *
 * Also holds the host-resolvable id -> entity map: [BookmarksComposeActivity] resolves a
 * tapped/selected row's [BookmarkRow.id] back to its [BaseBookmarkWithNotes] (for the verbatim
 * select-result intent, assign-labels, delete, CSV export) via [bookmarkById] / [bookmarksByIds] /
 * [loadedBookmarks] — populated by the most recent [loadRows] call, mirroring how classic keeps its
 * `bookmarkList` around between the adapter and the button/menu handlers.
 */
class BookmarksServiceImpl(
    private val bookmarkControl: BookmarkControl,
    private val windowControl: WindowControl,
) : BookmarksService {

    /** The bookmarks behind the most recent [loadRows] result, keyed by `id.toString()`. Insertion
     *  order (bible bookmarks then generic, same as classic's `bookmarkList`) is preserved by
     *  `associateBy` on a freshly-built map, so [loadedBookmarks] can be used for CSV export. */
    private var loaded: Map<String, BaseBookmarkWithNotes> = emptyMap()

    override fun filterLabels(): List<BookmarkFilterLabel> =
        bookmarkControl.allLabels.mapIndexed { i, l -> BookmarkFilterLabel(i, l.displayName) }

    override suspend fun loadRows(
        filterIndex: Int,
        sort: BookmarkSortMode,
        search: String?,
        showNotes: Boolean,
    ): List<BookmarkRow> = withContext(Dispatchers.IO) {
        val label = bookmarkControl.allLabels.getOrNull(filterIndex) ?: run {
            loaded = emptyMap()
            return@withContext emptyList()
        }

        val bible = bookmarkControl.getBibleBookmarksWithLabel(label, sort.toClassicSortOrder(), search = search)
        val generic = bookmarkControl.getGenericBookmarksWithLabel(label, search = search)
        val all: List<BaseBookmarkWithNotes> = bible + generic

        val versification = windowControl.activeWindowPageManager.currentBible.versification
        val rows = all.map { toRow(it, versification, showNotes) }
        loaded = all.associateBy { it.id.toString() }
        rows
    }

    private fun toRow(bm: BaseBookmarkWithNotes, versification: Versification, showNotes: Boolean): BookmarkRow {
        // Classic's BookmarkItemAdapter.getView lazily resolves text the first time a row is bound.
        if (bm.text == null) bookmarkControl.addText(bm)

        // Mirrors BookmarkItemAdapter: isSpeak from the raw label list, THEN default an empty list
        // to [Unlabeled] for the chip colours (labelUnlabelled is never the speak label). Computed
        // first so the title branch below can reuse `isSpeak` instead of re-querying
        // `isSpeakBookmark` (which just re-runs `labelsForBookmark`).
        val labels = bookmarkControl.labelsForBookmark(bm)
        val isSpeak = labels.contains(bookmarkControl.speakLabel)

        val title = when (bm) {
            is BibleBookmarkWithNotes -> {
                val verseName = bm.verseRange.toV11n(versification).getName()
                val speakBook = bm.speakBook
                if (isSpeak && speakBook != null) {
                    CommonUtils.getResourceString(R.string.something_with_parenthesis, verseName, speakBook.abbreviation)
                } else verseName
            }
            is GenericBookmarkWithNotes -> "${bm.book?.abbreviation ?: bm.bookInitials}: ${bm.bookKey?.getName() ?: bm.key}"
            else -> ""
        }

        val chipLabels = labels.ifEmpty { listOf(bookmarkControl.labelUnlabelled) }
        val labelColors = chipLabels.filterNot { it.isSpeakLabel }.map { it.color }

        val dateText = DateFormat.format("EEE, yyyy-MM-dd HH:mm", bm.createdAt).toString()
        val notes = bm.notes
        return BookmarkRow(
            id = bm.id.toString(),
            title = title,
            dateText = dateText,
            content = htmlToStyledText(bm.highlightedText),
            notes = if (showNotes && notes != null) htmlToStyledText(notes) else null,
            labelColors = labelColors,
            isSpeak = isSpeak,
        )
    }

    /** Resolves a [BookmarkRow.id] back to the Room/JSword entity, from the most recent [loadRows]. */
    fun bookmarkById(id: String): BaseBookmarkWithNotes? = loaded[id]

    /** Resolves a list of [BookmarkRow.id]s (order-preserving over [ids]) back to their entities. */
    fun bookmarksByIds(ids: List<String>): List<BaseBookmarkWithNotes> = ids.mapNotNull { loaded[it] }

    /** All bookmarks behind the most recent (filtered/sorted/searched) [loadRows] result, in the
     *  same order the rows were shown — used for CSV export (classic exports the currently-loaded
     *  `bookmarkList`, not the multi-selection). */
    fun loadedBookmarks(): List<BaseBookmarkWithNotes> = loaded.values.toList()

    override fun loadSortMode(): BookmarkSortMode {
        val str = CommonUtils.getSharedPreference(BOOKMARK_SORT_ORDER_PREF, BookmarkSortOrder.BIBLE_ORDER.toString())
        val order = try {
            BookmarkSortOrder.valueOf(str!!)
        } catch (e: IllegalArgumentException) {
            BookmarkSortOrder.BIBLE_ORDER
        }
        return order.toSortMode()
    }

    override fun saveSortMode(mode: BookmarkSortMode) {
        CommonUtils.saveSharedPreference(BOOKMARK_SORT_ORDER_PREF, mode.toClassicSortOrder().toString())
    }

    override fun loadShowNotes(): Boolean = CommonUtils.settings.getBoolean(BOOKMARK_SHOW_NOTES_PREF, true)

    override fun saveShowNotes(v: Boolean) {
        CommonUtils.settings.setBoolean(BOOKMARK_SHOW_NOTES_PREF, v)
    }
}

/** The 4-state Compose cycle maps 1:1 onto its 4 same-named classic [BookmarkSortOrder] entries. */
fun BookmarkSortMode.toClassicSortOrder(): BookmarkSortOrder = when (this) {
    BookmarkSortMode.BIBLE_ORDER -> BookmarkSortOrder.BIBLE_ORDER
    BookmarkSortMode.BIBLE_ORDER_DESC -> BookmarkSortOrder.BIBLE_ORDER_DESC
    BookmarkSortMode.CREATED_AT_DESC -> BookmarkSortOrder.CREATED_AT_DESC
    BookmarkSortMode.CREATED_AT -> BookmarkSortOrder.CREATED_AT
}

/** The reverse mapping; [BookmarkSortOrder.LAST_UPDATED]/[BookmarkSortOrder.ORDER_NUMBER] are not
 *  reachable from the Compose toggle (classic's `else -> BIBLE_ORDER` catch-all for the pref reader
 *  hits this too), so they fall back to [BookmarkSortMode.BIBLE_ORDER]. */
fun BookmarkSortOrder.toSortMode(): BookmarkSortMode = when (this) {
    BookmarkSortOrder.BIBLE_ORDER -> BookmarkSortMode.BIBLE_ORDER
    BookmarkSortOrder.BIBLE_ORDER_DESC -> BookmarkSortMode.BIBLE_ORDER_DESC
    BookmarkSortOrder.CREATED_AT_DESC -> BookmarkSortMode.CREATED_AT_DESC
    BookmarkSortOrder.CREATED_AT -> BookmarkSortMode.CREATED_AT
    BookmarkSortOrder.LAST_UPDATED, BookmarkSortOrder.ORDER_NUMBER -> BookmarkSortMode.BIBLE_ORDER
}

/**
 * Parses `pre<b>bold</b>post`-shaped bookmark HTML — that's exactly what
 * `BaseBookmarkWithNotes.highlightedText` is (`"$startText<b>$text</b>$endText"`) — into a portable
 * [StyledText], mirroring [net.bible.sharedcore.search.parseHighlightHtml] (Batch-5 EPUB search):
 * `<b>`/`</b>` toggle a bold run and HTML entities are unescaped. Also used for bookmark notes,
 * which classic feeds through the same `Html.fromHtml`-based `htmlToSpan` — notes are ordinarily
 * plain user text, but on the (rare) chance one contains a stray tag other than `<b>`, that tag is
 * stripped rather than rendered literally, since [StyledText] only models bold/highlight runs.
 * Unlike [net.bible.sharedcore.search.parseHighlightHtml], this does NOT need whitespace
 * normalization: its input is bookmark/note text from the database, not raw source XHTML.
 */
fun htmlToStyledText(html: String): StyledText {
    val runs = mutableListOf<StyledRun>()
    var i = 0
    var bold = false
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) {
            runs.add(StyledRun(buf.toString(), bold = bold))
            buf.clear()
        }
    }
    while (i < html.length) {
        when {
            html.startsWith("<b>", i) -> { flush(); bold = true; i += 3 }
            html.startsWith("</b>", i) -> { flush(); bold = false; i += 4 }
            html[i] == '<' -> {
                // Strip any other tag (e.g. a literal <i>/<br> a user typed into a note) — StyledText
                // has no run kind for it.
                val gt = html.indexOf('>', i)
                i = if (gt > i) gt + 1 else i + 1
            }
            html[i] == '&' -> {
                val semi = html.indexOf(';', i)
                if (semi > i) {
                    when (html.substring(i, semi + 1)) {
                        "&amp;" -> buf.append('&')
                        "&lt;" -> buf.append('<')
                        "&gt;" -> buf.append('>')
                        "&quot;" -> buf.append('"')
                        "&#39;", "&apos;" -> buf.append('\'')
                        else -> buf.append(html, i, semi + 1)
                    }
                    i = semi + 1
                } else {
                    buf.append(html[i]); i++
                }
            }
            else -> { buf.append(html[i]); i++ }
        }
    }
    flush()
    if (runs.isEmpty()) runs.add(StyledRun(""))
    return StyledText(runs)
}
