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
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.versification.Scripture
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.common.ReadingProgressSettings
import net.bible.android.database.progress.ChapterReadingRecord
import net.bible.android.database.progress.DailyReadingCount
import net.bible.android.database.progress.MemorizationTarget
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.progress.ReadingSource
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

data class RangeDifferenceResult(
    val remaining: List<Pair<Int, Int>>,
    val removed: List<Int>,
)

/**
 * Computes the set difference of target ranges minus a removal range.
 * Returns remaining sub-ranges and the actually removed ordinals.
 */
fun computeRangeDifference(
    targets: List<Pair<Int, Int>>,
    removeStart: Int,
    removeEnd: Int,
): RangeDifferenceResult {
    val remaining = mutableListOf<Pair<Int, Int>>()
    val removed = mutableListOf<Int>()

    for ((tStart, tEnd) in targets) {
        val intStart = maxOf(tStart, removeStart)
        val intEnd = minOf(tEnd, removeEnd)
        if (intStart > intEnd) continue // no overlap

        removed.addAll(intStart..intEnd)

        if (tStart < removeStart) {
            remaining.add(tStart to removeStart - 1)
        }
        if (tEnd > removeEnd) {
            remaining.add(removeEnd + 1 to tEnd)
        }
    }
    return RangeDifferenceResult(remaining, removed)
}

/** Posted when a chapter's read status changes. All BibleViews should update their mark-as-read button. */
class ChapterReadStatusChangedEvent(
    val kjvBookOrdinal: Int,
    val chapter: Int,
    val isRead: Boolean,
)

/** Posted when global reading progress settings change. All BibleViews should update their settings. */
class ReadingProgressSettingsChangedEvent

/** Posted when memorized verses or memorization targets change. All BibleViews should update their indicators. */
class MemorizationDataChangedEvent(
    val addedMemorized: List<Int> = emptyList(),
    val removedMemorized: List<Int> = emptyList(),
    val addedTargets: List<Int> = emptyList(),
    val removedTargets: List<Int> = emptyList(),
)

object ProgressControl {
    private val dao get() = DatabaseContainer.instance.progressDb.progressDao()

    var autoMarkMemorized: Boolean
        get() = ReadingProgressSettings.autoMarkMemorized
        set(value) { ReadingProgressSettings.autoMarkMemorized = value }

    fun markVerseMemorized(verseRange: VerseRange) {
        val kjvRange = verseRange.toV11n(KJVA)
        val added = mutableListOf<Int>()
        for (ordinal in kjvRange.start.ordinal..kjvRange.end.ordinal) {
            if (!dao.isVerseMemorized(ordinal)) {
                dao.insertMemorizedVerse(MemorizedVerse(kjvOrdinal = ordinal))
                added.add(ordinal)
            }
        }
        if (added.isNotEmpty()) {
            ABEventBus.post(MemorizationDataChangedEvent(addedMemorized = added))
        }
    }

