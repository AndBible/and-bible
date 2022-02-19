/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.database.readinghistory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.orderBy
//import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
//import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.android.database.readinghistory.ReadingHistoryEntities.ReadingHistory

/**
 * Dao for ReadingHistory table
 */
@Dao
interface ReadingHistoryDao {

    //region ReadingHistory
    @Query("SELECT * FROM ReadingHistory WHERE id = :id")
    suspend fun getReadingHistory(id: Int): ReadingHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateReadingHistory(readingHistory: ReadingHistory)

    @Query("DELETE FROM ReadingHistory WHERE id = :id")
    suspend fun deleteReadingHistory(id: Int)

    @Insert fun insert(entity: ReadingHistoryEntities.ReadingHistory): Long

    @Query("SELECT * from ReadingHistory ORDER BY id")
    fun allReadingHistory(): List<ReadingHistoryEntities.ReadingHistory>

    //endregion

}
