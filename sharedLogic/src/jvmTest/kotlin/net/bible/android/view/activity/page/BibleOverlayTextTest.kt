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
package net.bible.android.view.activity.page

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The common overlay text: `"<abbreviation>:<title>"`, short book names, a verse 0 shown as its whole chapter. */
class BibleOverlayTextTest {
    private val kjv = Versifications.instance().getVersification("KJV")
    private val john = org.crosswire.jsword.versification.BibleBook.JOHN

    private fun overlay(abbreviation: String?, verse: Verse?): String? =
        withShortBookNames { overlayTextFor(abbreviation, pageTitleFor(verse)) }

    @Test
    fun bibleShowsAbbreviationAndChapterVerse() {
        // Short book names: John is "Joh" (the brief's "KJV:John 3:16" is the full-name spelling).
        assertEquals("KJV:Joh 3:16", overlay("KJV", Verse(kjv, john, 3, 16)))
    }

    @Test
    fun verseZeroShowsWholeChapter() {
        // Verse 0 stands for its whole chapter, not "Joh 3:0".
        assertEquals("KJV:Joh 3", overlay("KJV", Verse(kjv, john, 3, 0)))
    }

    @Test
    fun nullKeyGivesNull() {
        assertNull(pageTitleFor(null))
        assertNull(overlay("KJV", null))
    }

    @Test
    fun shortBookNamesAreRestored() {
        val before = BookName.isFullBookName()
        withShortBookNames { assertEquals(false, BookName.isFullBookName()) }
        assertEquals(before, BookName.isFullBookName())
        try {
            withShortBookNames { error("boom") }
        } catch (_: IllegalStateException) {
        }
        assertEquals(before, BookName.isFullBookName(), "restored even when the block throws")
    }
}
