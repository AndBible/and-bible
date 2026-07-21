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
package net.bible.android.control.progress

import android.text.format.DateFormat
import android.text.format.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.control.progress.ProgressControl.ChapterReadEntry
import net.bible.android.control.versification.Scripture
import net.bible.android.database.IdType
import net.bible.sharedcore.progress.BookHeat
import net.bible.sharedcore.progress.CalendarSkeleton
import net.bible.sharedcore.progress.ChapterDetail
import net.bible.sharedcore.progress.ChapterHeat
import net.bible.sharedcore.progress.DaySlot
import net.bible.sharedcore.progress.MemorizeSummaryData
import net.bible.sharedcore.progress.MonthLabel
import net.bible.sharedcore.progress.PassageRow
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.progress.ReadingSummary
import net.bible.sharedcore.progress.TargetRow
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Host (`:app`) implementation of the portable [ReadingProgressService] seam. Wraps the classic
 * [ProgressControl] (JSword/Room/`Calendar`/`DateFormat`) and maps its results to the plain
 * `:sharedCore` view-data types, so the shared Compose reading-progress UI needs no JSword or
 * Android platform types.
 *
 * `bookId` on the seam is always [BibleBook.name] (e.g. "GEN"); [BibleBook.valueOf] round-trips it.
 * Read-history entry `id` is [net.bible.android.database.IdType.toString]; since the interface only
 * carries the string id back for deletion, entries are cached here (keyed by that string) from the
 * last history load so [deleteReadHistoryEntries] can resolve them back to
 * [ProgressControl.ChapterReadEntry] for [ProgressControl.deleteReadHistoryEntries].
 */
class ReadingProgressServiceImpl : ReadingProgressService {
    private val kjva get() = Versifications.instance().getVersification("KJVA")

    /**
     * Last-loaded read-history entries, keyed by [ChapterReadEntry.id]'s string form. A
     * [ConcurrentHashMap] because this is a Koin singleton field mutated from multiple `suspend`
     * functions that all run on the shared multi-threaded [Dispatchers.IO] pool.
     */
    private val historyCache = ConcurrentHashMap<String, ChapterReadEntry>()

    // --- cycles ---

    override fun currentCycle(): Int = ProgressControl.getCurrentCycle()
    override fun latestCycle(): Int = ProgressControl.getLatestCycle()
    override fun setActiveCycle(cycle: Int) = ProgressControl.setActiveCycle(cycle)
    override fun startNewCycle(): Int = ProgressControl.startNewCycle()

    // --- reading data ---

    override suspend fun readingSummary(cycle: Int): ReadingSummary = withContext(Dispatchers.IO) {
        val total = ProgressControl.totalBibleChapters
        val read = ProgressControl.getTotalReadChapters(cycle)
        val activeDays = ProgressControl.getDistinctReadDays(cycle)
        val overallPermille = if (total > 0) read * 1000 / total else 0
        val overallPercent = if (total > 0) read * 100f / total else 0f
        ReadingSummary(
            chaptersRead = read,
            activeDays = activeDays,
            overallPermille = overallPermille,
            overallPercent = overallPercent,
        )
    }

    override suspend fun bookReadProgress(cycle: Int): List<BookHeat> = withContext(Dispatchers.IO) {
        val bookCountProgress = ProgressControl.getBookCountProgress(cycle)
        kjva.bookIterator.asSequence()
            .filter { Scripture.isScripture(it) }
            .map { book ->
                val cp = bookCountProgress[book]
                val isComplete = ProgressControl.getDistinctReadChaptersCountForBook(book, cycle) >= kjva.getLastChapter(book)
                BookHeat(
                    bookId = book.name,
                    shortName = kjva.getShortName(book),
                    isNT = book.ordinal >= BibleBook.MATT.ordinal,
                    readPercent = cp?.readPercent ?: 0f,
                    isComplete = isComplete,
                )
            }.toList()
    }

    override suspend fun chapterReadCounts(bookId: String, cycle: Int): ChapterDetail = withContext(Dispatchers.IO) {
        val book = BibleBook.valueOf(bookId)
        val counts = ProgressControl.getChapterReadCountsForBook(book, cycle)
        val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
        val totalChapters = kjva.getLastChapter(book)
        val chapters = (1..totalChapters).map { ch ->
            val count = counts[ch] ?: 0
            ChapterHeat(chapter = ch, count = count, level = ReadingProgressScale.heatLevel(count, maxCount))
        }
        ChapterDetail(
            bookId = bookId,
            title = kjva.getLongName(book),
            chapters = chapters,
            maxCount = maxCount,
            countScaleSteps = ReadingProgressScale.countScaleSteps(maxCount),
        )
    }

