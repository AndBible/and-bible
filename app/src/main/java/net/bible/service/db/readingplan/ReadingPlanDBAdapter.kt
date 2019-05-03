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

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.service.common.CommonUtils
import net.bible.service.db.CommonDatabaseHelper
import net.bible.service.db.readingplan.ReadingPlanDatabaseDefinition.ReadingPlanDays
import net.bible.service.db.readingplan.ReadingPlanDatabaseDefinition.ReadingPlan
import net.bible.service.readingplan.PassageReader
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.SystemNRSVA
import org.crosswire.jsword.versification.system.Versifications
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.collections.ArrayList
import kotlin.reflect.KClass

/**
 * @author Timmy Braun [tim.bze at gmail dot com] (2/14/2019)
 */
class ReadingPlanDBAdapter {
    companion object {
        private const val TAG = "ReadingPlanDBAdapter"
        const val CURRENT_PLAN_INDEX_PREF = "currentReadingPlanIndex"
        private val app = BibleApplication.application

        private val writableDatabase = CommonDatabaseHelper.getInstance().readableDatabase
        private val preferences: SharedPreferences get() = CommonUtils.sharedPreferences

        @SuppressLint("SimpleDateFormat")
        private val dateBasedFormatMonthDay = SimpleDateFormat("MMM-d")
        @SuppressLint("SimpleDateFormat")
        private val dateBasedFormatWithYear = SimpleDateFormat("MMM-d/yyyy")
    }

    var currentActiveReadingPlanID: Int
        get() = preferences.getInt(CURRENT_PLAN_INDEX_PREF,0)
        set(newValue) = preferences.edit().putInt(CURRENT_PLAN_INDEX_PREF, newValue).apply()

    val currentPlanShortCode: String
        get() = StringUtils.left(getPlanFileNameFromId(currentActiveReadingPlanID),8)
    val currentDayAndNumberDescription: String
        get() = app.getString(
                R.string.rdg_plan_day,
                getCurrentDayNumber(currentActiveReadingPlanID).toString()
            )

    fun getCurrentDayNumber(readingPlanID: Int): Int {
        return if (getIsDateBasedPlan(readingPlanID)) {
            ReadingPlanDays.run {
                queryDatabaseValue(
                    Int::class,
                    queryColumn = COLUMN_DAY_NUMBER,
                    queryTable = TABLE_NAME,
                    selection = "$COLUMN_READING_PLAN_ID=? AND $COLUMN_READING_DATE=?",
                    selectionArgs = arrayOf(readingPlanID.toString(), dateFormatterPlanDateToString(getTodayDate))
                ) ?: 0
            }
        } else {
            Math.max(queryDatabaseValue(Int::class, readingPlanID, ReadingPlan.COLUMN_CURRENT_DAY) ?: 1, 1)
        }
    }

    fun setCurrentDayNumber(readingPlanID: Int, dayNumber: Int) {
        if (!getIsDateBasedPlan(readingPlanID)) {
            writableDatabase.update(
                ReadingPlan.TABLE_NAME,
                ContentValues().apply { put(ReadingPlan.COLUMN_CURRENT_DAY, dayNumber) },
                "${ReadingPlan.COLUMN_ID}=?",
                arrayOf(readingPlanID.toString()))
        }
    }

    fun getDueDayToBeRead(readingPlanID: Int): Long {
        val today = CommonUtils.truncatedDate
        val startDate = getPlanStartDate(readingPlanID)
        startDate ?: return -1
        // on final day, after done the startDate will be null

        // Rounding is necessary (due to DST I think) because
        // when the clocks went forward the difference became 88.95833 but should have been 89
        val diffInDays = (today.time - startDate.time) / (1000.0 * 60.0 * 60.0 * 24.0)
        val diffInWholeDays = Math.round(diffInDays)
        Log.d(TAG, "Days diff between today and start:$diffInWholeDays")

        // if diff is zero then we are on day 1 so add 1
        return diffInWholeDays + 1
    }

