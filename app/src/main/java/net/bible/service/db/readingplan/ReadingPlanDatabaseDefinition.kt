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

/**
 *
 */
package net.bible.service.db.readingplan

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import android.util.Log
import net.bible.service.common.CommonUtils

import net.bible.service.readingplan.ReadingPlanDao
import net.bible.service.readingplan.ReadingPlanInfoDto

/**
 * @author Timmy Braun [tim.bze at gmail dot com] (2/12/2019)
 * TODO: Figure out something that a plan like Prof Horner 10 chapter a day,
 * which is 10 groups of chapters, will continue to loop through the groups
 * even after the year is up
 */
object ReadingPlanDatabaseDefinition {
    private const val TAG = "ReadingPlanDatabaseDef"
    const val DB_TRUE_VALUE = "1"
    const val DB_FALSE_VALUE = "0"

    object ReadingPlanMeta : BaseColumns {
        // If COLUMN_CURRENT_DAY has CONSTANT_CURRENT_DAY_BY_DATE value,
        // then the reading plan will by default get today's date
        const val CONSTANT_CURRENT_DAY_BY_DATE = -1

        const val TABLE_NAME = "readingplan_meta"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_PLAN_FILE_NAME = "plan_file_name"
        const val COLUMN_PLAN_NAME = "plan_name"
        const val COLUMN_DESCRIPTION = "description"
        const val COLUMN_DATE_START = "date_start"
        const val COLUMN_DAYS_IN_PLAN = "days_in_plan"
        const val COLUMN_CURRENT_DAY = "current_day"
        const val COLUMN_VERSIFICATION_NAME = "versification"
        const val COLUMN_LAST_USED_PLAN = "last_used"
    }

    object ReadingPlanDays : BaseColumns {
        const val TABLE_NAME = "readingplan_days"
        const val COLUMN_ID = BaseColumns._ID
        const val COLUMN_READING_PLAN_META_ID = "readingplan_meta_id"
        const val COLUMN_DAY_NUMBER = "day_number"
        const val COLUMN_READING_DATE = "reading_date"
        const val COLUMN_DAY_CHAPTERS = "day_chapters"
        const val COLUMN_READ_STATUS = "read_status"
    }