    /**
     * Ports [net.bible.android.view.activity.progress.CalendarHeatmapView.onDraw]'s day-walk
     * exactly: 52 weeks back from today, aligned to the locale's first day of week, local-midnight
     * timestamps (so they match [ProgressControl]'s local-day bucketing), walking day-by-day up to
     * (and including) today, with a month label whenever the month changes on the first day of a
     * week row.
     */
    override suspend fun readingCalendarSkeleton(): CalendarSkeleton = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        val today = cal.clone() as Calendar

        cal.add(Calendar.WEEK_OF_YEAR, -52)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val slots = mutableListOf<DaySlot>()
        val monthLabels = mutableListOf<MonthLabel>()
        var lastMonth = -1

        for (week in 0 until 53) {
            for (day in 0 until 7) {
                if (cal.after(today)) break

                slots.add(DaySlot(week, day, cal.timeInMillis))

                if (day == 0 && cal.get(Calendar.MONTH) != lastMonth) {
                    lastMonth = cal.get(Calendar.MONTH)
                    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
                    monthLabels.add(MonthLabel(week, monthName))
                }

                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        CalendarSkeleton(
            slots = slots,
            monthLabels = monthLabels,
            weeks = 53,
            dayOfWeekLabels = listOf("", "M", "", "W", "", "F", ""),
        )
    }

    override suspend fun dailyReadCounts(cycle: Int): Map<Long, Int> = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        val endMs = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, -52)
        val startMs = cal.timeInMillis
        ProgressControl.getReadingCalendar(startMs, endMs, cycle).associate { it.dayTimestamp to it.count }
    }

    // --- read history ---

    private fun ChapterReadEntry.toReadHistoryEntry(): ReadHistoryEntry {
        val idStr = id.toString()
        historyCache[idStr] = this
        return ReadHistoryEntry(
            id = idStr,
            // Classic parity: ReadHistoryDialog.kt uses BibleBook.entries.getOrNull(...) with a
            // "?" fallback for an out-of-range ordinal; "" is the equivalent unknown-book
            // sentinel here (bookShortName/bookLongName below map it back to "?").
            bookId = BibleBook.entries.getOrNull(kjvBookOrdinal)?.name ?: "",
            chapter = chapter,
            readAt = readAt,
            bookInitials = bookInitials,
        )
    }

    override suspend fun readHistoryForBook(bookId: String, cycle: Int): List<ReadHistoryEntry> =
        withContext(Dispatchers.IO) {
            ProgressControl.getReadHistoryForBook(BibleBook.valueOf(bookId), cycle).map { it.toReadHistoryEntry() }
        }

    override suspend fun readHistoryForChapter(bookId: String, chapter: Int, cycle: Int): List<ReadHistoryEntry> =
        withContext(Dispatchers.IO) {
            ProgressControl.getReadHistoryForChapter(BibleBook.valueOf(bookId), chapter, cycle)
                .map { it.toReadHistoryEntry() }
        }

    override suspend fun readHistoryForDay(dayTimestamp: Long, cycle: Int): List<ReadHistoryEntry> =
        withContext(Dispatchers.IO) {
            ProgressControl.getReadHistoryForDay(dayTimestamp, cycle).map { it.toReadHistoryEntry() }
        }

    override suspend fun deleteReadHistoryEntries(ids: List<String>, cycle: Int): Unit =
        withContext(Dispatchers.IO) {
            val entries = ids.mapNotNull { historyCache[it] }
            if (entries.isEmpty()) return@withContext
            ProgressControl.deleteReadHistoryEntries(entries, cycle)
            entries.forEach { historyCache.remove(it.id.toString()) }
        }

    // --- display helpers ---

    override fun dayTitle(dayTimestamp: Long): String =
        DateFormat.getDateFormat(application).format(Date(dayTimestamp))

    override fun formatEntryDate(readAt: Long): String =
        DateFormat.getDateFormat(application).format(Date(readAt))

    override fun formatEntryTime(readAt: Long): String =
        DateFormat.getTimeFormat(application).format(Date(readAt))

    override fun bookShortName(bookId: String): String =
        if (bookId.isEmpty()) "?" else kjva.getShortName(BibleBook.valueOf(bookId))

    override fun bookLongName(bookId: String): String =
        if (bookId.isEmpty()) "?" else kjva.getLongName(BibleBook.valueOf(bookId))

    // --- memorize ---

    private fun formatRelative(timestampMs: Long): String =
        DateUtils.getRelativeTimeSpanString(
            timestampMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()

    override suspend fun memorizeSummary(): MemorizeSummaryData = withContext(Dispatchers.IO) {
        val memorizedCount = ProgressControl.getTotalMemorizedVerses()
        val (targetMemorized, targetTotal) = ProgressControl.getMemorizationTargetProgress()
        MemorizeSummaryData(memorizedCount = memorizedCount, targetMemorized = targetMemorized, targetTotal = targetTotal)
    }

    override suspend fun bookMemorizationProgress(): List<BookHeat> = withContext(Dispatchers.IO) {
        val progress = ProgressControl.getBookMemorizationProgress()
        val targets = ProgressControl.getBooksWithMemorizationTargets()
        kjva.bookIterator.asSequence()
            .filter { Scripture.isScripture(it) }
            .map { book ->
                val readPercent = progress[book] ?: 0f
                BookHeat(
                    bookId = book.name,
                    shortName = kjva.getShortName(book),
                    isNT = book.ordinal >= BibleBook.MATT.ordinal,
                    readPercent = readPercent,
                    isComplete = readPercent >= 1f,
                    hasTarget = book in targets,
                )
            }.toList()
    }

    override suspend fun chapterMemorizationProgress(bookId: String): ChapterDetail = withContext(Dispatchers.IO) {
        val book = BibleBook.valueOf(bookId)
        val totalChapters = kjva.getLastChapter(book)
        val targetChapters = ProgressControl.getChaptersWithMemorizationTargets(book)
        val chapters = (1..totalChapters).map { ch ->
            val progress = ProgressControl.getMemorizationProgress(kjva, book, ch)
            ChapterHeat(
                chapter = ch,
                count = 0,
                level = ReadingProgressScale.memorizationLevel(progress),
                hasTarget = ch in targetChapters,
            )
        }
        ChapterDetail(
            bookId = bookId,
            title = kjva.getLongName(book),
            chapters = chapters,
            maxCount = 1,
            countScaleSteps = emptyList(),
        )
    }

    override suspend fun dailyMemorizationCounts(): Map<Long, Int> = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        val endMs = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, -52)
        val startMs = cal.timeInMillis
        ProgressControl.getMemorizationCalendar(startMs, endMs).associate { it.dayTimestamp to it.count }
    }

    override suspend fun memorizedPassages(): List<PassageRow> = withContext(Dispatchers.IO) {
        ProgressControl.getMemorizedVerseRangesWithTimestamps().map { r ->
            PassageRow(
                rangeName = r.verseRange.name,
                startOrdinal = r.verseRange.start.ordinal,
                endOrdinal = r.verseRange.end.ordinal,
                relativeTime = formatRelative(r.latestMemorizedAt),
            )
        }
    }

    override suspend fun memorizeTargets(): List<TargetRow> = withContext(Dispatchers.IO) {
        ProgressControl.getAllMemorizationTargets()
            .mapNotNull { t ->
                val memorized = ProgressControl.getMemorizedOrdinalsInRange(t.kjvOrdinalStart, t.kjvOrdinalEnd).size
                if (memorized >= t.verseCount) return@mapNotNull null
                TargetRow(
                    id = t.id.toString(),
                    rangeName = t.verseRange.name,
                    memorized = memorized,
                    total = t.verseCount,
                    startOrdinal = t.verseRange.start.ordinal,
                    endOrdinal = t.verseRange.end.ordinal,
                    relativeTime = formatRelative(t.createdAt),
                )
            }
    }

    override suspend fun unmarkMemorized(startOrdinal: Int, endOrdinal: Int): Unit = withContext(Dispatchers.IO) {
        ProgressControl.unmarkVerseMemorized(VerseRange(kjva, Verse(kjva, startOrdinal), Verse(kjva, endOrdinal)))
    }

    override suspend fun removeMemorizationTarget(id: String): Unit = withContext(Dispatchers.IO) {
        ProgressControl.removeMemorizationTarget(IdType(id))
    }

    // --- extra: host-only helper for the chapter-tap result Intent ---

    /** OSIS id (e.g. "Gen.1.1") for a chapter, used by the host for the chapter-tap result `Intent`. */
    fun osisIdForChapter(bookId: String, chapter: Int): String =
        Verse(kjva, BibleBook.valueOf(bookId), chapter, 1).osisID
}
