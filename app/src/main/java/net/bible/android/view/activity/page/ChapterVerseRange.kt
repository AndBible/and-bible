/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.page

import net.bible.android.control.page.ChapterVerse
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

/**
 * Handle verse selection logic.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
data class ChapterVerseRange(
    private val v11n: Versification,
    private val bibleBook: BibleBook,
    val start: ChapterVerse?,
    val end: ChapterVerse?
) {

    fun toggleVerse(verse: ChapterVerse): ChapterVerseRange {
        var newStart = start
        var newEnd = end
        if (verse.after(end)) {
            newEnd = verse
        } else if (verse.before(start)) {
            newStart = verse
        } else if (verse.after(start)) {
            // inc/dec are tricky when we don't know how many verses in chapters
            newEnd =
                if (verse.verse > 1) {
                    ChapterVerse(verse.chapter, verse.verse - 1)
                } else {
                    verse
                }
        } else if (verse == start && start == end) {
            newStart = null
            newEnd = null
        } else if (verse == start) {
            // Inc/dec are tricky when we don't know how many verses in chapters.
            // So there is a flaw in that the first verse cannot be deselected if selection spans multiple chapters
            if (start.sameChapter(end) && start.sameChapter(verse)) {
                newStart = ChapterVerse(verse.chapter, verse.verse + 1)
            }
        }

        return ChapterVerseRange(v11n, bibleBook, newStart, newEnd)
    }

    fun getExtrasIn(other: ChapterVerseRange): Set<ChapterVerse> {
        val verseRange = createVerseRange()
        val otherVerseRange = other.createVerseRange()
        val otherVerses = otherVerseRange?.toVerseArray() ?: arrayOf()

        return otherVerses
                .filterNot { verseRange?.contains(it) ?: false }
                .map { ChapterVerse(it.chapter, it.verse) }
                .toSet()
    }

    operator fun contains(verse: ChapterVerse) =
            verse == start ||
            verse == end ||
            (verse.after(start) && verse.before(end))

    private fun createVerseRange() =
        if(start != null && end != null) {
            VerseRange(
                v11n,
                Verse(v11n, bibleBook, start.chapter, start.verse),
                Verse(v11n, bibleBook, end.chapter, end.verse)
            )
        } else null

    fun isEmpty() = start == null || end == null
}
