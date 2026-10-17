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

import androidx.annotation.ColorInt
import org.crosswire.jsword.versification.BibleBook

/**
 * Bible book categories used to color-code spines in the passage finder.
 *
 * The OT/NT colors mirror the palette used by GridChoosePassageBook
 * (http://en.wikipedia.org/wiki/Books_of_the_Bible). DEUTEROCANONICAL groups
 * apocryphal/deuterocanonical books — anything past Revelation in JSword's
 * BibleBook enum — so Catholic and Orthodox modules render their full canon.
 *
 * Each category carries a normal color (packed ARGB) and a monochrome shade
 * (0.0 = black, 1.0 = white) used on e-ink devices.
 */
enum class BookCategory(@ColorInt val color: Int, val monochromeShade: Float) {
    PENTATEUCH(0xFFCCCCFE.toInt(), 0.85f),
    HISTORY(0xFFFECC9B.toInt(), 0.75f),
    WISDOM(0xFF99FF99.toInt(), 0.70f),
    MAJOR_PROPHETS(0xFFFF99FF.toInt(), 0.65f),
    MINOR_PROPHETS(0xFFFFFECD.toInt(), 0.80f),
    GOSPELS(0xFFFF9703.toInt(), 0.55f),
    ACTS(0xFF0099FF.toInt(), 0.50f),
    PAULINE(0xFFFFFF31.toInt(), 0.60f),
    GENERAL_EPISTLES(0xFF67CC66.toInt(), 0.45f),
    REVELATION(0xFFFE33FF.toInt(), 0.40f),
    DEUTEROCANONICAL(0xFFD4A574.toInt(), 0.35f);

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
