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

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ReadingProgressInfoTest {
    private val kjv = Versifications.instance().getVersification("KJV")

    @Test
    fun forVerseKey_genesisStart_spansWholeBook() {
        val range = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 1, 1), Verse(kjv, BibleBook.GEN, 1, 5))
        val info = ReadingProgressInfo.forVerseKey(kjv, range)

        assertEquals("bible", info.kind)
        assertEquals(50, info.chapterCount)         // Genesis has 50 chapters
        assertEquals(1, info.currentChapter)
        assertEquals(Verse(kjv, BibleBook.GEN, 1, 1).ordinal, info.unitStart)
        val lastChapter = kjv.getLastChapter(BibleBook.GEN)
        val lastOrdinal = Verse(kjv, BibleBook.GEN, lastChapter, kjv.getLastVerse(BibleBook.GEN, lastChapter)).ordinal
        assertEquals(lastOrdinal, info.unitEnd)
        assertTrue(info.unitEnd!! > info.unitStart!!)
        assertEquals(null, info.charCount)
    }

    @Test
    fun forVerseKey_midBook_reportsCurrentChapterButWholeBookUnit() {
        val start = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 1, 1), Verse(kjv, BibleBook.GEN, 1, 1))
        val mid = VerseRange(kjv, Verse(kjv, BibleBook.GEN, 30, 1), Verse(kjv, BibleBook.GEN, 30, 2))

        val startInfo = ReadingProgressInfo.forVerseKey(kjv, start)
        val midInfo = ReadingProgressInfo.forVerseKey(kjv, mid)

        assertEquals(30, midInfo.currentChapter)
        assertEquals(50, midInfo.chapterCount)
        // Unit (whole book) is identical regardless of position within the book:
        assertEquals(startInfo.unitStart, midInfo.unitStart)
        assertEquals(startInfo.unitEnd, midInfo.unitEnd)
    }

    @Test
    fun forEpub_buildsBookKind() {
        val info = ReadingProgressInfo.forEpub(fragmentOffset = 1500, bookOrdinalSpan = 8000, charCount = 50_000)
        assertEquals("book", info.kind)
        assertEquals(1500, info.fragmentOffset)
        assertEquals(8000, info.bookOrdinalSpan)
        assertEquals(50_000, info.charCount)
        assertEquals(null, info.chapterCount)
        assertEquals(null, info.unitStart)
    }
}
