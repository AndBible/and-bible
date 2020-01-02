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
package net.bible.service.db.bookmark

import android.provider.BaseColumns
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase


/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkDatabaseDefinition {
    interface Table {
        companion object {
            const val BOOKMARK = "bookmark"
            // many-to-many cross-reference/join table between bookmark and label
            const val BOOKMARK_LABEL = "bookmark_label"
            const val LABEL = "label"
        }
    }

    interface Join {
        companion object {
            // http://stackoverflow.com/questions/973790/sql-multiple-join-on-many-to-many-tables-comma-separation
            const val BOOKMARK_JOIN_LABEL = ("bookmark "
                + "JOIN bookmark_label ON (groups.package_id = packages._id)")
        }
    }

    interface View
    interface Clause
    interface BookmarkColumn {
        companion object {
            const val _ID = BaseColumns._ID
            const val KEY = "key"
            const val VERSIFICATION = "versification"
            const val CREATED_ON = "created_on"
            const val PLAYBACK_SETTINGS = "speak_settings"
        }
    }

    interface BookmarkLabelColumn {
        companion object {
            const val BOOKMARK_ID = "bookmark_id"
            const val LABEL_ID = "label_id"
        }
    }

    interface LabelColumn {
        companion object {
            const val _ID = BaseColumns._ID
            const val NAME = "name"
            const val BOOKMARK_STYLE = "bookmark_style"
        }
    }

    /** Called when no database exists in disk and the helper class needs
     * to create a new one.
     */
    fun onCreate(db: SupportSQLiteDatabase) {
        bootstrapDB(db)
    }

    fun upgradeToVersion5(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Upgrading Bookmark db to version 5")
        db.execSQL("ALTER TABLE " + Table.BOOKMARK + " ADD COLUMN " + BookmarkColumn.PLAYBACK_SETTINGS + " TEXT DEFAULT null;")
    }

    fun upgradeToVersion4(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Upgrading Bookmark db to version 4")
        db.execSQL("ALTER TABLE " + Table.LABEL + " ADD COLUMN " + LabelColumn.BOOKMARK_STYLE + " TEXT;")
    }

    fun upgradeToVersion3(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Upgrading Bookmark db to version 3")
        db.execSQL("ALTER TABLE " + Table.BOOKMARK + " ADD COLUMN " + BookmarkColumn.VERSIFICATION + " TEXT;")
        db.execSQL("ALTER TABLE " + Table.BOOKMARK + " ADD COLUMN " + BookmarkColumn.CREATED_ON + " INTEGER DEFAULT 0;")
    }

    private fun bootstrapDB(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Bootstrapping And Bible database (Bookmarks)")
        db.execSQL("CREATE TABLE " + Table.BOOKMARK + " (" +
            BookmarkColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            BookmarkColumn.KEY + " TEXT NOT NULL," +
            BookmarkColumn.VERSIFICATION + " TEXT," +
            BookmarkColumn.CREATED_ON + " INTEGER DEFAULT 0," +
            BookmarkColumn.PLAYBACK_SETTINGS + " TEXT DEFAULT NULL" +
            ");")
        // Intersection table
        db.execSQL("CREATE TABLE " + Table.BOOKMARK_LABEL + " (" +
            BookmarkLabelColumn.BOOKMARK_ID + " INTEGER NOT NULL," +
            BookmarkLabelColumn.LABEL_ID + " INTEGER NOT NULL" +
            ");")
        db.execSQL("CREATE TABLE " + Table.LABEL + " (" +
            LabelColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            LabelColumn.NAME + " TEXT NOT NULL," +
            LabelColumn.BOOKMARK_STYLE + " TEXT" +
            ");")
        // SQLite version in android 1.6 is 3.5.9 which doesn't support foreign keys so use a trigger
		// Trigger to remove join table rows when either side of the join is deleted
        db.execSQL("CREATE TRIGGER bookmark_cleanup DELETE ON " + Table.BOOKMARK + " " +
            "BEGIN " +
            "DELETE FROM " + Table.BOOKMARK_LABEL + " WHERE " + BookmarkLabelColumn.BOOKMARK_ID + " = old._id;" +
            "END")
        db.execSQL("CREATE TRIGGER label_cleanup DELETE ON " + Table.LABEL + " " +
            "BEGIN " +
            "DELETE FROM " + Table.BOOKMARK_LABEL + " WHERE " + BookmarkLabelColumn.LABEL_ID + " = old._id;" +
            "END")
    }

    companion object {
        private const val TAG = "BookmarkDatabaseDefn"
        private var sSingleton: BookmarkDatabaseDefinition? = null
        @JvmStatic
		@get:Synchronized
        val instance: BookmarkDatabaseDefinition
            get() {
                if (sSingleton == null) {
                    sSingleton = BookmarkDatabaseDefinition()
                }
                return sSingleton!!
            }
    }
}
