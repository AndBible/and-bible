/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword.mysword

import org.crosswire.jsword.versification.BibleBook

val books = arrayOf(
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

val mySwordIntToBibleBook = books.mapIndexed { i, b -> i+1 to b}.toMap()
val bibleBookToMySwordInt = mySwordIntToBibleBook.toList().associate { (k, v) -> v to k }
