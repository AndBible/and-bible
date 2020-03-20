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
/**
 *
 */
package net.bible.service.db.mynote

import android.provider.BaseColumns
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase


/**
 * MyNote database definitions
 *
 * @author John D. Lewis [balinjdl at gmail dot com]
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class MyNoteDatabaseDefinition
/**
 * Private constructor, callers except unit tests should obtain an instance through
 * { link #getInstance(android.content.Context)} instead.
 */
private constructor() {
    interface Table {
        companion object {
            const val MYNOTE = "mynote"
        }
    }

    interface Index {
        companion object {
            const val MYNOTE_KEY = "mynote_key"
        }
    }

    interface Join
    interface View
    interface Clause
    interface MyNoteColumn {
        companion object {
            const val _ID = BaseColumns._ID
            const val KEY = "key"
            const val VERSIFICATION = "versification"
            const val MYNOTE = "mynote"
            const val LAST_UPDATED_ON = "last_updated_on"
            const val CREATED_ON = "created_on"
        }
    }

    /** Called when no database exists in disk and the helper class needs
     * to create a new one.
     */
    fun onCreate(db: SupportSQLiteDatabase) {
        bootstrapDB(db)
    }

    private fun bootstrapDB(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Bootstrapping And Bible database (MyNotes)")
        db.execSQL("CREATE TABLE " + Table.MYNOTE + " (" +
            MyNoteColumn._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            MyNoteColumn.KEY + " TEXT NOT NULL, " +
            MyNoteColumn.VERSIFICATION + " TEXT," +
            MyNoteColumn.MYNOTE + " TEXT NOT NULL, " +
            MyNoteColumn.LAST_UPDATED_ON + " INTEGER," +
            MyNoteColumn.CREATED_ON + " INTEGER" +
            ");")
        // create an index on key
        db.execSQL("CREATE INDEX " + Index.MYNOTE_KEY + " ON " + Table.MYNOTE + "(" +
            MyNoteColumn.KEY +
            ");")
    }

    fun upgradeToVersion3(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Upgrading MyNote db to version 3")
        db.execSQL("ALTER TABLE " + Table.MYNOTE + " ADD COLUMN " + MyNoteColumn.VERSIFICATION + " TEXT;")
    }

    companion object {
        private const val TAG = "MyNoteDatabaseDef"
        private var sSingleton: MyNoteDatabaseDefinition? = null
        @JvmStatic
		@get:Synchronized
        val instance: MyNoteDatabaseDefinition
            get() {
                if (sSingleton == null) {
                    sSingleton = MyNoteDatabaseDefinition()
                }
                return sSingleton!!
            }
    }
}