    fun incrementCurrentPlanDay(): Int {
        val readingPlanID: Int = currentActiveReadingPlanID
        val currentDay: Int = getCurrentDayNumber(readingPlanID)

        val incrementDay =
            ReadingPlanDays.run {
                queryDatabaseValue(
                    Int::class,
                    queryColumn = COLUMN_DAY_NUMBER,
                    queryTable = TABLE_NAME,
                    selection = "$COLUMN_READING_PLAN_ID=? AND $COLUMN_DAY_NUMBER>?",
                    selectionArgs = arrayOf(readingPlanID.toString(), currentDay.toString()),
                    orderBy = COLUMN_DAY_NUMBER
                ) ?: 0
            }

        if (currentDay == 1) {
            if (getPlanStartDate(readingPlanID) == null) {
                setPlanStartDate(readingPlanID)
            }
        }

        if (incrementDay > 0) {
            setCurrentDayNumber(currentActiveReadingPlanID, incrementDay)
        }

        return incrementDay
    }

    fun setAllDaysReadUpTo(dayNumber: Int) {
        val readingPlanID: Int = currentActiveReadingPlanID
        val numberOfReadings: Int = getDayNumberOfReadings(readingPlanID,dayNumber)

        val values = ContentValues().apply {
            put(ReadingPlanDays.COLUMN_READ_STATUS,
                ReadingPlanOneDayDB.ReadingStatus(
                    readingPlanID,
                    numberOfReadings,
                    getDayReadingChaptersArrayAsAllRead(numberOfReadings)
                ).toJsonString()
            )
        }
        val whereClause = "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}<=?"
        val whereArgs = arrayOf(readingPlanID.toString(), dayNumber.toString())

        writableDatabase.update(ReadingPlanDays.TABLE_NAME, values, whereClause, whereArgs)
    }

    fun getIsDateBasedPlan(readingPlanID: Int): Boolean {
        return queryDatabaseValue(Int::class, readingPlanID, ReadingPlan.COLUMN_CURRENT_DAY) ==
            ReadingPlan.CONSTANT_CURRENT_DAY_BY_DATE
    }

    fun getIsCustomUserAddedPlan(fileName: String): Boolean {
        return getPlanDetailLink(null, fileName) == null
    }

    fun getIsPlanAlreadyImported(fileName: String): Boolean {
        return queryDatabaseValue(
            Int::class,
            queryColumn = ReadingPlan.COLUMN_ID,
            selection = "${ReadingPlan.COLUMN_PLAN_FILE_NAME}=?",
            selectionArgs = arrayOf(fileName)
        ) != null
    }

    /**
     * Get's Reading Plan description from resources strings. If not available there, it will try to get description
     * from DB with second of this function with double constructor, here: [getPlanDescription]
     */
    fun getPlanDescription(readingPlanID: Int): String {
        return getPlanDetailLink(readingPlanID)?.planDescription ?:
            getPlanDescription(readingPlanID, true)
    }

    /**
     * Get's Reading Plan description from database. AndBible distributed reading plans
     * should always get description from resources strings, which this first function with one
     * constructor param does, here: [getPlanDescription]
     */
    private fun getPlanDescription(readingPlanID: Int, fromDB: Boolean): String {
        return queryDatabaseValue(String::class, readingPlanID, ReadingPlan.COLUMN_DESCRIPTION) ?: ""
    }

    fun getPlanFileNameFromId(readingPlanID: Int): String {
        return queryDatabaseValue(String::class, readingPlanID, ReadingPlan.COLUMN_PLAN_FILE_NAME) ?: ""
    }

    fun getPlanList(): List<ReadingPlanInformationDB> {
        val readingPlanList = ArrayList<ReadingPlanInformationDB>()

        val q = writableDatabase.query(
            ReadingPlan.TABLE_NAME, arrayOf(ReadingPlan.COLUMN_ID),
            null,null,null,null,null
        )
        while (q.moveToNext()) {
            readingPlanList.add(ReadingPlanInformationDB(q.getInt(0)))
        }
        q.close()

        Log.d(TAG, "${readingPlanList.count()} plans returned in list.")
        return readingPlanList
    }

