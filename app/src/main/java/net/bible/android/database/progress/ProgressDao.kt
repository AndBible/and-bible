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

package net.bible.android.database.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.bible.android.database.IdType

data class DailyReadingCount(
    val dayTimestamp: Long,
    val count: Int,
)

data class ChapterReadCount(
    val chapter: Int,
    val cnt: Int,
)

@Dao
interface ProgressDao {
    // Memorization queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMemorizedVerse(verse: MemorizedVerse)

    @Query("DELETE FROM MemorizedVerse WHERE kjvOrdinal = :kjvOrdinal")
    fun deleteMemorizedVerse(kjvOrdinal: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM MemorizedVerse WHERE kjvOrdinal = :kjvOrdinal)")
    fun isVerseMemorized(kjvOrdinal: Int): Boolean

    @Query("SELECT COUNT(*) FROM MemorizedVerse WHERE kjvOrdinal >= :startOrdinal AND kjvOrdinal <= :endOrdinal")
    fun countMemorizedVersesInRange(startOrdinal: Int, endOrdinal: Int): Int

    @Query("SELECT * FROM MemorizedVerse ORDER BY memorizedAt DESC")
    fun allMemorizedVerses(): List<MemorizedVerse>

    @Query("SELECT COUNT(*) FROM MemorizedVerse")
    fun countTotalMemorizedVerses(): Int

    @Query("SELECT kjvOrdinal FROM MemorizedVerse WHERE kjvOrdinal >= :startOrdinal AND kjvOrdinal <= :endOrdinal ORDER BY kjvOrdinal")
    fun memorizedOrdinalsInRange(startOrdinal: Int, endOrdinal: Int): List<Int>

    @Query("DELETE FROM MemorizedVerse WHERE kjvOrdinal >= :startOrdinal AND kjvOrdinal <= :endOrdinal")
    fun deleteMemorizedVersesInRange(startOrdinal: Int, endOrdinal: Int)

    @Query("SELECT (memorizedAt / 86400000) * 86400000 AS dayTimestamp, COUNT(*) AS count " +
        "FROM MemorizedVerse " +
        "WHERE memorizedAt >= :startMs AND memorizedAt <= :endMs " +
        "GROUP BY memorizedAt / 86400000 " +
        "ORDER BY dayTimestamp")
    fun getMemorizationCalendar(startMs: Long, endMs: Long): List<DailyReadingCount>

    // Memorization target queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMemorizationTarget(target: MemorizationTarget)

    @Query("DELETE FROM MemorizationTarget WHERE id = :id")
    fun deleteMemorizationTarget(id: IdType)

    @Query("SELECT * FROM MemorizationTarget WHERE kjvOrdinalStart = :startOrdinal AND kjvOrdinalEnd = :endOrdinal")
    fun findMemorizationTarget(startOrdinal: Int, endOrdinal: Int): MemorizationTarget?

    @Query("SELECT * FROM MemorizationTarget ORDER BY createdAt DESC")
    fun allMemorizationTargets(): List<MemorizationTarget>

    @Query("SELECT COUNT(*) FROM MemorizationTarget")
    fun countMemorizationTargets(): Int

    @Query("SELECT * FROM MemorizationTarget WHERE kjvOrdinalStart <= :endOrdinal AND kjvOrdinalEnd >= :startOrdinal")
    fun memorizationTargetsOverlapping(startOrdinal: Int, endOrdinal: Int): List<MemorizationTarget>

    // Chapter read history queries — append-only history is the only data model.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapterReadHistory(record: ChapterReadHistory)

    @Query("DELETE FROM ChapterReadHistory WHERE id = :id")
    fun deleteChapterReadHistoryById(id: IdType)

    @Query("DELETE FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND chapter = :chapter AND cycle = :cycle")
    fun deleteAllReadsForChapter(kjvBookOrdinal: Int, chapter: Int, cycle: Int)

    @Query("SELECT COUNT(*) FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND chapter = :chapter AND cycle = :cycle")
    fun getChapterReadCount(kjvBookOrdinal: Int, chapter: Int, cycle: Int): Int

    @Query("SELECT * FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND chapter = :chapter AND cycle = :cycle ORDER BY readAt DESC")
    fun getChapterReadHistory(kjvBookOrdinal: Int, chapter: Int, cycle: Int): List<ChapterReadHistory>

    @Query("SELECT * FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle ORDER BY readAt DESC")
    fun getHistoryForBook(kjvBookOrdinal: Int, cycle: Int): List<ChapterReadHistory>

    @Query("SELECT * FROM ChapterReadHistory WHERE readAt >= :startMs AND readAt < :endMs AND cycle = :cycle ORDER BY readAt DESC")
    fun getHistoryForDay(startMs: Long, endMs: Long, cycle: Int): List<ChapterReadHistory>

    @Query("SELECT COALESCE(MAX(cnt), 0) FROM (SELECT COUNT(*) as cnt FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle GROUP BY chapter)")
    fun getMaxReadCountForBook(kjvBookOrdinal: Int, cycle: Int): Int

    @Query("SELECT COUNT(DISTINCT chapter) FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle")
    fun getDistinctReadChaptersCountForBook(kjvBookOrdinal: Int, cycle: Int): Int

    @Query("SELECT COUNT(*) FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle")
    fun getTotalReadCountForBook(kjvBookOrdinal: Int, cycle: Int): Int

    @Query("SELECT DISTINCT chapter FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle ORDER BY chapter")
    fun getReadChaptersForBook(kjvBookOrdinal: Int, cycle: Int): List<Int>

    @Query("SELECT chapter, COUNT(*) as cnt FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle GROUP BY chapter")
    fun getChapterReadCountsForBook(kjvBookOrdinal: Int, cycle: Int): List<ChapterReadCount>

    @Query("SELECT COUNT(DISTINCT (kjvBookOrdinal || ',' || chapter)) FROM ChapterReadHistory WHERE cycle = :cycle")
    fun countDistinctChaptersRead(cycle: Int): Int

    @Query("SELECT COUNT(DISTINCT (readAt / 86400000)) FROM ChapterReadHistory WHERE cycle = :cycle")
    fun countDistinctReadDays(cycle: Int): Int

    @Query("SELECT COUNT(*) FROM ChapterReadHistory WHERE cycle = :cycle")
    fun countTotalReads(cycle: Int): Int

    @Query("SELECT COALESCE(MAX(cycle), 1) FROM ChapterReadHistory")
    fun getLatestCycle(): Int

    @Query("SELECT (readAt / 86400000) * 86400000 AS dayTimestamp, COUNT(*) AS count " +
        "FROM ChapterReadHistory " +
        "WHERE readAt >= :startMs AND readAt <= :endMs AND cycle = :cycle " +
        "GROUP BY readAt / 86400000 ORDER BY dayTimestamp")
    fun getReadingCalendar(startMs: Long, endMs: Long, cycle: Int): List<DailyReadingCount>
}

@Dao
interface GlobalReadingProgressSettingsDao {
    @Query("SELECT * FROM GlobalReadingProgressSettings LIMIT 1")
    fun get(): GlobalReadingProgressSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(settings: GlobalReadingProgressSettings)
}
