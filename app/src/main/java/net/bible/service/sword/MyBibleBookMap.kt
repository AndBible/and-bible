/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.sword

import org.crosswire.jsword.versification.BibleBook

val intToBibleBook = mapOf(
    10 to BibleBook.GEN,
    20 to BibleBook.EXOD,
    30 to BibleBook.LEV,
    40 to BibleBook.NUM,
    50 to BibleBook.DEUT,
    60 to BibleBook.JOSH,
    70 to BibleBook.JUDG,
    80 to BibleBook.RUTH,
    90 to BibleBook.SAM1,
    100 to BibleBook.SAM2,
    110 to BibleBook.KGS1,
    120 to BibleBook.KGS2,
    //180 to
    130 to BibleBook.CHR1,
    140 to BibleBook.CHR2,
    //145 to
    150 to BibleBook.EZRA,
    160 to BibleBook.NEH,
    //165 to
    170 to BibleBook.TOB,
    190 to BibleBook.ESTH,
    192 to BibleBook.ADD_ESTH,
    220 to BibleBook.JOB,
    230 to BibleBook.PS,
    240 to BibleBook.PROV,
    250 to BibleBook.ECCL,
    260 to BibleBook.SONG,
    270 to BibleBook.WIS,
    280 to BibleBook.SIR,
    290 to BibleBook.ISA,
    300 to BibleBook.JER,
    305 to BibleBook.PR_AZAR,
    310 to BibleBook.LAM,
    315 to BibleBook.EP_JER,
    320 to BibleBook.BAR,
    //323 to
    325 to BibleBook.SUS,
    330 to BibleBook.EZEK,
    340 to BibleBook.DAN,
    345 to BibleBook.ADD_DAN,
    350 to BibleBook.HOS,
    360 to BibleBook.JOEL,
    370 to BibleBook.AMOS,
    380 to BibleBook.OBAD,
    390 to BibleBook.JONAH,
    400 to BibleBook.MIC,
    410 to BibleBook.NAH,
    420 to BibleBook.HAB,
    430 to BibleBook.ZEPH,
    440 to BibleBook.HAG,
    450 to BibleBook.ZECH,
    460 to BibleBook.MAL,
    462 to BibleBook.MACC1,
    464 to BibleBook.MACC2,
    466 to BibleBook.MACC3,
    467 to BibleBook.MACC4,
    468 to BibleBook.ESD2,
    470 to BibleBook.MATT,
    480 to BibleBook.MARK,
    490 to BibleBook.LUKE,
    500 to BibleBook.JOHN,
    510 to BibleBook.ACTS,
    660 to BibleBook.JAS,
    670 to BibleBook.PET1,
    680 to BibleBook.PET2,
    690 to BibleBook.JOHN1,
    700 to BibleBook.JOHN2,
    710 to BibleBook.JOHN3,
    720 to BibleBook.JUDE,
    520 to BibleBook.ROM,
    530 to BibleBook.COR1,
    540 to BibleBook.COR2,
    550 to BibleBook.GAL,
    560 to BibleBook.EPH,
    570 to BibleBook.PHIL,
    580 to BibleBook.COL,
    590 to BibleBook.THESS1,
    600 to BibleBook.THESS2,
    610 to BibleBook.TIM1,
    620 to BibleBook.TIM2,
    630 to BibleBook.TITUS,
    640 to BibleBook.PHLM,
    650 to BibleBook.HEB,
    730 to BibleBook.REV,
    780 to BibleBook.EP_LAO,
)

val bibleBookToInt = intToBibleBook.toList().associate { (k, v) -> v to k }
