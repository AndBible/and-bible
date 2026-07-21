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

package net.bible.sharedcore.progress

interface ReadingProgressService {
    // --- cycles ---
    fun currentCycle(): Int
    fun latestCycle(): Int
    fun setActiveCycle(cycle: Int)
    fun startNewCycle(): Int

    // --- reading data (loaded off the main thread by the impl) ---
    suspend fun readingSummary(cycle: Int): ReadingSummary
    /** All scripture books with readPercent + completion; caller splits OT/NT via [BookHeat.isNT]. */
    suspend fun bookReadProgress(cycle: Int): List<BookHeat>
    suspend fun chapterReadCounts(bookId: String, cycle: Int): ChapterDetail
    /** Platform day-walk skeleton for the last 52 weeks (uses Calendar; DST-correct). */
    suspend fun readingCalendarSkeleton(): CalendarSkeleton
    /** Per-local-day read counts for the last 52 weeks, keyed by local-midnight ms. */
    suspend fun dailyReadCounts(cycle: Int): Map<Long, Int>

    // --- read history (for AbReadHistoryDialog) ---
    suspend fun readHistoryForBook(bookId: String, cycle: Int): List<ReadHistoryEntry>
    suspend fun readHistoryForChapter(bookId: String, chapter: Int, cycle: Int): List<ReadHistoryEntry>
    suspend fun readHistoryForDay(dayTimestamp: Long, cycle: Int): List<ReadHistoryEntry>
    suspend fun deleteReadHistoryEntries(ids: List<String>, cycle: Int)

    // --- display helpers (platform formatting) ---
    fun dayTitle(dayTimestamp: Long): String        // DateFormat.getDateFormat(...)
    fun formatEntryDate(readAt: Long): String        // DateFormat date
    fun formatEntryTime(readAt: Long): String        // DateFormat time
    fun bookShortName(bookId: String): String
    fun bookLongName(bookId: String): String
}
