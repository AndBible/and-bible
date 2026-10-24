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


package net.bible.sharedui.reading

import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.positiveOrNull
import net.bible.android.view.activity.page.refChooserVerseName
import org.crosswire.jsword.versification.BookName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionTest {
    @Test
    fun verseRangeIsNullWhenBookIsNotInstalled() {
        // Behaviour kept from the Android original: no installed SwordBook -> no verse range
        // (KJVA is only the versification fallback for an installed book without its own).
        val selection = Selection("NOPE", 1, 3)
        assertNull(selection.book)
        assertNull(selection.verseRange)
    }

    @Test
    fun hasRangeOnlyWithBothOffsets() {
        fun sel(start: Int?, end: Int?) = Selection("NOPE", 1, start, 2, end, emptyList())
        assertTrue(sel(0, 5).hasRange)
        assertFalse(sel(0, null).hasRange)
        assertFalse(sel(null, 5).hasRange)
        assertFalse(sel(null, null).hasRange)
        // The convenience constructor sets startOffset 0 and no end offset: no range.
        assertFalse(Selection("NOPE", 1, 3).hasRange)
    }

    @Test
    fun endOrdinalDefaultsToStartOrdinal() {
        assertEquals(7, Selection("NOPE", 7, null).endOrdinal)
        assertEquals(9, Selection("NOPE", 7, 9).endOrdinal)
    }

    @Test
    fun positiveOrNullMapsNegativeToNull() {
        assertNull(positiveOrNull(-1))
        assertEquals(0, positiveOrNull(0))
        assertEquals(5, positiveOrNull(5))
    }

    @Test
    fun refChooserVerseNameRestoresFullBookNameGlobal() {
        val old = BookName.isFullBookName()
        try {
            BookName.setFullBookName(true)
            assertEquals("Gen 1:1", refChooserVerseName("Gen.1.1"))
            assertTrue(BookName.isFullBookName())
        } finally {
            BookName.setFullBookName(old)
        }
    }

    @Test
    fun refChooserVerseNameOfNullIsEmpty() {
        assertEquals("", refChooserVerseName(null))
    }
}
