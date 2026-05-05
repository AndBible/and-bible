/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import org.crosswire.jsword.versification.BibleBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCategoryTest {

    @Test
    fun `canonical books map to expected categories`() {
        assertEquals(BookCategory.PENTATEUCH, BookCategory.forBook(BibleBook.GEN))
        assertEquals(BookCategory.PENTATEUCH, BookCategory.forBook(BibleBook.DEUT))
        assertEquals(BookCategory.HISTORY, BookCategory.forBook(BibleBook.JOSH))
        assertEquals(BookCategory.WISDOM, BookCategory.forBook(BibleBook.PS))
        assertEquals(BookCategory.MAJOR_PROPHETS, BookCategory.forBook(BibleBook.ISA))
        assertEquals(BookCategory.MINOR_PROPHETS, BookCategory.forBook(BibleBook.MAL))
        assertEquals(BookCategory.GOSPELS, BookCategory.forBook(BibleBook.MATT))
        assertEquals(BookCategory.ACTS, BookCategory.forBook(BibleBook.ACTS))
        assertEquals(BookCategory.PAULINE, BookCategory.forBook(BibleBook.ROM))
        assertEquals(BookCategory.GENERAL_EPISTLES, BookCategory.forBook(BibleBook.JUDE))
        assertEquals(BookCategory.REVELATION, BookCategory.forBook(BibleBook.REV))
    }

    @Test
    fun `deuterocanonical books map to DEUTEROCANONICAL`() {
        // BibleBook entries past REV in the enum are deuterocanonical/apocryphal.
        val deutero = BibleBook.values().filter { it.ordinal > BibleBook.REV.ordinal }
        assertTrue("expected deuterocanonical books in BibleBook enum", deutero.isNotEmpty())
        deutero.forEach { book ->
            assertEquals(
                "expected $book to map to DEUTEROCANONICAL",
                BookCategory.DEUTEROCANONICAL,
                BookCategory.forBook(book)
            )
        }
    }

    @Test
    fun `all category colors are unique`() {
        val colors = BookCategory.values().map { it.color.value }
        assertEquals(
            "duplicate colors detected across BookCategory values",
            colors.size,
            colors.toSet().size
        )
    }

    @Test
    fun `all monochrome shades are unique`() {
        val shades = BookCategory.values().map { it.monochromeShade }
        assertEquals(
            "duplicate monochrome shades detected across BookCategory values",
            shades.size,
            shades.toSet().size
        )
    }
}
