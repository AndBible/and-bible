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

import androidx.compose.ui.graphics.Color
import org.crosswire.jsword.versification.BibleBook

/**
 * Bible book categories used to color-code spines in the passage finder.
 *
 * The OT/NT colors mirror the palette used by GridChoosePassageBook
 * (http://en.wikipedia.org/wiki/Books_of_the_Bible). DEUTEROCANONICAL groups
 * apocryphal/deuterocanonical books — anything past Revelation in JSword's
 * BibleBook enum — so Catholic and Orthodox modules render their full canon.
 *
 * Each category carries a normal color and a monochrome shade
 * (0.0 = black, 1.0 = white) used on e-ink devices.
 */
enum class BookCategory(val color: Color, val monochromeShade: Float) {
    PENTATEUCH(Color(0xFFCCCCFE), 0.85f),
    HISTORY(Color(0xFFFECC9B), 0.75f),
    WISDOM(Color(0xFF99FF99), 0.70f),
    MAJOR_PROPHETS(Color(0xFFFF99FF), 0.65f),
    MINOR_PROPHETS(Color(0xFFFFFECD), 0.80f),
    GOSPELS(Color(0xFFFF9703), 0.55f),
    ACTS(Color(0xFF0099FF), 0.50f),
    PAULINE(Color(0xFFFFFF31), 0.60f),
    GENERAL_EPISTLES(Color(0xFF67CC66), 0.45f),
    REVELATION(Color(0xFFFE33FF), 0.40f),
    DEUTEROCANONICAL(Color(0xFFD4A574), 0.35f);

    companion object {
        fun forBook(book: BibleBook): BookCategory = when {
            book.ordinal <= BibleBook.DEUT.ordinal -> PENTATEUCH
            book.ordinal <= BibleBook.ESTH.ordinal -> HISTORY
            book.ordinal <= BibleBook.SONG.ordinal -> WISDOM
            book.ordinal <= BibleBook.DAN.ordinal -> MAJOR_PROPHETS
            book.ordinal <= BibleBook.MAL.ordinal -> MINOR_PROPHETS
            book.ordinal <= BibleBook.JOHN.ordinal -> GOSPELS
            book.ordinal <= BibleBook.ACTS.ordinal -> ACTS
            book.ordinal <= BibleBook.PHLM.ordinal -> PAULINE
            book.ordinal <= BibleBook.JUDE.ordinal -> GENERAL_EPISTLES
            book.ordinal <= BibleBook.REV.ordinal -> REVELATION
            else -> DEUTEROCANONICAL
        }
    }
}
