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
    val count: Int,
)

@Dao
interface ProgressDao {
    // Memorization queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemorizedVerse(verse: MemorizedVerse)

    @Query("DELETE FROM MemorizedVerse WHERE kjvOrdinal = :kjvOrdinal")
    suspend fun deleteMemorizedVerse(kjvOrdinal: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM MemorizedVerse WHERE kjvOrdinal = :kjvOrdinal)")
    suspend fun isVerseMemorized(kjvOrdinal: Int): Boolean

    @Query("SELECT COUNT(*) FROM MemorizedVerse WHERE kjvOrdinal >= :startOrdinal AND kjvOrdinal <= :endOrdinal")
    suspend fun countMemorizedVersesInRange(startOrdinal: Int, endOrdinal: Int): Int

    @Query("SELECT * FROM MemorizedVerse ORDER BY memorizedAt DESC")
    suspend fun allMemorizedVerses(): List<MemorizedVerse>

    @Query("SELECT COUNT(*) FROM MemorizedVerse")
    suspend fun countTotalMemorizedVerses(): Int

    @Query("SELECT kjvOrdinal FROM MemorizedVerse WHERE kjvOrdinal >= :startOrdinal AND kjvOrdinal <= :endOrdinal ORDER BY kjvOrdinal")
    suspend fun memorizedOrdinalsInRange(startOrdinal: Int, endOrdinal: Int): List<Int>

    @Query("DELETE FROM MemorizedVerse WHERE kjvOrdinal >= :startOrdinal AND kjvOrdinal <= :endOrdinal")
    suspend fun deleteMemorizedVersesInRange(startOrdinal: Int, endOrdinal: Int)

    /** Raw memorization timestamps in the given range. Bucketing into local days happens in Kotlin
     *  (see [ProgressControl.getMemorizationCalendar]) because SQLite cannot resolve the device timezone. */
    @Query("SELECT memorizedAt FROM MemorizedVerse WHERE memorizedAt >= :startMs AND memorizedAt <= :endMs")
    suspend fun getMemorizationTimestamps(startMs: Long, endMs: Long): List<Long>

    // Memorization target queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemorizationTarget(target: MemorizationTarget)

    @Query("DELETE FROM MemorizationTarget WHERE id = :id")
    suspend fun deleteMemorizationTarget(id: IdType)

    @Query("SELECT * FROM MemorizationTarget WHERE kjvOrdinalStart = :startOrdinal AND kjvOrdinalEnd = :endOrdinal")
    suspend fun findMemorizationTarget(startOrdinal: Int, endOrdinal: Int): MemorizationTarget?

    @Query("SELECT * FROM MemorizationTarget ORDER BY createdAt DESC")
    suspend fun allMemorizationTargets(): List<MemorizationTarget>

    @Query("SELECT COUNT(*) FROM MemorizationTarget")
    suspend fun countMemorizationTargets(): Int

    @Query("SELECT * FROM MemorizationTarget WHERE kjvOrdinalStart <= :endOrdinal AND kjvOrdinalEnd >= :startOrdinal")
    suspend fun memorizationTargetsOverlapping(startOrdinal: Int, endOrdinal: Int): List<MemorizationTarget>

    // Chapter read history queries — append-only history is the only data model.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterReadHistory(record: ChapterReadHistory)

    @Query("DELETE FROM ChapterReadHistory WHERE id = :id")
    suspend fun deleteChapterReadHistoryById(id: IdType)

    @Query("SELECT COUNT(*) FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND chapter = :chapter AND cycle = :cycle")
    suspend fun getChapterReadCount(kjvBookOrdinal: Int, chapter: Int, cycle: Int): Int

    @Query("SELECT * FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND chapter = :chapter AND cycle = :cycle ORDER BY readAt DESC")
    suspend fun getChapterReadHistory(kjvBookOrdinal: Int, chapter: Int, cycle: Int): List<ChapterReadHistory>

    @Query("SELECT * FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle ORDER BY readAt DESC")
    suspend fun getHistoryForBook(kjvBookOrdinal: Int, cycle: Int): List<ChapterReadHistory>

    @Query("SELECT * FROM ChapterReadHistory WHERE readAt >= :startMs AND readAt < :endMs AND cycle = :cycle ORDER BY readAt DESC")
    suspend fun getHistoryForDay(startMs: Long, endMs: Long, cycle: Int): List<ChapterReadHistory>

    @Query("SELECT COUNT(DISTINCT chapter) FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle")
    suspend fun getDistinctReadChaptersCountForBook(kjvBookOrdinal: Int, cycle: Int): Int

    @Query("SELECT COUNT(*) FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle")
    suspend fun getTotalReadCountForBook(kjvBookOrdinal: Int, cycle: Int): Int

    @Query("SELECT DISTINCT chapter FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle ORDER BY chapter")
    suspend fun getReadChaptersForBook(kjvBookOrdinal: Int, cycle: Int): List<Int>

    @Query("SELECT chapter, COUNT(*) as count FROM ChapterReadHistory WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle GROUP BY chapter")
    suspend fun getChapterReadCountsForBook(kjvBookOrdinal: Int, cycle: Int): List<ChapterReadCount>

    @Query("SELECT COUNT(DISTINCT (kjvBookOrdinal || ',' || chapter)) FROM ChapterReadHistory WHERE cycle = :cycle")
    suspend fun countDistinctChaptersRead(cycle: Int): Int

    /** Raw read timestamps for a cycle. Distinct local days are computed in Kotlin
     *  (see [ProgressControl.getDistinctReadDays]) because SQLite cannot resolve the device timezone. */
    @Query("SELECT readAt FROM ChapterReadHistory WHERE cycle = :cycle")
    suspend fun getAllReadingTimestampsForCycle(cycle: Int): List<Long>

    @Query("SELECT COALESCE(MAX(cycle), 1) FROM ChapterReadHistory")
    suspend fun getLatestCycle(): Int

    /** Raw read timestamps in the given range. Bucketing into local days happens in Kotlin
     *  (see [ProgressControl.getReadingCalendar]) because SQLite cannot resolve the device timezone. */
    @Query("SELECT readAt FROM ChapterReadHistory " +
        "WHERE readAt >= :startMs AND readAt <= :endMs AND cycle = :cycle")
    suspend fun getReadingTimestamps(startMs: Long, endMs: Long, cycle: Int): List<Long>
}

@Dao
interface GlobalReadingProgressSettingsDao {
    @Query("SELECT * FROM GlobalReadingProgressSettings LIMIT 1")
    suspend fun get(): GlobalReadingProgressSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(settings: GlobalReadingProgressSettings)
}
