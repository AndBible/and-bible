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

package net.bible.service.llm.agent

import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.SystemKJV
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for AI doc marker not appearing on Bible pages when an LLM action
 * is launched from the window menu. The window-menu path roundtrips the page key
 * through `book.getKey(osisRef)`, which on a Bible returns a [RangedPassage] —
 * not a [VerseRange] or [Verse] — so a naive cast leaves the verseRange null and
 * the resulting marker has no KJVA ordinals.
 */
class KeyToVerseRangeTest {

    private val v11n = Versifications.instance().getVersification(SystemKJV.V11N_NAME)

    @Test
    fun nullKey_returnsNull() {
        assertNull(AgentSessionManager.keyToVerseRange(null))
    }

    @Test
    fun verseRange_isReturnedAsIs() {
        val range = VerseRange(v11n, Verse(v11n, BibleBook.GEN, 1, 1), Verse(v11n, BibleBook.GEN, 1, 5))
        assertEquals(range, AgentSessionManager.keyToVerseRange(range))
    }

    @Test
    fun singleVerse_isWrappedAsRange() {
        val verse = Verse(v11n, BibleBook.JOHN, 3, 16)
        val result = AgentSessionManager.keyToVerseRange(verse)
        assertNotNull(result)
        assertEquals(verse, result!!.start)
        assertEquals(verse, result.end)
    }

    @Test
    fun rangedPassageWithChapterRange_yieldsChapterVerseRange() {
        // Mimics what `book.getKey("Gen.1.0-Gen.1.31")` returns on a Bible.
        val passage = RangedPassage(v11n)
        passage.add(VerseRange(v11n, Verse(v11n, BibleBook.GEN, 1, 0), Verse(v11n, BibleBook.GEN, 1, 31)))
        val result = AgentSessionManager.keyToVerseRange(passage)
        assertNotNull("RangedPassage should be coerced to a VerseRange (not null)", result)
        assertEquals(BibleBook.GEN, result!!.start.book)
        assertEquals(BibleBook.GEN, result.end.book)
        assertEquals(0, result.start.verse)
        assertEquals(31, result.end.verse)
    }

    @Test
    fun rangedPassageWithSingleVerse_yieldsSingleVerseRange() {
        val passage = RangedPassage(v11n)
        val verse = Verse(v11n, BibleBook.JOHN, 3, 16)
        passage.add(VerseRange(v11n, verse, verse))
        val result = AgentSessionManager.keyToVerseRange(passage)
        assertNotNull(result)
        assertEquals(16, result!!.start.verse)
        assertEquals(16, result.end.verse)
    }

    @Test
    fun emptyRangedPassage_returnsNull() {
        val empty = RangedPassage(v11n)
        assertNull(AgentSessionManager.keyToVerseRange(empty))
    }
}