    class Operations {
        companion object {
            private const val TAG = "ReadingPlanDatabaseOps"
            private const val SQL_CREATE_ENTRIES_META =
                "CREATE TABLE ${ReadingPlanMeta.TABLE_NAME} (" +
                "${ReadingPlanMeta.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                "${ReadingPlanMeta.COLUMN_PLAN_FILE_NAME} TEXT NOT NULL," +
                "${ReadingPlanMeta.COLUMN_PLAN_NAME} TEXT NOT NULL," +
                "${ReadingPlanMeta.COLUMN_DESCRIPTION} TEXT NOT NULL," +
                "${ReadingPlanMeta.COLUMN_DATE_START} INTEGER," +
                "${ReadingPlanMeta.COLUMN_DAYS_IN_PLAN} INTEGER," +
                "${ReadingPlanMeta.COLUMN_CURRENT_DAY} INTEGER NOT NULL DEFAULT 0," +
                "${ReadingPlanMeta.COLUMN_VERSIFICATION_NAME} TEXT NOT NULL," +
                "${ReadingPlanMeta.COLUMN_LAST_USED_PLAN} INTEGER NOT NULL DEFAULT 0" +
                "); "

            private const val SQL_CREATE_ENTRIES_DAYS =
                "CREATE TABLE ${ReadingPlanDays.TABLE_NAME} (" +
                "${ReadingPlanDays.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT," +
                "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID} INTEGER NOT NULL," +
                "${ReadingPlanDays.COLUMN_DAY_NUMBER} INTEGER NOT NULL," +
                "${ReadingPlanDays.COLUMN_READING_DATE} TEXT," +
                "${ReadingPlanDays.COLUMN_DAY_CHAPTERS} TEXT NOT NULL," +
                "${ReadingPlanDays.COLUMN_READ_STATUS} TEXT" +
                ");"

            private const val SQL_CREATE_DAYS_INDEX = "CREATE INDEX days_date_name ON ${ReadingPlanDays.TABLE_NAME}(${ReadingPlanDays.COLUMN_READING_DATE});"


            /** Called from CommonDatabaseHelper when ReadingPlan tables
             * don't exists and the helper class needs to create them.
             */
            @JvmStatic
            fun onCreate(db: SQLiteDatabase) {
                Log.i(TAG, "Creating AndBible tables (ReadingPlan)")
                db.execSQL(SQL_CREATE_ENTRIES_META)
                db.execSQL(SQL_CREATE_ENTRIES_DAYS)

                importReadingPlansToDatabase(db, true)

                db.execSQL(SQL_CREATE_DAYS_INDEX)
            }

            /** This is a function to run only 1 time when updating Database
             * to have reading plans in DB instead of text files
             */
            fun importReadingPlansToDatabase(db: SQLiteDatabase, firstImport: Boolean = false) {

                // dbAdapter can ONLY be used in this function if it's not [firstImport]
                lateinit var dbAdapter: ReadingPlanDBAdapter
                lateinit var readingPlanCodes: List<ReadingPlanInfoDto>

                if (!firstImport) {
                    // Get SD reading plans list
                    dbAdapter = ReadingPlanDBAdapter()
                    if (ReadingPlanDao().sdReadingPlanList == null) {
                        Log.d(TAG, "There are no reading plans on SD card.")
                        return
                    }
                    readingPlanCodes = ReadingPlanDao().sdReadingPlanList!!
                } else {
                    // Get all Reading Plans list
                    readingPlanCodes = ReadingPlanDao().readingPlanList
                }

                // Get SharedPrefs about current plan
                val prefs = CommonUtils.getSharedPreferences()
                val currentPlanCode = prefs.getString("reading_plan", "") as String

                for (plan in readingPlanCodes) {
                    if (firstImport || (!firstImport && !dbAdapter.getMetaIsPlanAlreadyImported(plan.code))) {
                        var thisPlanDay: Int = prefs.getInt(plan.code + "_day", 0)

                        val metaValues = ContentValues().apply {
                            put(ReadingPlanMeta.COLUMN_PLAN_FILE_NAME, plan.code)
                            put(ReadingPlanMeta.COLUMN_PLAN_NAME, plan.planName)
                            put(ReadingPlanMeta.COLUMN_DESCRIPTION, plan.description)
                            put(ReadingPlanMeta.COLUMN_DAYS_IN_PLAN, plan.numberOfPlanDays)
                            put(ReadingPlanMeta.COLUMN_VERSIFICATION_NAME, plan.versification?.name)
                            put(ReadingPlanMeta.COLUMN_CURRENT_DAY, thisPlanDay)
                            put(ReadingPlanMeta.COLUMN_DATE_START, plan.startdate?.time)
                            if (firstImport && currentPlanCode == plan.code) {
                                put(ReadingPlanMeta.COLUMN_LAST_USED_PLAN, DB_TRUE_VALUE)
                            }
                        }

                        val dbReadingPlanMetaID = db.insert(ReadingPlanMeta.TABLE_NAME, null, metaValues)

                        val planReadingList = ReadingPlanDao()
                            .getReadingList(plan.code)


                        var isDateBasedPlan: Boolean? = null

                        // Set to 1 just in case chapters have been read on day 1, that ReadStatus will be correct
                        if (thisPlanDay == 0) {
                            thisPlanDay = 1
                        }

                        for (dailyReading in planReadingList) {
                            isDateBasedPlan = dailyReading.dateBasedReadingDate != null

                            // Update meta table to specify plan as Date-Based
                            if (isDateBasedPlan) {
                                val values = ContentValues().apply {
                                    put(ReadingPlanMeta.COLUMN_CURRENT_DAY, ReadingPlanMeta.CONSTANT_CURRENT_DAY_BY_DATE)
                                }
                                val whereClause = "${ReadingPlanMeta.COLUMN_ID} = ?"
                                val whereArgs = arrayOf(dbReadingPlanMetaID.toString())
                                db.update(ReadingPlanMeta.TABLE_NAME, values, whereClause, whereArgs)
                            }

                            // Get Reading Plan date start, current day, and chapters read from SharedPrefs and update DB
                            // Set all days up to current day as all chapters read
                            val chaptersReadArray: Array<ReadingPlanOneDayDB.ChapterRead?> = arrayOfNulls(dailyReading.numReadings)
                            var readStatusString: String? = null
                            if (dailyReading.day == thisPlanDay) {
                                val prefsKey: String = plan.code + "_" + dailyReading.day
                                val prefsStatusString = prefs.getString(prefsKey, null)

                                if (prefsStatusString != null) {
                                    for (i in 0 until dailyReading.numReadings) {
                                        if (i < prefsStatusString.length && prefsStatusString[i] == '1') {
                                            chaptersReadArray[i] = ReadingPlanOneDayDB.ChapterRead(i + 1, true)
                                        } else {
                                            chaptersReadArray[i] = ReadingPlanOneDayDB.ChapterRead(i + 1, false)
                                        }
                                    }
                                    readStatusString = ReadingPlanOneDayDB.ReadingStatus(
                                        dbReadingPlanMetaID.toInt(),
                                        dailyReading.numReadings,
                                        chaptersReadArray
                                    ).toJsonString()
                                }
                            } else if (dailyReading.day < thisPlanDay) {
                                for (i in 0 until dailyReading.numReadings) {
                                    chaptersReadArray[i] = ReadingPlanOneDayDB.ChapterRead(i + 1, true)
                                }
                                readStatusString = ReadingPlanOneDayDB.ReadingStatus(
                                    dbReadingPlanMetaID.toInt(),
                                    dailyReading.numReadings,
                                    chaptersReadArray
                                ).toJsonString()
                            }


                            // Enter day info to DB
                            val dayValues = ContentValues().apply {
                                put(ReadingPlanDays.COLUMN_READING_PLAN_META_ID, dbReadingPlanMetaID)
                                put(ReadingPlanDays.COLUMN_DAY_NUMBER, dailyReading.day)
                                put(ReadingPlanDays.COLUMN_DAY_CHAPTERS, dailyReading.getReadingsString())
                                put(ReadingPlanDays.COLUMN_READ_STATUS, readStatusString)
                                if (isDateBasedPlan) {
                                    put(ReadingPlanDays.COLUMN_READING_DATE, dailyReading.dateBasedReadingDateStringFromFile) // only for those plans that are by-date
                                }
                            }
                            db.insert(ReadingPlanDays.TABLE_NAME, null, dayValues)
                        } // loop reading plan day


                    } // loop reading plan


                }


            }

        }

    }
}