    fun getPlanDaysList(readingPlanID: Int): List<ReadingPlanOneDayDB> {
        val readingPlanDaysList = ArrayList<ReadingPlanOneDayDB>()

        val planInfo = ReadingPlanInformationDB(readingPlanID)
        val q = writableDatabase.query(
            ReadingPlanDays.TABLE_NAME,
            arrayOf(
                ReadingPlanDays.COLUMN_DAY_NUMBER,
                ReadingPlanDays.COLUMN_DAY_CHAPTERS,
                ReadingPlanDays.COLUMN_READING_DATE,
                ReadingPlanDays.COLUMN_READ_STATUS
            ),
            "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=?",
            arrayOf(readingPlanID.toString()),
            null,
            null,
            ReadingPlanDays.COLUMN_DAY_NUMBER
        )
        while (q.moveToNext()) {
            readingPlanDaysList.add(ReadingPlanOneDayDB(
                planInfo,
                null,
                readingPlanID,
                dayNumber = q.getInt(0),
                readingChaptersString = q.getString(1),
                readingDateForDateBasedPlan = q.getString(2),
                readingStatusJSON = q.getString(3)
            ))
        }
        q.close()

        Log.d(TAG, "${readingPlanDaysList.count()} days as list returned from plan ${planInfo.planName}.")
        return readingPlanDaysList
    }

    fun getPlanName(readingPlanID: Int): String {
        return getPlanDetailLink(readingPlanID)?.planName ?: getPlanName(readingPlanID, true)
    }

    private fun getPlanName(readingPlanID: Int, fromDB: Boolean): String {
        return when (fromDB) {
            true -> queryDatabaseValue(String::class, readingPlanID, ReadingPlan.COLUMN_PLAN_NAME) ?: ""
            else -> ""
        }
    }

    fun getPlanStartDate(readingPlanID: Int): Date? {
        val startDateInt: Long? = queryDatabaseValue(Long::class, readingPlanID, ReadingPlan.COLUMN_DATE_START)
        startDateInt ?: return null
        return Date(startDateInt)
    }

    /**
     * @param startDate If parameter is not passed, will set start date as today.
     */
    fun setPlanStartDate(readingPlanID: Int, startDate: Date? = null) {
        val updateDate: Date = startDate ?: getTodayDate
        val values = ContentValues().apply {
            put(ReadingPlan.COLUMN_DATE_START, updateDate.time)
        }
        val whereClause = "${ReadingPlan.COLUMN_ID}=?"
        val whereArgs = arrayOf(readingPlanID.toString())

        val rowsAffected = writableDatabase.update(ReadingPlan.TABLE_NAME, values, whereClause, whereArgs)
        Log.d(TAG, """Set start date. $rowsAffected DB rows updated -- R.Plan.Id=$readingPlanID
            -- whereClause=$whereClause -- whereArgs=$whereArgs""")
    }

    val getTodayDate: Date get() = Calendar.getInstance().time

    fun getPlanTotalDays(readingPlanID: Int): Int {
        return queryDatabaseValue(Int::class, readingPlanID,ReadingPlan.COLUMN_DAYS_IN_PLAN) ?: 0
    }

    fun getPlanVersificationName(readingPlanID: Int): String? {
        return queryDatabaseValue(String::class, readingPlanID, ReadingPlan.COLUMN_VERSIFICATION_NAME)
    }

    fun getDayDateString(readingPlanID: Int, dayNumber: Int): String? {
        return queryDatabaseValue(
            String::class,
            queryColumn = ReadingPlanDays.COLUMN_READING_DATE,
            queryTable = ReadingPlanDays.TABLE_NAME,
            selection = "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}=?",
            selectionArgs = arrayOf(readingPlanID.toString(), dayNumber.toString())
        )
    }

    private fun getDayNumberOfReadings(readingPlanID: Int, dayNumber: Int): Int {
        val readingsString: String? = getDayReadingChaptersString(readingPlanID, dayNumber)
        readingsString ?: return 0

        return readingsString.split(",").count()
    }

