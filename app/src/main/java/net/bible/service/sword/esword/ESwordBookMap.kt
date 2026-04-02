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

package net.bible.service.sword.esword

import org.crosswire.jsword.versification.BibleBook

/**
 * e-Sword uses sequential book numbering: 1-66 for the Protestant canon,
 * 67-78 for deuterocanonical/apocryphal books (76-77 are unused).
 */

// Books 1-66: identical to MySword's sequential numbering
private val canonicalBooks = arrayOf(
    BibleBook.GEN,
    BibleBook.EXOD,
    BibleBook.LEV,
    BibleBook.NUM,
    BibleBook.DEUT,
    BibleBook.JOSH,
    BibleBook.JUDG,
    BibleBook.RUTH,
    BibleBook.SAM1,
    BibleBook.SAM2,
    BibleBook.KGS1,
    BibleBook.KGS2,
    BibleBook.CHR1,
    BibleBook.CHR2,
    BibleBook.EZRA,
    BibleBook.NEH,
    BibleBook.ESTH,
    BibleBook.JOB,
    BibleBook.PS,
    BibleBook.PROV,
    BibleBook.ECCL,
    BibleBook.SONG,
    BibleBook.ISA,
    BibleBook.JER,
    BibleBook.LAM,
    BibleBook.EZEK,
    BibleBook.DAN,
    BibleBook.HOS,
    BibleBook.JOEL,
    BibleBook.AMOS,
    BibleBook.OBAD,
    BibleBook.JONAH,
    BibleBook.MIC,
    BibleBook.NAH,
    BibleBook.HAB,
    BibleBook.ZEPH,
    BibleBook.HAG,
    BibleBook.ZECH,
    BibleBook.MAL,
    BibleBook.MATT,
    BibleBook.MARK,
    BibleBook.LUKE,
    BibleBook.JOHN,
    BibleBook.ACTS,
    BibleBook.ROM,
    BibleBook.COR1,
    BibleBook.COR2,
    BibleBook.GAL,
    BibleBook.EPH,
    BibleBook.PHIL,
    BibleBook.COL,
    BibleBook.THESS1,
    BibleBook.THESS2,
    BibleBook.TIM1,
    BibleBook.TIM2,
    BibleBook.TITUS,
    BibleBook.PHLM,
    BibleBook.HEB,
    BibleBook.JAS,
    BibleBook.PET1,
    BibleBook.PET2,
    BibleBook.JOHN1,
    BibleBook.JOHN2,
    BibleBook.JOHN3,
    BibleBook.JUDE,
    BibleBook.REV,
)

// Books 67-78: deuterocanonical/apocryphal (76-77 unused)
private val apocryphaBooks = mapOf(
    67 to BibleBook.TOB,
    68 to BibleBook.JDT,
    69 to BibleBook.WIS,
    70 to BibleBook.SIR,
    71 to BibleBook.BAR,
    72 to BibleBook.MACC1,
    73 to BibleBook.MACC2,
    74 to BibleBook.ESD1,
    75 to BibleBook.ESD2,
    78 to BibleBook.PR_MAN,
)

val eSwordIntToBibleBook: Map<Int, BibleBook> = buildMap {
    canonicalBooks.forEachIndexed { i, b -> put(i + 1, b) }
    putAll(apocryphaBooks)
}

val bibleBookToESwordInt: Map<BibleBook, Int> =
    eSwordIntToBibleBook.entries.associate { (k, v) -> v to k }
