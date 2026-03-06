/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.progress

import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.progress.ChapterReadingRecord
import net.bible.android.database.progress.DailyReadingCount
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.progress.ReadingSource
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

object ProgressControl {
    private val dao get() = DatabaseContainer.instance.progressDb.progressDao()

    fun markVerseMemorized(verseRange: VerseRange) {
        val kjvRange = verseRange.toV11n(KJVA)
        for (ordinal in kjvRange.start.ordinal..kjvRange.end.ordinal) {
            if (!dao.isVerseMemorized(ordinal)) {
                dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = ordinal))
            }
        }
    }

    fun isVerseMemorized(v11n: Versification, book: BibleBook, chapter: Int, verse: Int): Boolean {
        val kjvVerse = Verse(v11n, book, chapter, verse).toV11n(KJVA)
        return dao.isVerseMemorized(kjvVerse.ordinal)
    }

    fun getMemorizationProgress(v11n: Versification, book: BibleBook): Float {
        val startOrdinal = Verse(KJVA, book, 1, 1).ordinal
        val lastChapter = KJVA.getLastChapter(book)
        val lastVerse = KJVA.getLastVerse(book, lastChapter)
        val endOrdinal = Verse(KJVA, book, lastChapter, lastVerse).ordinal
        val totalVerses = endOrdinal - startOrdinal + 1
        if (totalVerses <= 0) return 0f
        val memorized = dao.countMemorizedVersesInRange(startOrdinal, endOrdinal)
        return memorized.toFloat() / totalVerses
    }

    fun getMemorizationProgress(v11n: Versification, book: BibleBook, chapter: Int): Float {
        val startOrdinal = Verse(KJVA, book, chapter, 1).ordinal
        val lastVerse = KJVA.getLastVerse(book, chapter)
        val endOrdinal = Verse(KJVA, book, chapter, lastVerse).ordinal
        val totalVerses = endOrdinal - startOrdinal + 1
        if (totalVerses <= 0) return 0f
        val memorized = dao.countMemorizedVersesInRange(startOrdinal, endOrdinal)
        return memorized.toFloat() / totalVerses
    }

    fun markChapterRead(v11n: Versification, book: BibleBook, chapter: Int, source: ReadingSource = ReadingSource.MANUAL) {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        val cycle = getCurrentCycle()
        if (!dao.isChapterRead(kjvBook.ordinal, chapter, cycle)) {
            dao.insertChapterReadingRecord(
                ChapterReadingRecord(
                    kjvBookOrdinal = kjvBook.ordinal,
                    chapter = chapter,
                    cycle = cycle,
                    source = source,
                )
            )
        }
    }

    fun isChapterRead(v11n: Versification, book: BibleBook, chapter: Int): Boolean {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        return dao.isChapterRead(kjvBook.ordinal, chapter, getCurrentCycle())
    }

    fun getReadingProgress(v11n: Versification, book: BibleBook): Float {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        val totalChapters = KJVA.getLastChapter(kjvBook)
        if (totalChapters <= 0) return 0f
        val readChapters = dao.countReadChaptersForBook(kjvBook.ordinal, getCurrentCycle())
        return readChapters.toFloat() / totalChapters
    }

    fun getCurrentCycle(): Int = dao.getLatestCycle()

    fun startNewCycle(): Int {
        val newCycle = getCurrentCycle() + 1
        return newCycle
    }

    // Statistics methods for ReadingProgressActivity

    fun getTotalReadChapters(cycle: Int = getCurrentCycle()): Int = dao.countTotalReadChapters(cycle)

    fun getDistinctReadDays(cycle: Int = getCurrentCycle()): Int = dao.countDistinctReadDays(cycle)

    fun getTotalMemorizedVerses(): Int = dao.countTotalMemorizedVerses()

    val totalBibleChapters: Int by lazy {
        var total = 0
        for (book in KJVA.bookIterator) {
            total += KJVA.getLastChapter(book)
        }
        total
    }

    fun getReadChaptersForBook(book: BibleBook, cycle: Int = getCurrentCycle()): List<Int> {
        return dao.getReadChaptersForBook(book.ordinal, cycle)
    }

    fun getReadingCalendar(startMs: Long, endMs: Long): List<DailyReadingCount> {
        return dao.getReadingCalendar(startMs, endMs)
    }

    fun getBookReadingProgress(cycle: Int = getCurrentCycle()): Map<BibleBook, Float> {
        val result = mutableMapOf<BibleBook, Float>()
        for (book in KJVA.bookIterator) {
            val totalChapters = KJVA.getLastChapter(book)
            if (totalChapters <= 0) continue
            val readChapters = dao.countReadChaptersForBook(book.ordinal, cycle)
            if (readChapters > 0) {
                result[book] = readChapters.toFloat() / totalChapters
            }
        }
        return result
    }
}