    fun getDayReadingChaptersString(readingPlanID: Int, dayNumber: Int): String? {
        return queryDatabaseValue(
            String::class,
            queryColumn = ReadingPlanDays.COLUMN_DAY_CHAPTERS,
            queryTable = ReadingPlanDays.TABLE_NAME,
            selection = "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}=?",
            selectionArgs = arrayOf(readingPlanID.toString(), dayNumber.toString())
        )
    }

    private fun getDayReadingChaptersArrayAsAllRead(numberOfReadings: Int): List<ReadingPlanOneDayDB.ChapterRead?> {
        val chapterArray = ArrayList<ReadingPlanOneDayDB.ChapterRead?>()

        for (i in 0 until numberOfReadings) {
            chapterArray.add(ReadingPlanOneDayDB.ChapterRead(i + 1,true))
        }
        return chapterArray
    }

    fun getDayReadingStatus(readingPlanID: Int, dayNumber: Int): String {
        return queryDatabaseValue(
            String::class,
            queryColumn = ReadingPlanDays.COLUMN_READ_STATUS,
            queryTable = ReadingPlanDays.TABLE_NAME,
            selection = "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}=?",
            selectionArgs = arrayOf(readingPlanID.toString(), dayNumber.toString())
        ) ?: ""
    }

    fun setDayReadingStatus(readingPlanID: Int, dayNumber: Int, readingStatusJson: String) {

        val values = ContentValues().apply {
            put(ReadingPlanDays.COLUMN_READ_STATUS, readingStatusJson)
        }

        val whereClause = "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}=?"
        val whereArgs = arrayOf(readingPlanID.toString(),dayNumber.toString())

        writableDatabase.update(ReadingPlanDays.TABLE_NAME, values, whereClause, whereArgs)
    }

    fun resetPlan(readingPlanID: Int) {

        // Clear reading status on all days
        val values = ContentValues().apply {
            putNull(ReadingPlanDays.COLUMN_READ_STATUS)
        }
        val whereClause = "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=?"
        val whereArgs = arrayOf(readingPlanID.toString())

        writableDatabase.update(ReadingPlanDays.TABLE_NAME, values, whereClause, whereArgs)
        Log.d(TAG, "Cleared reading statuses on all days on reset")

        // Update meta info, reset start date and current day
        val metaValues = ContentValues().apply {
            if (!getIsDateBasedPlan(readingPlanID)) {
                put(ReadingPlan.COLUMN_CURRENT_DAY, 0)
            }
            put(ReadingPlan.COLUMN_DATE_START, getTodayDate.time)
        }
        val metaWhereClause = "${ReadingPlan.COLUMN_ID}=?"
        writableDatabase.update(ReadingPlan.TABLE_NAME, metaValues, metaWhereClause, whereArgs)
        Log.d(TAG, "Updated start date and current day on reset")
    }

    fun switchToReadingPlan(readingPlanInfo: ReadingPlanInformationDB) {
        Log.d(TAG, """Switching reading plan. R.Plan.Id=${readingPlanInfo.readingPlanID} --
            startDate=${readingPlanInfo.startDate}""")
        currentActiveReadingPlanID = readingPlanInfo.readingPlanID

        // Date based plan has no use for start date, except just for the record. Other plan start date is set
        // in function incrementCurrentPlanDay()
        if (getIsDateBasedPlan(readingPlanInfo.readingPlanID) && getPlanStartDate(readingPlanInfo.readingPlanID) == null) {
                setPlanStartDate(readingPlanInfo.readingPlanID)
        }
    }

    fun deletePlan(readingPlanID: Int): Boolean {
        // Delete days related to plan
        writableDatabase.delete(
            ReadingPlanDays.TABLE_NAME,
            "${ReadingPlanDays.COLUMN_READING_PLAN_ID}=?",
            arrayOf(readingPlanID.toString())
        )

        // Delete plan meta information
        writableDatabase.delete(
            ReadingPlan.TABLE_NAME,
            "${ReadingPlan.COLUMN_ID}=?",
            arrayOf(readingPlanID.toString())
        )

        // If active reading plan is this plan. Change to 0 to be forced to select another plan
        if (currentActiveReadingPlanID == readingPlanID)
            currentActiveReadingPlanID = 0

        return true
    }