    fun unmarkVerseMemorized(verseRange: VerseRange) {
        val kjvRange = verseRange.toV11n(KJVA)
        val removed = (kjvRange.start.ordinal..kjvRange.end.ordinal).toList()
        dao.deleteMemorizedVersesInRange(kjvRange.start.ordinal, kjvRange.end.ordinal)
        ABEventBus.post(MemorizationDataChangedEvent(removedMemorized = removed))
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
            ABEventBus.post(ChapterReadStatusChangedEvent(kjvBook.ordinal, chapter, true))
        }
    }

    fun unmarkChapterRead(v11n: Versification, book: BibleBook, chapter: Int) {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        val cycle = getCurrentCycle()
        if (dao.isChapterRead(kjvBook.ordinal, chapter, cycle)) {
            dao.deleteChapterReadingRecord(kjvBook.ordinal, chapter, cycle)
            ABEventBus.post(ChapterReadStatusChangedEvent(kjvBook.ordinal, chapter, false))
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
            if (!Scripture.isScripture(book)) continue
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
            if (!Scripture.isScripture(book)) continue
            val totalChapters = KJVA.getLastChapter(book)
            if (totalChapters <= 0) continue
            val readChapters = dao.countReadChaptersForBook(book.ordinal, cycle)
            if (readChapters > 0) {
                result[book] = readChapters.toFloat() / totalChapters
            }
        }
        return result
    }

    // Memorization target methods

    fun addMemorizationTarget(verseRange: VerseRange): MemorizationTarget {
        val kjvRange = verseRange.toV11n(KJVA)
        val target = MemorizationTarget(
            kjvOrdinalStart = kjvRange.start.ordinal,
            kjvOrdinalEnd = kjvRange.end.ordinal,
        )
        dao.insertMemorizationTarget(target)
        val added = (kjvRange.start.ordinal..kjvRange.end.ordinal).toList()
        ABEventBus.post(MemorizationDataChangedEvent(addedTargets = added))
        return target
    }

    fun removeMemorizationTarget(id: IdType) {
        val target = dao.allMemorizationTargets().find { it.id == id }
        dao.deleteMemorizationTarget(id)
        if (target != null) {
            val removed = (target.kjvOrdinalStart..target.kjvOrdinalEnd).toList()
            ABEventBus.post(MemorizationDataChangedEvent(removedTargets = removed))
        }
    }

    /**
     * Removes the intersection of the given range from overlapping targets (set difference).
     * Targets fully covered are deleted. Partially covered targets are split into remaining parts.
     */
    fun removeMemorizationTargetByRange(verseRange: VerseRange) {
        val kjvRange = verseRange.toV11n(KJVA)
        val overlapping = dao.memorizationTargetsOverlapping(kjvRange.start.ordinal, kjvRange.end.ordinal)
        if (overlapping.isEmpty()) return

        val result = computeRangeDifference(
            overlapping.map { it.kjvOrdinalStart to it.kjvOrdinalEnd },
            kjvRange.start.ordinal,
            kjvRange.end.ordinal,
        )

        for (target in overlapping) {
            dao.deleteMemorizationTarget(target.id)
        }
        for ((start, end) in result.remaining) {
            dao.insertMemorizationTarget(MemorizationTarget(kjvOrdinalStart = start, kjvOrdinalEnd = end))
        }
        if (result.removed.isNotEmpty()) {
            ABEventBus.post(MemorizationDataChangedEvent(removedTargets = result.removed))
        }
    }

    fun getAllMemorizationTargets(): List<MemorizationTarget> = dao.allMemorizationTargets()

    /** Total number of verses across all memorization targets. */
    fun getTargetTotalVerses(): Int =
        dao.allMemorizationTargets().sumOf { it.verseCount }

    /**
     * Returns memorization target progress: (memorized verses in targets, total target verses).
     */
    fun getMemorizationTargetProgress(): Pair<Int, Int> {
        val targets = dao.allMemorizationTargets()
        if (targets.isEmpty()) return 0 to 0
        val totalTarget = targets.sumOf { it.verseCount }
        val memorizedInTargets = targets.sumOf { target ->
            dao.countMemorizedVersesInRange(target.kjvOrdinalStart, target.kjvOrdinalEnd)
        }
        return memorizedInTargets to totalTarget
    }

    /**
     * Groups consecutive memorized verses into VerseRange objects for display.
     */
    fun getMemorizedVerseRanges(): List<VerseRange> {
        val allVerses = dao.allMemorizedVerses().sortedBy { it.kjvOrdinal }
        if (allVerses.isEmpty()) return emptyList()

        val ranges = mutableListOf<VerseRange>()
        var rangeStart = allVerses.first().kjvOrdinal
        var rangeEnd = rangeStart

        for (i in 1 until allVerses.size) {
            val ordinal = allVerses[i].kjvOrdinal
            if (ordinal == rangeEnd + 1) {
                rangeEnd = ordinal
            } else {
                ranges.add(VerseRange(KJVA, Verse(KJVA, rangeStart), Verse(KJVA, rangeEnd)))
                rangeStart = ordinal
                rangeEnd = ordinal
            }
        }
        ranges.add(VerseRange(KJVA, Verse(KJVA, rangeStart), Verse(KJVA, rangeEnd)))
        return ranges
    }

    /** Returns memorized KJV ordinals within the given range (for BibleView indicators). */
    fun getMemorizedOrdinalsInRange(startOrdinal: Int, endOrdinal: Int): List<Int> =
        dao.memorizedOrdinalsInRange(startOrdinal, endOrdinal)

    /** Returns target KJV ordinals within the given range (for BibleView indicators). */
    fun getTargetOrdinalsInRange(startOrdinal: Int, endOrdinal: Int): List<Int> {
        val targets = dao.memorizationTargetsOverlapping(startOrdinal, endOrdinal)
        val ordinals = mutableListOf<Int>()
        for (target in targets) {
            val start = maxOf(target.kjvOrdinalStart, startOrdinal)
            val end = minOf(target.kjvOrdinalEnd, endOrdinal)
            for (ordinal in start..end) {
                ordinals.add(ordinal)
            }
        }
        return ordinals
    }
}
