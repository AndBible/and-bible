/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
package net.bible.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

import net.bible.android.database.workspaces.Bookmark
import net.bible.android.database.workspaces.BookmarkToLabel
import net.bible.android.database.workspaces.Label
import net.bible.android.database.workspaces.MyNote
import net.bible.android.database.workspaces.ReadingPlan
import net.bible.android.database.workspaces.ReadingPlanStatus
import net.bible.android.database.workspaces.WorkspaceDao
import net.bible.android.database.workspaces.WorkspaceEntities
import java.util.*


class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long): Date = Date(value)

    @TypeConverter
    fun dateToTimestamp(date: Date): Long = date.time
}

@Database(
    entities = [
        Bookmark::class,
        Label::class,
        BookmarkToLabel::class,
        MyNote::class,
        ReadingPlan::class,
        ReadingPlanStatus::class,
        WorkspaceEntities.Workspace::class,
        WorkspaceEntities.Window::class,
        WorkspaceEntities.HistoryItem::class,
        WorkspaceEntities.PageManager::class
    ],
    version = 8
)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    fun sync() { // Sync all data so far into database file
        val cur = openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL)")
        cur.moveToFirst()
        cur.close()
    }
}
