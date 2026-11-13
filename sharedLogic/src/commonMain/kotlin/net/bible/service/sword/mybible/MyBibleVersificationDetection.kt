/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.mybible

import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import kotlin.math.abs

/** One chapter of a MyBible module: its MyBible [book] number, [chapter] and the [lastVerse] number in it. */
internal data class MyBibleChapterBound(val book: Int, val chapter: Int, val lastVerse: Int)

/** Verses of one book a versification would drop, split by whether KJV has their coordinates. */
internal data class MyBibleLostVerses(val protocanonical: Long, val deuterocanonical: Long) {
    operator fun plus(other: MyBibleLostVerses) =
        MyBibleLostVerses(protocanonical + other.protocanonical, deuterocanonical + other.deuterocanonical)
}

/**
 * How well [versification] holds a MyBible module. JSword cannot show a verse outside its versification, so
 * [lostVerses] counts, per book (as mapped in [versification]), the module verses that would vanish: all of a
 * book it lacks, chapters past its last chapter, verses past a chapter's last verse. A lost verse is
 * protocanonical when the KJV versification has its book, chapter and verse: losing Malachi 4 or Joel 2:28-32
 * hurts readers more than losing a Greek addition such as Daniel 13 or Esther 10:4-13. [discrepancy] sums the
 * differences of the kept chapter and verse bounds (verses the module combines into the preceding one only
 * cost discrepancy).
 */
internal class MyBibleVersificationFit(
    val versification: Versification,
    val lostVerses: Map<BibleBook, MyBibleLostVerses>,
    val discrepancy: Long,
) {
    val protocanonicalLost: Long get() = lostVerses.values.sumOf { it.protocanonical }
    val totalLost: Long get() = lostVerses.values.sumOf { it.protocanonical + it.deuterocanonical }
}

private val kjv: Versification by lazy { Versifications.instance().getVersification("KJV")!! }

/** Verses [first]..[last] of [book] [chapter] split by whether KJV has them. */
private fun lostVerses(book: BibleBook, chapter: Int, first: Int, last: Int): MyBibleLostVerses {
    val kjvLast = if (kjv.containsBook(book) && chapter <= kjv.getLastChapter(book)) kjv.getLastVerse(book, chapter) else 0
    val proto = maxOf(0, minOf(last, kjvLast) - first + 1).toLong()
    return MyBibleLostVerses(proto, (last - first + 1) - proto)
}

internal fun fitMyBibleVersification(chapters: List<MyBibleChapterBound>, v11n: Versification): MyBibleVersificationFit {
    val known = chapters.filter { myBibleIntToBibleBook.containsKey(it.book) }
    val lost = mutableMapOf<BibleBook, MyBibleLostVerses>()
    var discrepancy = 0L
    for ((number, bounds) in known.groupBy { it.book }) {
        val book = myBibleBook(number, v11n)!!
        if (v11n.containsBook(book)) discrepancy += abs(v11n.getLastChapter(book) - bounds.maxOf { it.chapter })
        for (bound in bounds) {
            val firstLost = if (!v11n.containsBook(book) || bound.chapter > v11n.getLastChapter(book)) {
                1
            } else {
                val lastVerse = v11n.getLastVerse(book, bound.chapter)
                discrepancy += abs(lastVerse - bound.lastVerse)
                lastVerse + 1
            }
            if (firstLost <= bound.lastVerse) {
                val dropped = lostVerses(book, bound.chapter, firstLost, bound.lastVerse)
                lost[book] = lost[book]?.plus(dropped) ?: dropped
            }
        }
    }
    return MyBibleVersificationFit(v11n, lost, discrepancy)
}

/**
 * MyBible stores native chapter/verse coordinates but declares no SWORD versification. The versification
 * wins that loses the fewest protocanonical verses, then the fewest verses overall (a missing deuterocanonical
 * book loses all of its verses, issue #2747), then whose bounds differ least. KJVA wins ties, including empty or
 * inconclusive modules, then German, then the rest by name (not registry HashSet order).
 */
internal fun detectMyBibleVersification(chapters: List<MyBibleChapterBound>): Versification {
    val registry = Versifications.instance()
    val candidates = (listOf("KJVA", "German") + registry.iterator().asSequence().toList().sorted()).distinct()
    // minWithOrNull keeps the first of equal scores.
    return candidates.mapNotNull { registry.getVersification(it) }.map { fitMyBibleVersification(chapters, it) }
        .minWithOrNull(compareBy({ it.protocanonicalLost }, { it.totalLost }, { it.discrepancy }))?.versification
        ?: registry.getVersification("KJVA")!!
}