    /**
     * @param readingPlanID should ONLY be null when selectionArgs is supplied
     * @param queryTable default value is [ReadingPlan.TABLE_NAME]
     * @param selection default value is "${[ReadingPlan.COLUMN_ID]}=?"
     * @param selectionArgs default value is arrayOf([readingPlanID].toString())
     */
    private fun <T: Any> queryDatabaseValue(
        returnType: KClass<T>,
        readingPlanID: Int? = null,
        queryColumn: String,
        queryTable: String = ReadingPlan.TABLE_NAME,
        selection: String = "${ReadingPlan.COLUMN_ID}=?",
        selectionArgs: Array<String> = arrayOf(readingPlanID.toString()),
        orderBy: String? = null
    ): T? {
        val q = writableDatabase.query(
            queryTable,
            arrayOf(queryColumn),
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        )
        val returnValue: T? =
            if (q.moveToFirst())
                when (returnType) {
                    String::class -> {
                        if (!q.isNull(0)) q.getString(0) as T else null
                    }
                    Int::class -> {
                        if (!q.isNull(0)) q.getInt(0) as T else null
                    }
                    Long::class -> {
                        if (!q.isNull(0)) q.getLong(0) as T else null
                    }
                    else -> null
                }
            else null
        q.close()
        return returnValue
    }

    /**
     * @param fileName If this param is given, [readingPlanID] will not be used.
     * @param readingPlanID If [fileName] param is not given, this param will be used to get fileName from DB.
     */
    private fun getPlanDetailLink(readingPlanID: Int? = null, fileName: String? = null
    ): DailyReading.Companion.PlanDetails? {
        val useFileName = fileName ?: getPlanFileNameFromId(readingPlanID!!)
        return DailyReading.ABDistributedPlanDetailArray.find { it.fileName == useFileName }
    }

    /**
     * @param dateString Must be in this format: Feb-1, Mar-22, Dec-11, etc
     */
    fun dateFormatterPlanStringToDate(dateString: String): Date {
        return dateBasedFormatWithYear.parse(dateString + "/" + Calendar.getInstance().get(Calendar.YEAR))
    }

    /**
     * @return Will return string in this format: Feb-1, Mar-22, Dec-11, etc
     */
    private fun dateFormatterPlanDateToString(date: Date): String {
        return dateBasedFormatMonthDay.format(date).toString()
    }

}

/**
 *
 * @throws Exception when there is no [readingPlanIDParam] provided AND ALSO can not get the current
 * active reading plan from DB. It usually simply means that no plans have been started as yet
 */
class ReadingPlanInformationDB(private val readingPlanIDParam: Int?) {
    companion object {
        const val TAG = "ReadingPlanInfoDB"
        private const val INCLUSIVE_VERSIFICATION = SystemNRSVA.V11N_NAME
    }

    private val dbAdapter = ReadingPlanDBAdapter()

    val readingPlanID: Int = readingPlanIDParam ?: dbAdapter.currentActiveReadingPlanID
    val isDateBased: Boolean = dbAdapter.getIsDateBasedPlan(readingPlanID)
    val fileName: String = dbAdapter.getPlanFileNameFromId(readingPlanID)
    val planName: String = dbAdapter.getPlanName(readingPlanID)
    val planDescription: String = dbAdapter.getPlanDescription(readingPlanID)
    val startDate: Date? get() = dbAdapter.getPlanStartDate(readingPlanID)
    val totalDays: Int = dbAdapter.getPlanTotalDays(readingPlanID)
    val isCustomUserAdded: Boolean = dbAdapter.getIsCustomUserAddedPlan(fileName)
    private val versificationName: String? = dbAdapter.getPlanVersificationName(readingPlanID)

