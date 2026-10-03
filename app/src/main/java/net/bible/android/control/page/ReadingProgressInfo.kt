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

package net.bible.android.control.page

import net.bible.android.misc.wrapString
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification

/**
 * Type-aware reading-progress metadata sent to the WebView so it can render a
 * "how far through" indicator. For verse-keyed documents (Bible/commentary) the unit is
 * the current book; for EPUB/general books the unit is the whole book.
 */
data class ReadingProgressInfo(
    val kind: String,            // "bible" or "book"
    // Bible/commentary: absolute verse-ordinal span of the current book.
    val unitStart: Int? = null,
    val unitEnd: Int? = null,
    val chapterCount: Int? = null,
    val currentChapter: Int? = null,
    // EPUB/general book: position of the current fragment within the whole book.
    // EPUB anchor ordinals restart at 0 for every spine item, so whole-book position
    // is the cumulative ordinal offset of the current fragment plus the in-fragment
    // offset (computed client-side from the fragment's local ordinal range).
    val fragmentOffset: Int? = null,
    val bookOrdinalSpan: Int? = null,
    val charCount: Int? = null,
) {
    val asJson: String get() = mapToJson(buildMap {
        put("kind", wrapString(kind))
        unitStart?.let { put("unitStart", it.toString()) }
        unitEnd?.let { put("unitEnd", it.toString()) }
        chapterCount?.let { put("chapterCount", it.toString()) }
        currentChapter?.let { put("currentChapter", it.toString()) }
        fragmentOffset?.let { put("fragmentOffset", it.toString()) }
        bookOrdinalSpan?.let { put("bookOrdinalSpan", it.toString()) }
        charCount?.let { put("charCount", it.toString()) }
    })

    companion object {
        /** Progress relative to the whole Bible book that [verseRange] starts in. */
        fun forVerseKey(v11n: Versification, verseRange: VerseRange): ReadingProgressInfo {
            val book = verseRange.start.book
            val lastChapter = v11n.getLastChapter(book)
            val lastVerseNo = v11n.getLastVerse(book, lastChapter)
            return ReadingProgressInfo(
                kind = "bible",
                unitStart = Verse(v11n, book, 1, 1).ordinal,
                unitEnd = Verse(v11n, book, lastChapter, lastVerseNo).ordinal,
                chapterCount = lastChapter,
                currentChapter = verseRange.start.chapter,
            )
        }

        /**
         * Progress relative to the whole EPUB/general book. [fragmentOffset] is the sum
         * of all earlier fragments' ordinal spans; [bookOrdinalSpan] is the total.
         */
        fun forEpub(fragmentOffset: Int, bookOrdinalSpan: Int, charCount: Int): ReadingProgressInfo =
            ReadingProgressInfo(
                kind = "book",
                fragmentOffset = fragmentOffset,
                bookOrdinalSpan = bookOrdinalSpan,
                charCount = charCount,
            )
    }
}
