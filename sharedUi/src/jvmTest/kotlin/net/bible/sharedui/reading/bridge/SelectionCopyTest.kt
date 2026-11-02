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

package net.bible.sharedui.reading.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What Ctrl+C copies: #3462 (footnote popups) and #2860 (non-Bible documents). */
class SelectionCopyTest {
    /** A `bibleView.querySelection()` result for a selection inside a `.document` element. */
    private fun documentSelection(bookInitials: String, text: String) =
        """{"bookInitials":"$bookInitials","osisRef":"Gen.1.1","startOrdinal":3,"startOffset":4,"endOrdinal":5,""" +
            """"endOffset":9,"bookmarks":[],"text":"$text"}"""

    private val onlyKjvIsBible: (String?) -> Boolean = { it == "KJV" }

    @Test
    fun bibleSelectionIsCopiedAsVersesWithItsOffsets() {
        val copy = assertIs<SelectionCopy.Verses>(selectionCopy(documentSelection("KJV", "the beginning"), onlyKjvIsBible))
        assertEquals("KJV", copy.selection.bookInitials)
        assertEquals(3, copy.selection.startOrdinal)
        assertEquals(4, copy.selection.startOffset)
        assertEquals(5, copy.selection.endOrdinal)
        assertEquals(9, copy.selection.endOffset)
    }

    @Test
    fun commentarySelectionIsCopiedAsTheSelectedTextOnly() {
        // #2860: the commentary's ordinals are not Bible verses; formatting them as verses copied whole entries.
        val copy = selectionCopy(documentSelection("MHC", "only this sentence"), onlyKjvIsBible)
        assertEquals("only this sentence", assertIs<SelectionCopy.PlainText>(copy).text)
    }

    @Test
    fun footnotePopupTextIsCopiedAsPlainText() {
        // #3462: popup text lies outside `.document`, so the page returns a bare JSON string.
        val copy = selectionCopy("\"An alternative reading\"", onlyKjvIsBible)
        assertEquals("An alternative reading", assertIs<SelectionCopy.PlainText>(copy).text)
    }

    @Test
    fun plainTextKeepsEscapedCharacters() {
        val copy = selectionCopy("\"line one\\nline \\\"two\\\"\"", onlyKjvIsBible)
        assertEquals("line one\nline \"two\"", assertIs<SelectionCopy.PlainText>(copy).text)
    }

    @Test
    fun nothingIsCopiedWithoutUsableText() {
        assertNull(selectionCopy("null", onlyKjvIsBible), "no selection")
        assertNull(selectionCopy("\"   \"", onlyKjvIsBible), "blank popup selection")
        assertNull(selectionCopy(documentSelection("MHC", " "), onlyKjvIsBible), "blank non-Bible selection")
        assertNull(selectionCopy("not json", onlyKjvIsBible), "unreadable result")
        assertNull(selectionCopy("""{"text":"no ordinals"}""", onlyKjvIsBible), "object that is not a selection")
        assertNull(selectionCopy("42", onlyKjvIsBible), "number")
    }

    @Test
    fun bookThatIsNotInstalledFallsBackToPlainText() {
        // Default predicate: an uninstalled book cannot be formatted as verses, so the visible text is copied.
        val copy = selectionCopy(documentSelection("NOT-INSTALLED-BOOK", "visible text"))
        assertEquals("visible text", assertIs<SelectionCopy.PlainText>(copy).text)
    }
}