    /**
     * Get versification specified in properties file e.g. 'Versification=Vulg', now stored in DB
     * Default is KJV.
     * If specified Versification is not found then use NRSVA because it includes most books possible
     */
    private var readingPlanVersification: Versification? = null
    fun getReadingPlanVersification(): Versification? {
        if (readingPlanVersification == null) {
            readingPlanVersification = try {
                Versifications.instance().getVersification(versificationName)
            } catch (e: Exception) {
                Log.e(TAG, """Error loading versification from Reading plan:$fileName.
                    Will now use $INCLUSIVE_VERSIFICATION because it includes most books.""")
                Versifications.instance().getVersification(INCLUSIVE_VERSIFICATION)
            }
        }

        return readingPlanVersification!!
    }


    override fun toString(): String {
        return """
            metaId=$readingPlanID
            isDateBased=$isDateBased
            fileName=$fileName
            planName=$planName
            planDescription=$planDescription
            startDate=$startDate
            totalDays=$totalDays
            versificationName=$versificationName
            isCustomUserAdded=$isCustomUserAdded
        """.trimIndent()
    }

}

/**
 * @param readingPlanInformationParam Optional. Either pass in this and optionally param [readingPlanDayNumberParam],
 * or also pass in the rest of the following params to not go to DB for each value.
 * @param readingPlanDayNumberParam Optional. If not supplied, this class will get the current reading day of plan
 * based on [readingPlanInformationParam] current day if that param is passed in.
 */
