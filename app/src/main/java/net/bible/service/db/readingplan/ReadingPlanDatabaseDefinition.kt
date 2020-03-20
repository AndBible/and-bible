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

package net.bible.service.db.readingplan

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.provider.BaseColumns
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import net.bible.android.control.readingplan.ReadingStatus
import net.bible.service.common.CommonUtils
import net.bible.service.readingplan.ReadingPlanDao
import java.lang.Exception
import kotlin.collections.ArrayList
import kotlin.math.max

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

    fun onCreate(db: SupportSQLiteDatabase) {

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

    fun migratePrefsToDatabase(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Now importing reading plan preferences from shared preferences to database")
        try {
            val DAY_EXT = "_day"
            val START_EXT = "_start"
            val readingPlanDao = ReadingPlanDao()

            val readingPlans: ArrayList<String> = ArrayList(readingPlanDao.internalPlanCodes)
            val userPlans = readingPlanDao.userPlanCodes()
            if (userPlans != null) readingPlans.addAll(userPlans.toTypedArray())

            val prefs = CommonUtils.sharedPreferences
            for (planCode in readingPlans) {
                Log.i(TAG, "Importing status for plan $planCode")
                val start = prefs.getLong(planCode + START_EXT, 0)
                var day = prefs.getInt(planCode + DAY_EXT, 0)
                val values = ContentValues().apply { put(readingPlan.COLUMN_PLAN_CODE, planCode) }
                if (start > 0L) {
                    values.put(readingPlan.COLUMN_PLAN_START_DATE, start)
                    day = max(day, 1)
                }
                if (day > 0) values.put(readingPlan.COLUMN_PLAN_CURRENT_DAY, day)

                if ((start > 0L || day > 0) && db.insert(readingPlan.TABLE_NAME, CONFLICT_FAIL, values) < 0)
                    Log.e(TAG, "Error inserting start date and current day to db for plan $planCode")

                val prefKey = "${planCode}_$day"
                if (prefs.contains(prefKey)) {
                    val prefDayStatus = prefs.getString(prefKey, "")
                    if (!prefDayStatus.isNullOrEmpty()) {
                        enterStatusToDb(prefDayStatus, planCode, day, db)
                    }
                }

                prefs.edit()
                    .remove(planCode + START_EXT)
                    .remove(planCode + DAY_EXT)
                    .remove(prefKey)
                    .apply()

                // find other days that have reading status in shared preferences
                for ((key, value) in prefs.all) {
                    if (key.contains((planCode + "_[0-9]{1,3}$").toRegex()) && value.toString().contains("^[0-1]*$".toRegex())) {
                        val day = "(?<=_)[0-9]{1,3}$".toRegex().find(key)?.value?.toIntOrNull()
                        day ?: continue
                        enterStatusToDb(value.toString(), planCode, day, db)

                        prefs.edit()
                            .remove(key)
                            .apply()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating readingplans from preferences to database!")
        }
    }

    private fun enterStatusToDb(prefDayStatus: String, planCode: String, day: Int, db: SupportSQLiteDatabase) {
        val status = ReadingStatus(planCode, day, prefDayStatus.length)
        for (i in prefDayStatus.indices) {
            val isRead = prefDayStatus[i].toString().toInt().toBoolean()
            status.setStatus(i+1, isRead,false
            )
        }
        val statusValues = ContentValues().apply {
            put(readingPlanStatus.COLUMN_READING_STATUS, status.toString())
            put(readingPlanStatus.COLUMN_PLAN_DAY, day)
            put(readingPlanStatus.COLUMN_PLAN_CODE, planCode)
        }
        if (db.insert(readingPlanStatus.TABLE_NAME, CONFLICT_FAIL, statusValues) < 0)
            Log.e(TAG, "Error inserting reading status to db for plan $planCode day #$day")
    }

    private fun Int.toBoolean() = this > 0
}
