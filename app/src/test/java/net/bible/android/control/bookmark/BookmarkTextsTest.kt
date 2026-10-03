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

package net.bible.android.control.bookmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [computeBookmarkTexts] — the pure offset-slicing logic behind bookmark display text.
 * Plain JUnit (no Robolectric/DB) since the function has no Android dependencies.
 */
class BookmarkTextsTest {
    private val verse = "In the beginning God created the heaven and the earth."
    //                   0         1         2         3         4         5
    //                   0123456789...

    @Test
    fun `in-range single verse splits into prefix, selection and suffix`() {
        // select "beginning" (offset 7..16)
        val r = computeBookmarkTexts(listOf(verse), 7, 16, wholeVerse = false)!!
        assertEquals("In the ", r.startText)
        assertEquals("beginning", r.text)
        assertEquals(" God created the heaven and the earth.", r.endText)
        assertEquals(verse.trim(), r.fullText)
    }

    @Test
    fun `wholeVerse selects entire single verse`() {
        val r = computeBookmarkTexts(listOf(verse), 7, 16, wholeVerse = true)!!
        assertEquals("", r.startText)
        assertEquals(verse, r.text)
        assertEquals("", r.endText)
        assertEquals(verse, r.fullText)
    }

    @Test
    fun `null offsets default to whole verse`() {
        val r = computeBookmarkTexts(listOf(verse), null, null, wholeVerse = false)!!
        assertEquals("", r.startText)
        assertEquals(verse, r.text)
        assertEquals("", r.endText)
    }

    /** Reproduces the crash from OSTicket reports (build 1100/1101):
     *  a stored endOffset of Int.MIN_VALUE caused StringIndexOutOfBoundsException in slice(). */
    @Test
    fun `negative endOffset is clamped instead of crashing`() {
        val r = computeBookmarkTexts(listOf(verse), 0, Int.MIN_VALUE, wholeVerse = false)!!
        // endOffset clamped up to startOffset (0) → empty selection, whole verse is suffix
        assertEquals("", r.startText)
        assertEquals("", r.text)
        assertEquals(verse, r.endText)
    }

    @Test
    fun `negative startOffset is clamped to zero`() {
        val r = computeBookmarkTexts(listOf(verse), Int.MIN_VALUE, 6, wholeVerse = false)!!
        assertEquals("", r.startText)
        assertEquals("In the", r.text)
        assertEquals(" beginning God created the heaven and the earth.", r.endText)
    }

    @Test
    fun `offsets beyond verse length are clamped to length`() {
        val r = computeBookmarkTexts(listOf(verse), 7, Int.MAX_VALUE, wholeVerse = false)!!
        assertEquals("In the ", r.startText)
        assertEquals("beginning God created the heaven and the earth.", r.text)
        assertEquals("", r.endText)
    }

    @Test
    fun `startOffset greater than endOffset does not crash`() {
        // invalid ordering: endOffset clamped up to startOffset → empty selection
        val r = computeBookmarkTexts(listOf(verse), 20, 5, wholeVerse = false)!!
        assertEquals(verse.substring(0, 20), r.startText)
        assertEquals("", r.text)
        assertEquals(verse.substring(20), r.endText)
    }

    @Test
    fun `multi verse selection spans first, middle and last`() {
        val first = "first verse text"
        val middle = "middle verse text"
        val last = "last verse text"
        // start at offset 6 of first ("verse text"), end at offset 4 of last ("last")
        val r = computeBookmarkTexts(listOf(first, middle, last), 6, 4, wholeVerse = false)!!
        assertEquals("first ", r.startText)
        // segments are concatenated without separators, matching the original rendering
        assertEquals("verse textmiddle verse textlast", r.text)
        assertEquals(" verse text", r.endText)
    }

    @Test
    fun `multi verse with negative offsets does not crash`() {
        val first = "first verse"
        val last = "last verse"
        val r = computeBookmarkTexts(listOf(first, last), Int.MIN_VALUE, Int.MIN_VALUE, wholeVerse = false)!!
        // startOffset clamped to 0 → whole first verse selected; endOffset clamped to 0 → nothing of last
        assertEquals("", r.startText)
        assertEquals("first verse", r.text)
        assertEquals("last verse", r.endText)
    }

    @Test
    fun `empty texts returns null`() {
        assertNull(computeBookmarkTexts(emptyList(), 0, 5, wholeVerse = false))
    }
}
