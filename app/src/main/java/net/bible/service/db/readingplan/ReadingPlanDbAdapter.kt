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
import android.util.Log
import net.bible.service.db.CommonDatabaseHelper
import java.lang.Exception
import kotlin.math.max

/** @author Timmy Braun [tim.bze at gmail dot com] (Oct. 22, 2019)
 */
class ReadingPlanDbAdapter {
    companion object {
        val instance = ReadingPlanDbAdapter()

        private const val TAG = "ReadingPlanDBAdapter"
    }

    private val db = CommonDatabaseHelper.getInstance().readableDatabase
    private val readingPlanDef = ReadingPlanDatabaseDefinition.ReadingPlan
    private val statusDef = ReadingPlanDatabaseDefinition.ReadingPlanStatus

    fun getReadingPlanStatus(planCode: String, dayNo: Int): String? {
        val selection = "${statusDef.COLUMN_PLAN_CODE}=? AND ${statusDef.COLUMN_PLAN_DAY}=?"
        val selectionArgs = arrayOf(planCode, dayNo.toString())
        val q = db.query(statusDef.TABLE_NAME,
            arrayOf(statusDef.COLUMN_READING_STATUS),
            selection,
            selectionArgs,
            null, null, null)
        if (q.moveToFirst()) return q.getString(0)
        return null
    }

    fun setReadingPlanStatus(planCode: String, dayNo: Int, status: String) {
        if (db.update(statusDef.TABLE_NAME,
                ContentValues().apply {
                    put(statusDef.COLUMN_READING_STATUS, status)
                },
                "${statusDef.COLUMN_PLAN_CODE}=? AND ${statusDef.COLUMN_PLAN_DAY}=?",
                arrayOf(planCode, dayNo.toString())
            ) < 1) {
            // if no row updated then insert new row

            if (db.insert(statusDef.TABLE_NAME,
                    null,
                    ContentValues().apply {
                        put(statusDef.COLUMN_PLAN_CODE, planCode)
                        put(statusDef.COLUMN_PLAN_DAY, dayNo)
                        put(statusDef.COLUMN_READING_STATUS, status)
                    }
                ) < 0) {
                Log.e(TAG, "Error inserting reading status into table. planCode=$planCode, " +
                    "dayNo=$dayNo, status=$status")
            }
        }
    }

    fun getReadingStartDate(planCode: String): Long? {
        val selection = "${readingPlanDef.COLUMN_PLAN_CODE}=?"
        val selectionArgs = arrayOf(planCode)
        val q = db.query(readingPlanDef.TABLE_NAME,
            arrayOf(readingPlanDef.COLUMN_PLAN_START_DATE),
            selection,
            selectionArgs,
            null, null, null)
        if (q.moveToFirst()) return q.getLong(0)
        return null
    }

    fun setReadingStartDate(planCode: String, startDate: Long) {
        val values = ContentValues()
        values.put(readingPlanDef.COLUMN_PLAN_START_DATE, startDate)
        val rows = db.update(readingPlanDef.TABLE_NAME,
            values,
            "${readingPlanDef.COLUMN_PLAN_CODE}=?",
            arrayOf(planCode))

        if (rows < 1) {
            values.put(readingPlanDef.COLUMN_PLAN_CODE, planCode)
            if (db.insert(readingPlanDef.TABLE_NAME,null, values) < 0) {
                Log.e(TAG, "Error occurred while trying to insert startDate $startDate into db for plan $planCode")
            }
        }
    }

    fun getReadingCurrentDay(planCode: String): Int {
        val selection = "${readingPlanDef.COLUMN_PLAN_CODE}=?"
        val selectionArgs = arrayOf(planCode)
        var currentDay = 0
        val q = db.query(readingPlanDef.TABLE_NAME,
            arrayOf(readingPlanDef.COLUMN_PLAN_CURRENT_DAY),
            selection,
            selectionArgs,
            null, null, null)
        if (q.moveToFirst()) currentDay = q.getInt(0)
        return max(1, currentDay)
    }

    fun setReadingCurrentDay(planCode: String, dayNo: Int) {
        val values = ContentValues()
        values.put(readingPlanDef.COLUMN_PLAN_CURRENT_DAY, dayNo)
        var rows: Int = 0
        try {
            rows = db.update(readingPlanDef.TABLE_NAME,
                values,
                "${readingPlanDef.COLUMN_PLAN_CODE}=?",
                arrayOf(planCode))
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to update db current day $dayNo for plan $planCode", e)
        }

        if (rows < 1) {
            values.put(readingPlanDef.COLUMN_PLAN_CODE, planCode)
            if (db.insert(readingPlanDef.TABLE_NAME,null, values) < 0) {
                Log.e(TAG, "Error trying to insert db current day $dayNo for plan $planCode")
            }
        }
    }

    fun resetPlan(planCode: String) {
        Log.i(TAG, "Now resetting plan $planCode in database. Removing start date, current day, and read statuses")
        // delete reading statuses
        db.delete(statusDef.TABLE_NAME,
            "${statusDef.COLUMN_PLAN_CODE}=?",
            arrayOf(planCode))

        // delete reading start date and current day
        db.delete(readingPlanDef.TABLE_NAME,
            "${readingPlanDef.COLUMN_PLAN_CODE}=?",
            arrayOf(planCode))
    }
}
