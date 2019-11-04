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

package net.bible.service.db.readingplan

import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import java.lang.Exception

/** @author Timmy Braun [tim.bze at gmail dot com] (Oct. 21, 2019)
 */
object ReadingPlanDatabaseDefinition {

    object ReadingPlanStatus : BaseColumns {
        const val TABLE_NAME = "readingplan_status"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_PLAN_CODE = "plan_code"
        const val COLUMN_PLAN_DAY = "plan_day"
        const val COLUMN_READING_STATUS = "reading_status"
    }
}

class ReadingPlanDatabaseOperations {
    companion object {
        val instance = ReadingPlanDatabaseOperations()
    }

    private val TAG = "ReadingPlanDbOps"
    private val readingPlanStatus = ReadingPlanDatabaseDefinition.ReadingPlanStatus
    private val SQL_CREATE_READING_PLAN_STATUS = readingPlanStatus.run {
        """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PLAN_CODE TEXT NOT NULL,
                $COLUMN_PLAN_DAY INTEGER NOT NULL,
                $COLUMN_READING_STATUS TEXT NOT NULL
            );
            CREATE INDEX code_day ON $TABLE_NAME($COLUMN_PLAN_CODE,$COLUMN_PLAN_DAY);
        """
    }

    fun onCreate(db: SQLiteDatabase) {
        Log.i(TAG, "Creating table ${readingPlanStatus.TABLE_NAME}")
        try {
            db.execSQL(SQL_CREATE_READING_PLAN_STATUS)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating table ${readingPlanStatus.TABLE_NAME}")
        }
    }
}
