/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

class ReadingHistoryEntities {

    /** Stores information for plan, like start date and current day user is on.
     * Plans that exist are determined by text files. Row will only exist here for plan
     * that has already been started */
    @Entity(tableName = "ReadingHistory",
        indices = [Index(name = "index_ReadingHistory_createdAt",value=["createdAt"], unique = false)])
    // TODO: Add other indices
    data class ReadingHistory(
        @ColumnInfo(name = "createdAt") val createdAt: Int,
        @ColumnInfo(name = "bible_document") var bibleDocument: String,
        @ColumnInfo(name = "docFormat", defaultValue = "Normal") var docFormat: String = "Normal",
        @ColumnInfo(name = "bookInitials") var bookInitials: String,
        @ColumnInfo(name = "chapterNo") val chapterNo: Int,
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name="id") val id: Int? = null
    )
}
