/*
 * Copyright (c) 2024-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.database.progress.ChapterReadHistory
import net.bible.android.database.progress.DailyReadingCount
import net.bible.android.database.progress.MemorizationTarget
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.progress.ReadingSource
import net.bible.service.db.DatabaseContainer
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import java.util.Calendar

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
    val count: Int,
)

/** Posted when global reading progress settings change. All BibleViews should update their settings. */
class ReadingProgressSettingsChangedEvent

/** Posted when the active reading cycle changes. All BibleViews should re-check chapter read status. */
class ActiveCycleChangedEvent(val cycle: Int)

data class MemorizedVerseRangeWithTimestamp(
    val verseRange: VerseRange,
    val latestMemorizedAt: Long,
)

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

    /**
     * Records a new read-history row. Each call adds one row regardless of existing reads.
     *
     * Idempotency for the auto-track-while-scrolling source is enforced JS-side via the
     * `autoTrackDone` flag in `reading-tracker.ts`: an `IntersectionObserver` cleans up after
     * firing, so this method can be called at most once per mount cycle for an auto-track event.
     * Manual taps always increment.
     */
    fun recordChapterRead(
        v11n: Versification,
        book: BibleBook,
        chapter: Int,
        bookInitials: String = "",
        source: ReadingSource = ReadingSource.MANUAL,
    ) {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        val cycle = getCurrentCycle()
        dao.insertChapterReadHistory(
            ChapterReadHistory(
                kjvBookOrdinal = kjvBook.ordinal,
                chapter = chapter,
                cycle = cycle,
                readAt = System.currentTimeMillis(),
                bookInitials = bookInitials,
                source = source,
            )
        )
        val newCount = dao.getChapterReadCount(kjvBook.ordinal, chapter, cycle)
        ABEventBus.post(ChapterReadStatusChangedEvent(kjvBook.ordinal, chapter, newCount))
    }

    fun isChapterRead(v11n: Versification, book: BibleBook, chapter: Int): Boolean {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        return dao.getChapterReadCount(kjvBook.ordinal, chapter, getCurrentCycle()) > 0
    }

    fun getReadingProgress(v11n: Versification, book: BibleBook): Float {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        val totalChapters = KJVA.getLastChapter(kjvBook)
        if (totalChapters <= 0) return 0f
        val readChapters = dao.getDistinctReadChaptersCountForBook(kjvBook.ordinal, getCurrentCycle())
        return readChapters.toFloat() / totalChapters
    }

    /** Display model for a single read-history entry shown in the history dialog. */
    data class ChapterReadEntry(
        val id: IdType,
        val kjvBookOrdinal: Int,
        val chapter: Int,
        val readAt: Long,
        /** SWORD book initials of the version used; empty string if unknown. */
        val bookInitials: String,
    )

    private fun ChapterReadHistory.toEntry() =
        ChapterReadEntry(id, kjvBookOrdinal, chapter, readAt, bookInitials)

    /** Returns all history entries for a book, newest first. */
    fun getReadHistoryForBook(book: BibleBook, cycle: Int = getCurrentCycle()): List<ChapterReadEntry> =
        dao.getHistoryForBook(book.ordinal, cycle).map { it.toEntry() }

    /**
     * Returns all history entries for a calendar day, newest first.
     *
     * [dayTimestamp] is the local-midnight timestamp of the target day (in UTC ms), as produced
     * by [bucketByLocalDay] / [CalendarHeatmapView]. The end of the window is computed via
     * [Calendar.add] so DST transitions (23h or 25h "days") are handled correctly.
     */
    fun getReadHistoryForDay(dayTimestamp: Long, cycle: Int = getCurrentCycle()): List<ChapterReadEntry> {
        val cal = Calendar.getInstance().apply { timeInMillis = dayTimestamp }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val nextDayMs = cal.timeInMillis
        return dao.getHistoryForDay(dayTimestamp, nextDayMs, cycle).map { it.toEntry() }
    }

    /** Returns all history entries for a single chapter, newest first. */
    fun getReadHistoryForChapter(book: BibleBook, chapter: Int, cycle: Int = getCurrentCycle()): List<ChapterReadEntry> =
        dao.getChapterReadHistory(book.ordinal, chapter, cycle).map { it.toEntry() }

    /** Deletes a single history entry by ID and refreshes the final read state for that chapter. */
    fun deleteReadHistoryEntry(entry: ChapterReadEntry, cycle: Int = getCurrentCycle()) {
        deleteReadHistoryEntries(listOf(entry), cycle)
    }

    /** Deletes multiple history entries and refreshes the final read state for each affected chapter. */
    fun deleteReadHistoryEntries(entries: List<ChapterReadEntry>, cycle: Int = getCurrentCycle()) {
        if (entries.isEmpty()) return
        entries.forEach { dao.deleteChapterReadHistoryById(it.id) }
        entries.distinctBy { it.kjvBookOrdinal to it.chapter }.forEach { entry ->
            val newCount = dao.getChapterReadCount(entry.kjvBookOrdinal, entry.chapter, cycle)
            ABEventBus.post(ChapterReadStatusChangedEvent(entry.kjvBookOrdinal, entry.chapter, newCount))
        }
    }

    /** Get total read count for a chapter. */
    fun getChapterReadCount(v11n: Versification, book: BibleBook, chapter: Int): Int {
        val kjvBook = Verse(v11n, book, 1, 1).toV11n(KJVA).book
        return dao.getChapterReadCount(kjvBook.ordinal, chapter, getCurrentCycle())
    }

    fun getCurrentCycle(): Int {
        val stored = ReadingProgressSettings.activeCycle
        return if (stored > 0) stored else dao.getLatestCycle()
    }

    fun getLatestCycle(): Int = dao.getLatestCycle()

    fun setActiveCycle(cycle: Int) {
        ReadingProgressSettings.activeCycle = cycle
        ABEventBus.post(ActiveCycleChangedEvent(cycle))
    }

    fun startNewCycle(): Int {
        val newCycle = getLatestCycle() + 1
        setActiveCycle(newCycle)
        return newCycle
    }

    // Statistics methods for ReadingProgressActivity

    /** Distinct chapters read at least once in the cycle. */
    fun getTotalReadChapters(cycle: Int = getCurrentCycle()): Int = dao.countDistinctChaptersRead(cycle)

    /**
     * Distinct *local* calendar days on which any chapter was tapped (or auto-marked) in the cycle.
     * Bucketing must happen in Kotlin because SQLite cannot resolve the device timezone — see
     * [bucketByLocalDay] for the rationale.
     */
    fun getDistinctReadDays(cycle: Int = getCurrentCycle()): Int {
        val cal = Calendar.getInstance()
        return dao.getAllReadingTimestampsForCycle(cycle)
            .mapTo(HashSet()) { localDayStart(it, cal) }
            .size
    }

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

    fun getReadingCalendar(startMs: Long, endMs: Long, cycle: Int = getCurrentCycle()): List<DailyReadingCount> {
        return bucketByLocalDay(dao.getReadingTimestamps(startMs, endMs, cycle))
    }

    fun getBookReadingProgress(cycle: Int = getCurrentCycle()): Map<BibleBook, Float> {
        val result = mutableMapOf<BibleBook, Float>()
        for (book in KJVA.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val totalChapters = KJVA.getLastChapter(book)
            if (totalChapters <= 0) continue
            val readChapters = dao.getDistinctReadChaptersCountForBook(book.ordinal, cycle)
            if (readChapters > 0) {
                result[book] = readChapters.toFloat() / totalChapters
            }
        }
        return result
    }

    /** Returns map of chapter number → read count for a book. */
    fun getChapterReadCountsForBook(book: BibleBook, cycle: Int = getCurrentCycle()): Map<Int, Int> =
        dao.getChapterReadCountsForBook(book.ordinal, cycle).associate { it.chapter to it.count }

    /** Returns the number of distinct chapters read at least once in history for a book. */
    fun getDistinctReadChaptersCountForBook(book: BibleBook, cycle: Int = getCurrentCycle()): Int =
        dao.getDistinctReadChaptersCountForBook(book.ordinal, cycle)

    /**
     * Data for a book's count-mode heat map.
     * [readPercent] = totalReads / totalChapters (1.0 = 100%, 2.0 = 200%, etc.)
     */
    data class BookCountProgress(val readPercent: Float)

    fun getBookCountProgress(cycle: Int = getCurrentCycle()): Map<BibleBook, BookCountProgress> {
        val result = mutableMapOf<BibleBook, BookCountProgress>()
        for (book in KJVA.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val totalChapters = KJVA.getLastChapter(book)
            if (totalChapters <= 0) continue
            val totalReads = dao.getTotalReadCountForBook(book.ordinal, cycle)
            if (totalReads > 0) {
                result[book] = BookCountProgress(readPercent = totalReads.toFloat() / totalChapters)
            }
        }
        return result
    }

    fun getMemorizationCalendar(startMs: Long, endMs: Long): List<DailyReadingCount> {
        return bucketByLocalDay(dao.getMemorizationTimestamps(startMs, endMs))
    }

    /**
     * Returns the local-midnight (in UTC ms) of the day containing [timestamp].
     * The reusable [cal] avoids per-call allocation when bucketing many timestamps.
     */
    private fun localDayStart(timestamp: Long, cal: Calendar): Long {
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Buckets timestamps into local calendar days and returns counts per day, sorted by day.
     *
     * Bucketing happens here (not in SQL) because SQLite has no notion of the device timezone:
     * an expression like `(readAt / 86400000) * 86400000` always groups by *UTC* days, which
     * misaligns the heatmap for users not on UTC — see issue AndBible/and-bible#3800.
     * [Calendar] also handles DST transitions correctly.
     */
    private fun bucketByLocalDay(timestamps: List<Long>): List<DailyReadingCount> {
        if (timestamps.isEmpty()) return emptyList()
        val cal = Calendar.getInstance()
        val counts = HashMap<Long, Int>()
        for (ts in timestamps) {
            val key = localDayStart(ts, cal)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries.sortedBy { it.key }.map { DailyReadingCount(it.key, it.value) }
    }

    /**
     * Returns the set of Bible books that have at least one memorization target overlapping them.
     */
    fun getBooksWithMemorizationTargets(): Set<BibleBook> {
        val targets = dao.allMemorizationTargets()
        if (targets.isEmpty()) return emptySet()
        val result = mutableSetOf<BibleBook>()
        for (book in KJVA.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val startOrdinal = Verse(KJVA, book, 1, 1).ordinal
            val lastChapter = KJVA.getLastChapter(book)
            val lastVerse = KJVA.getLastVerse(book, lastChapter)
            val endOrdinal = Verse(KJVA, book, lastChapter, lastVerse).ordinal
            if (targets.any { it.kjvOrdinalStart <= endOrdinal && it.kjvOrdinalEnd >= startOrdinal }) {
                result.add(book)
            }
        }
        return result
    }

    /**
     * Returns chapters of a book that have at least one memorization target overlapping them.
     */
    fun getChaptersWithMemorizationTargets(book: BibleBook): Set<Int> {
        val startOrdinal = Verse(KJVA, book, 1, 1).ordinal
        val lastChapter = KJVA.getLastChapter(book)
        val lastVerse = KJVA.getLastVerse(book, lastChapter)
        val endOrdinal = Verse(KJVA, book, lastChapter, lastVerse).ordinal
        val targets = dao.memorizationTargetsOverlapping(startOrdinal, endOrdinal)
        if (targets.isEmpty()) return emptySet()

        val result = mutableSetOf<Int>()
        for (ch in 1..lastChapter) {
            val chStart = Verse(KJVA, book, ch, 1).ordinal
            val chLastVerse = KJVA.getLastVerse(book, ch)
            val chEnd = Verse(KJVA, book, ch, chLastVerse).ordinal
            if (targets.any { it.kjvOrdinalStart <= chEnd && it.kjvOrdinalEnd >= chStart }) {
                result.add(ch)
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

    /** Adds a memorization target only if the exact range is not already a target. */
    fun addMemorizationTargetIfNeeded(verseRange: VerseRange) {
        val kjvRange = verseRange.toV11n(KJVA)
        if (dao.findMemorizationTarget(kjvRange.start.ordinal, kjvRange.end.ordinal) != null) return
        addMemorizationTarget(verseRange)
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
        return getMemorizedVerseRangesWithTimestamps().map { it.verseRange }
    }

    /**
     * Groups consecutive memorized verses into VerseRange objects with the latest memorizedAt timestamp per range.
     * Returns sorted by latestMemorizedAt descending (newest first).
     */
    fun getMemorizedVerseRangesWithTimestamps(): List<MemorizedVerseRangeWithTimestamp> {
        val allVerses = dao.allMemorizedVerses().sortedBy { it.kjvOrdinal }
        if (allVerses.isEmpty()) return emptyList()

        val ranges = mutableListOf<MemorizedVerseRangeWithTimestamp>()
        var rangeStart = allVerses.first().kjvOrdinal
        var rangeEnd = rangeStart
        var maxTimestamp = allVerses.first().memorizedAt

        for (i in 1 until allVerses.size) {
            val verse = allVerses[i]
            if (verse.kjvOrdinal == rangeEnd + 1) {
                rangeEnd = verse.kjvOrdinal
                if (verse.memorizedAt > maxTimestamp) maxTimestamp = verse.memorizedAt
            } else {
                ranges.add(MemorizedVerseRangeWithTimestamp(
                    VerseRange(KJVA, Verse(KJVA, rangeStart), Verse(KJVA, rangeEnd)),
                    maxTimestamp,
                ))
                rangeStart = verse.kjvOrdinal
                rangeEnd = verse.kjvOrdinal
                maxTimestamp = verse.memorizedAt
            }
        }
        ranges.add(MemorizedVerseRangeWithTimestamp(
            VerseRange(KJVA, Verse(KJVA, rangeStart), Verse(KJVA, rangeEnd)),
            maxTimestamp,
        ))
        return ranges.sortedByDescending { it.latestMemorizedAt }
    }

    /**
     * Returns memorization progress per Bible book as a map.
     * Mirrors [getBookReadingProgress] but for memorization.
     */
    fun getBookMemorizationProgress(): Map<BibleBook, Float> {
        val result = mutableMapOf<BibleBook, Float>()
        for (book in KJVA.bookIterator) {
            if (!Scripture.isScripture(book)) continue
            val startOrdinal = Verse(KJVA, book, 1, 1).ordinal
            val lastChapter = KJVA.getLastChapter(book)
            val lastVerse = KJVA.getLastVerse(book, lastChapter)
            val endOrdinal = Verse(KJVA, book, lastChapter, lastVerse).ordinal
            val totalVerses = endOrdinal - startOrdinal + 1
            if (totalVerses <= 0) continue
            val memorized = dao.countMemorizedVersesInRange(startOrdinal, endOrdinal)
            if (memorized > 0) {
                result[book] = memorized.toFloat() / totalVerses
            }
        }
        return result
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
