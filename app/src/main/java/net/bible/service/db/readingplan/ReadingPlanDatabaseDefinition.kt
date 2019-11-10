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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import net.bible.service.common.CommonUtils
import net.bible.service.readingplan.ReadingPlanDao
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

/** @author Timmy Braun [tim.bze at gmail dot com] (Oct. 21, 2019)
 */
object ReadingPlanDatabaseDefinition {

    /** Table to keep track of plan start date and current day progress
     */
    object ReadingPlan : BaseColumns {
        const val TABLE_NAME = "readingplan"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_PLAN_CODE = "plan_code"
        const val COLUMN_PLAN_START_DATE = "plan_start_date"
        const val COLUMN_PLAN_CURRENT_DAY = "plan_current_day"
    }

    /** Table to keep track of which chapters have been read
     */
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

    private val readingPlan = ReadingPlanDatabaseDefinition.ReadingPlan
    private val SQL_CREATE_READING_PLAN = readingPlan.run {
        """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PLAN_CODE TEXT NOT NULL,
                $COLUMN_PLAN_START_DATE INTEGER NOT NULL,
                $COLUMN_PLAN_CURRENT_DAY INTEGER NOT NULL DEFAULT 1
            );
        """
    }

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

        try {
            Log.i(TAG, "Creating table ${readingPlan.TABLE_NAME}")
            db.execSQL(SQL_CREATE_READING_PLAN)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating table ${readingPlan.TABLE_NAME}")
        }

        try {
            Log.i(TAG, "Creating table ${readingPlanStatus.TABLE_NAME}")
            db.execSQL(SQL_CREATE_READING_PLAN_STATUS)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating table ${readingPlanStatus.TABLE_NAME}")
        }
    }

    fun importPrefsToDatabase(db: SQLiteDatabase) {
        val READING_PLAN_DAY_EXT = "_day"
        val READING_PLAN_START_EXT = "_start"
        val readingPlanDao = ReadingPlanDao()

        val readingPlans: ArrayList<String> = ArrayList(readingPlanDao.internalPlanCodes)
        val userPlans = readingPlanDao.userPlanCodes()
        userPlans ?: readingPlans.addAll(userPlans!!.toTypedArray())

        val prefs = CommonUtils.sharedPreferences
        for (planCode in readingPlans) {
            val start = prefs.getLong(planCode + READING_PLAN_START_EXT, 0)
            val day = prefs.getInt(planCode + READING_PLAN_DAY_EXT, 0)
            val values = ContentValues().apply { put(readingPlan.COLUMN_PLAN_CODE, planCode) }
            if (start > 0L) values.put(readingPlan.COLUMN_PLAN_START_DATE, start)
            if (day > 0) values.put(readingPlan.COLUMN_PLAN_CURRENT_DAY, day)

            if (start > 0L || day > 0)
                if (db.insert(readingPlan.TABLE_NAME,null, values) < 0)
                    Log.e(TAG, "")

            
        }
    }
}