class ReadingPlanOneDayDB(private val readingPlanInformationParam: ReadingPlanInformationDB?,
                          private val readingPlanDayNumberParam: Int? = null,

                        // Values used in class
                          private var readingPlanID: Int? = null,
                          var dayNumber: Int? = null,
                          private var readingChaptersString: String? = null,
                          var readingDateForDateBasedPlan: String? = null,
                          private var readingStatusJSON: String? = null
) {
    companion object {
        const val TAG = "ReadingPlanOneDayDB"

        val app = BibleApplication.application
        val dbAdapter = ReadingPlanDBAdapter()
    }

    var readingPlanInfo: ReadingPlanInformationDB =
        readingPlanInformationParam ?: ReadingPlanInformationDB(readingPlanID)

    init {
        if (readingPlanID == null) {
            readingPlanID = readingPlanInfo.readingPlanID
            dayNumber = readingPlanDayNumberParam ?: dbAdapter.getCurrentDayNumber(readingPlanID!!)
            readingChaptersString = dbAdapter.getDayReadingChaptersString(readingPlanID!!, dayNumber!!)
            readingStatusJSON = dbAdapter.getDayReadingStatus(readingPlanID!!, dayNumber!!)
            readingDateForDateBasedPlan = dbAdapter.getDayDateString(readingPlanID!!, dayNumber!!)
        }
    }


    /** Get the day description as (e.g. Day 46)
     */
    val dayAndNumberDescription: String = app.getString(R.string.rdg_plan_day, dayNumber.toString())

    val numberOfReadings: Int = readingChaptersString?.split(",")?.count() ?: 0

    val dateString: String = formatReadingDateForDateBasedPlan() ?: calculateReadingDateString()
    private fun formatReadingDateForDateBasedPlan(): String? {
        if (readingDateForDateBasedPlan == null) { return null }
        val returnDate = dbAdapter.dateFormatterPlanStringToDate(readingDateForDateBasedPlan!!)
        return SimpleDateFormat.getDateInstance().format(returnDate)
    }
    private fun calculateReadingDateString(): String {
        var dateString = SimpleDateFormat.getDateInstance().format(dbAdapter.getTodayDate)
        val startDate = readingPlanInfo.startDate
        if (startDate != null) {
            val cal = Calendar.getInstance()
            cal.time = startDate
            cal.add(Calendar.DAY_OF_MONTH, dayNumber!! - 1)
            dateString = SimpleDateFormat.getDateInstance().format(cal.time)
        }
        return dateString
    }

    val readingStatus = ReadingStatus.fromJsonToObject(readingPlanID!!, numberOfReadings, readingStatusJSON)

    val readingChaptersKeyArray = generateReadingKeys()
    val readingChaptersDescription: String
        get() {
            val readingsBuilder = StringBuilder()
            var appendString = ""
            for (i in readingChaptersKeyArray!!.indices) {
                readingsBuilder.append(appendString)
                readingsBuilder.append(readingChaptersKeyArray[i].name)
                appendString = ", "
            }
            return readingsBuilder.toString()
        }

    fun getReadingKey(readingNumber: Int): Key {
        return readingChaptersKeyArray?.get(readingNumber)!!
    }

    private fun generateReadingKeys(): ArrayList<Key>? {
        if (readingChaptersString != null) {
            val readingKeyList = ArrayList<Key>()


            if (StringUtils.isNotEmpty(readingChaptersString)) {
                val passageReader = PassageReader(readingPlanInfo.getReadingPlanVersification()!!)
                val readingArray =
                    readingChaptersString!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (reading in readingArray) {
                    //use the v11n specified in the reading plan (default is KJV)
                    readingKeyList.add(passageReader.getKey(reading))
                }
            }
            return readingKeyList
        }
        return null
    }


    @Serializable
    data class ChapterRead(val readingNumber: Int,
                           var isRead: Boolean = false)

    @Serializable
    data class ReadingStatus(val readingPlanID: Int,
                             val numberOfReadings: Int,
                             var chapterReadArray: List<ChapterRead?>?

    ) {
        companion object {
            fun fromJsonToObject(readingPlanID: Int, numOfReadings: Int, jsonString: String?): ReadingStatus {
                if (jsonString == null) {
                    return ReadingStatus(readingPlanID, numOfReadings,null)
                }
                return try {
                    Json(JsonConfiguration(strictMode = false)).parse(serializer(), jsonString)
                } catch (ex: SerializationException) {
                    ReadingStatus(readingPlanID, numOfReadings,null)
                } catch (ex: IllegalArgumentException) {
                    ReadingStatus(readingPlanID, numOfReadings,null)
                }
            }
        }

        fun toJsonString(): String {
            return Json.stringify(serializer(), this)
        }

        init {
            if (chapterReadArray == null) {
                val chapterReadArrayList = ArrayList<ChapterRead>()
                for (i in 1..numberOfReadings) {
                    chapterReadArrayList.add(ChapterRead(i))
                }
                chapterReadArray = chapterReadArrayList
            }
        }

        fun isAllRead(): Boolean {
                for (i in 0 until numberOfReadings) {
                    if (!isRead(i)) {
                        return false
                    }
                }
                return true
            }

        fun isRead(readingNumber: Int): Boolean {
            return chapterReadArray?.get(readingNumber)?.isRead!!
        }

        fun setRead(readingNumber: Int, dayNumber: Int, saveStatus: Boolean = true) {
            chapterReadArray?.get(readingNumber)?.isRead = true
            if (saveStatus) { saveStatus(dayNumber) }
        }

        fun setUnread(readingNumber: Int, dayNumber: Int, saveStatus: Boolean = true) {
            chapterReadArray?.get(readingNumber)?.isRead = false
            if (saveStatus) { saveStatus(dayNumber) }
        }

        fun setAllRead(dayNumber: Int) {
            for (i in 0 until numberOfReadings) {
                setRead(i, dayNumber, false)
            }
            saveStatus(dayNumber)
        }

        /** Serialize [ReadingStatus] to DB in JSON string
         */
        private fun saveStatus(dayNumber: Int) {
            dbAdapter.setDayReadingStatus(readingPlanID, dayNumber, toJsonString())
        }
    }


    override fun toString(): String {
        return """metaId=$readingPlanID
            dayNumber=$dayNumber
            readingChaptersString=$readingChaptersString
            readingDateForDateBasedPlan=$readingDateForDateBasedPlan
            readingStatusJSON=$readingStatusJSON""".trimIndent()
    }

}
