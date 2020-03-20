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
import android.util.Log
import androidx.sqlite.db.SupportSQLiteQueryBuilder.builder
import net.bible.service.db.DatabaseContainer
import net.bible.service.readingplan.ReadingPlanInfoDto
import java.lang.Exception
import kotlin.math.max

/** @author Timmy Braun [tim.bze at gmail dot com] (Oct. 22, 2019)
 */
class ReadingPlanDbAdapter {
    companion object {
        val instance = ReadingPlanDbAdapter()
        private const val TAG = "ReadingPlanDBAdapter"
    }

    private val db = DatabaseContainer.db.openHelper.readableDatabase

    private val readingPlanDef = ReadingPlanDatabaseDefinition.ReadingPlan
    private val statusDef = ReadingPlanDatabaseDefinition.ReadingPlanStatus

    fun getReadingPlanStatus(planCode: String, dayNo: Int): String? {
        val selection = "${statusDef.COLUMN_PLAN_CODE}=? AND ${statusDef.COLUMN_PLAN_DAY}=?"
        val selectionArgs = arrayOf(planCode, dayNo.toString())
        val q = db.query(builder(statusDef.TABLE_NAME).columns(arrayOf(statusDef.COLUMN_READING_STATUS))
            .selection(selection, selectionArgs).create())
        var returnValue: String? = null
        if (q.moveToFirst()) returnValue = q.getString(0)
        q.close()
        return returnValue
    }

    fun setReadingPlanStatus(planCode: String, dayNo: Int, status: String) {
        if (db.update(statusDef.TABLE_NAME, CONFLICT_FAIL,
                ContentValues().apply {
                    put(statusDef.COLUMN_READING_STATUS, status)
                },
                "${statusDef.COLUMN_PLAN_CODE}=? AND ${statusDef.COLUMN_PLAN_DAY}=?",
                arrayOf(planCode, dayNo.toString())
            ) < 1) {
            // if no row updated then insert new row

            if (db.insert(statusDef.TABLE_NAME,
                    CONFLICT_FAIL,
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
        val q = db.query(builder(readingPlanDef.TABLE_NAME)
            .columns(arrayOf(readingPlanDef.COLUMN_PLAN_START_DATE))
            .selection(selection, selectionArgs)
            .create()
        )
        var returnValue: Long? = null
        if (q.moveToFirst()) returnValue = q.getLong(0)
        q.close()
        return returnValue
    }

    fun setReadingStartDate(planCode: String, startDate: Long) {
        val values = ContentValues()
        values.put(readingPlanDef.COLUMN_PLAN_START_DATE, startDate)
        val rows = db.update(readingPlanDef.TABLE_NAME, CONFLICT_FAIL,
            values,
            "${readingPlanDef.COLUMN_PLAN_CODE}=?",
            arrayOf(planCode))

        if (rows < 1) {
            values.put(readingPlanDef.COLUMN_PLAN_CODE, planCode)
            if (db.insert(readingPlanDef.TABLE_NAME, CONFLICT_FAIL, values) < 0) {
                Log.e(TAG, "Error occurred while trying to insert startDate $startDate into db for plan $planCode")
            }
        }
    }

    fun getReadingCurrentDay(planCode: String): Int {
        val selection = "${readingPlanDef.COLUMN_PLAN_CODE}=?"
        val selectionArgs = arrayOf(planCode)
        val q = db.query(builder(readingPlanDef.TABLE_NAME).columns(arrayOf(readingPlanDef.COLUMN_PLAN_CURRENT_DAY))
            .selection(selection, selectionArgs).create())
        var returnValue = 0
        if (q.moveToFirst()) returnValue = q.getInt(0)
        q.close()
        return max(1, returnValue)
    }

    fun setReadingCurrentDay(planCode: String, dayNo: Int) {
        val values = ContentValues()
        values.put(readingPlanDef.COLUMN_PLAN_CURRENT_DAY, dayNo)
        var rows = 0
        try {
            rows = db.update(readingPlanDef.TABLE_NAME, CONFLICT_FAIL,
                values,
                "${readingPlanDef.COLUMN_PLAN_CODE}=?",
                arrayOf(planCode))
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to update db current day $dayNo for plan $planCode", e)
        }

        if (rows < 1) {
            values.put(readingPlanDef.COLUMN_PLAN_CODE, planCode)
            if (db.insert(readingPlanDef.TABLE_NAME, CONFLICT_FAIL, values) < 0) {
                Log.e(TAG, "Error trying to insert db current day $dayNo for plan $planCode")
            }
        }
    }

    /**
     * All reading statuses will be deleted that are before the [day] parameter given.
     * Date-based plan statuses are never deleted
     * @param day The current day, all day statuses before this day will be deleted
     */
    fun deleteOldStatuses(planInfo: ReadingPlanInfoDto, day: Int) {
        if (!planInfo.isDateBasedPlan) {
            db.delete(statusDef.TABLE_NAME,
                "${statusDef.COLUMN_PLAN_CODE}=? AND ${statusDef.COLUMN_PLAN_DAY}<?",
                arrayOf(planInfo.planCode, day.toString()))
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
