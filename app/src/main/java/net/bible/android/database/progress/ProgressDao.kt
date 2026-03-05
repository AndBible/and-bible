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

package net.bible.android.database.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    // Reading queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapterReadingRecord(record: ChapterReadingRecord)

    @Query("SELECT EXISTS(SELECT 1 FROM ChapterReadingRecord WHERE kjvBookOrdinal = :kjvBookOrdinal AND chapter = :chapter AND cycle = :cycle)")
    fun isChapterRead(kjvBookOrdinal: Int, chapter: Int, cycle: Int): Boolean

    @Query("SELECT COUNT(*) FROM ChapterReadingRecord WHERE kjvBookOrdinal = :kjvBookOrdinal AND cycle = :cycle")
    fun countReadChaptersForBook(kjvBookOrdinal: Int, cycle: Int): Int

    @Query("SELECT COALESCE(MAX(cycle), 1) FROM ChapterReadingRecord")
    fun getLatestCycle(): Int

    @Query("SELECT * FROM ChapterReadingRecord WHERE cycle = :cycle ORDER BY readAt DESC")
    fun getRecordsForCycle(cycle: Int): List<ChapterReadingRecord>

    @Query("SELECT * FROM ChapterReadingRecord ORDER BY readAt DESC")
    fun allReadingRecords(): List<ChapterReadingRecord>
}
