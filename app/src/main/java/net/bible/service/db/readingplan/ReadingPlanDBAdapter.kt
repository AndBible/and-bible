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
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JSON
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.service.common.CommonUtils
import net.bible.service.db.CommonDatabaseHelper
import net.bible.service.db.readingplan.ReadingPlanDatabaseDefinition.ReadingPlanDays
import net.bible.service.db.readingplan.ReadingPlanDatabaseDefinition.ReadingPlanMeta
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

/**
 * @author Timmy Braun [tim.bze at gmail dot com] (2/14/2019)
 */
class ReadingPlanDBAdapter {
    companion object {
        private const val TAG = "ReadingPlanDBAdapter"
        const val CURRENT_PLAN_INDEX = "currentReadingPlanIndex"
        private val app = BibleApplication.application
        private val dbHelper: CommonDatabaseHelper = CommonDatabaseHelper.getInstance()

        private val writableDatabase = dbHelper.readableDatabase
        private val preferences: SharedPreferences get() = CommonUtils.getSharedPreferences()

        private val dateBasedFormatMonthDay = SimpleDateFormat("MMM-d")
        val dateBasedFormatWithYear = SimpleDateFormat("MMM-d/yyyy")

    }

    var currentActiveReadingPlanID: Int
        get() {
            return preferences.getInt(CURRENT_PLAN_INDEX,0)
        }
        set(newValue) {
            preferences.edit().putInt(CURRENT_PLAN_INDEX, newValue).apply()
        }

    val currentPlanShortCode: String get() =
        StringUtils.left(getPlanFileNameFromId(currentActiveReadingPlanID),8)
    val currentDayAndNumberDescription: String get() =
        app.getString(
            R.string.rdg_plan_day,
            getCurrentDayNumber(currentActiveReadingPlanID).toString()
        )

    fun getCurrentDayNumber(readingPlanMetaId: Int): Int {
        if (getIsDateBasedPlan(readingPlanMetaId)) {
            val q = writableDatabase.query(
                ReadingPlanDays.TABLE_NAME,
                arrayOf(ReadingPlanDays.COLUMN_DAY_NUMBER),
                "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=? AND ${ReadingPlanDays.COLUMN_READING_DATE}=?",
                arrayOf(readingPlanMetaId.toString(), dateFormatterPlanDateToString(getTodayDate)),
                null, null, null
            )
            var result = 0
            if (q.moveToFirst()) {
                result = q.getInt(0)
            }
            q.close()

            return result

        } else {

            val currentDay: Int = getMetaIntegerDB(readingPlanMetaId, ReadingPlanMeta.COLUMN_CURRENT_DAY) ?: 1
            return if (currentDay == 0) 1
                else currentDay
        }
    }

    fun setCurrentDayNumber(readingPlanMetaId: Int, dayNumber: Int) {
        if (!getIsDateBasedPlan(readingPlanMetaId)) {
            val values = ContentValues().apply {
                put(ReadingPlanMeta.COLUMN_CURRENT_DAY, dayNumber)
            }
            val whereClause = "${ReadingPlanMeta.COLUMN_ID}=?"
            val whereArgs = arrayOf(readingPlanMetaId.toString())

            writableDatabase.update(ReadingPlanMeta.TABLE_NAME, values, whereClause, whereArgs)
        }
    }

    fun getDueDayToBeRead(readingPlanMetaId: Int): Long {
        val today = CommonUtils.getTruncatedDate()
        val startDate = getPlanStartDate(readingPlanMetaId)
        startDate ?: return -1
        // on final day, after done the startDate will be null

        // should not need to round as we use truncated dates, but safety first
        // later found that rounding is necessary (due to DST I think) because
        // when the clocks went forward the difference became 88.95833 but should have been 89
        val diffInDays = (today.time - startDate.time) / (1000.0 * 60.0 * 60.0 * 24.0)
        val diffInWholeDays = Math.round(diffInDays)
        Log.d(TAG, "Days diff between today and start:$diffInWholeDays")

        // if diff is zero then we are on day 1 so add 1
        return diffInWholeDays + 1
    }

    fun incrementCurrentPlanDay(): Int {
        val readingPlanMetaId: Int = currentActiveReadingPlanID
        val currentDay: Int = getCurrentDayNumber(readingPlanMetaId)

        val q = writableDatabase.query(
            ReadingPlanDays.TABLE_NAME,
            arrayOf(ReadingPlanDays.COLUMN_DAY_NUMBER),
            "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}>?",
            arrayOf(readingPlanMetaId.toString(), currentDay.toString()),
            null,
            null,
            ReadingPlanDays.COLUMN_DAY_NUMBER
        )
        var result = 0
        if (q.moveToFirst()) {
            result = q.getInt(0)
            q.close()
        }

        // Update plan current day
        if (result > 0) {
            setCurrentDayNumber(currentActiveReadingPlanID, result)
        }


        return result
    }

    fun setAllDaysReadUpTo(dayNumber: Int) {
        val readingPlanMetaId: Int = currentActiveReadingPlanID
        val numberOfReadings: Int = getDayNumberOfReadings(readingPlanMetaId,dayNumber)

        val values = ContentValues().apply {
            put(ReadingPlanDays.COLUMN_READ_STATUS,
                ReadingPlanOneDayDB.ReadingStatus(
                    readingPlanMetaId,
                    numberOfReadings,
                    getDayReadingChaptersArrayAsAllRead(numberOfReadings)
                ).toJsonString()
            )
        }
        val whereClause = "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}<=?"
        val whereArgs = arrayOf(readingPlanMetaId.toString(), dayNumber.toString())

        writableDatabase.update(ReadingPlanDays.TABLE_NAME, values, whereClause, whereArgs)
    }

    fun getIsDateBasedPlan(ReadingPlanMetaID: Int): Boolean {
        return when (getMetaIntegerDB(ReadingPlanMetaID,ReadingPlanMeta.COLUMN_CURRENT_DAY)) {
            ReadingPlanMeta.CONSTANT_CURRENT_DAY_BY_DATE -> true
            else -> false
        }
    }

    fun getIsCustomUserAddedPlan(fileName: String): Boolean {
        return getPlanDetailLink(null, fileName) == null
    }

    fun getIsPlanAlreadyImported(fileName: String): Boolean {
        val q = writableDatabase.query(
            ReadingPlanMeta.TABLE_NAME,
            arrayOf(ReadingPlanMeta.COLUMN_ID),
            "${ReadingPlanMeta.COLUMN_PLAN_FILE_NAME}=?",
            arrayOf(fileName),
            null,null,null)
        var result = false
        if (q.moveToFirst()) {
            result = !q.isNull(0)
        }
        q.close()
        return result
    }

    /**
     * Get's Reading Plan description from resources strings. If not available there, it will try to get description
     * from DB with second of this function with double constructor, here: [getPlanDescription]
     */
    fun getPlanDescription(ReadingPlanMetaID: Int): String {
        return getPlanDetailLink(ReadingPlanMetaID)?.planDescription ?:
            getPlanDescription(ReadingPlanMetaID, true)
    }

    /**
     * Get's Reading Plan description from database. AndBible distributed reading plans
     * should always get description from resources strings, which this first function with one
     * constructor param does, here: [getPlanDescription]
     */
    private fun getPlanDescription(ReadingPlanMetaID: Int, fromDB: Boolean): String {
        return getMetaStringDB(ReadingPlanMetaID, ReadingPlanMeta.COLUMN_DESCRIPTION) ?: ""
    }

    fun getPlanFileNameFromId(ReadingPlanMetaID: Int): String {
        return getMetaStringDB(ReadingPlanMetaID, ReadingPlanMeta.COLUMN_PLAN_FILE_NAME) ?: ""
    }

    fun getPlanList(): List<ReadingPlanInformationDB> {
        val readingPlanList = ArrayList<ReadingPlanInformationDB>()

        val q = writableDatabase.query(
            ReadingPlanMeta.TABLE_NAME, arrayOf(ReadingPlanMeta.COLUMN_ID),
            null,null,null,null,null
        )
        while (q.moveToNext()) {
            readingPlanList.add(ReadingPlanInformationDB(q.getInt(0)))
        }
        q.close()

        Log.d(TAG, "${readingPlanList.count()} plans returned in list.")
        return readingPlanList
    }

    fun getPlanDaysList(readingPlanMetaId: Int): List<ReadingPlanOneDayDB> {
        val readingPlanDaysList = ArrayList<ReadingPlanOneDayDB>()

        val planInfo = ReadingPlanInformationDB(readingPlanMetaId)
        val q = writableDatabase.query(
            ReadingPlanDays.TABLE_NAME,
            arrayOf(
                ReadingPlanDays.COLUMN_DAY_NUMBER,
                ReadingPlanDays.COLUMN_DAY_CHAPTERS,
                ReadingPlanDays.COLUMN_READING_DATE,
                ReadingPlanDays.COLUMN_READ_STATUS
            ),
            "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=?",
            arrayOf(readingPlanMetaId.toString()),
            null,
            null,
            ReadingPlanDays.COLUMN_DAY_NUMBER
        )
        while (q.moveToNext()) {
            readingPlanDaysList.add(ReadingPlanOneDayDB(
                planInfo,
                null,
                readingPlanMetaId,
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

    fun getPlanName(ReadingPlanMetaID: Int): String {
        return getPlanDetailLink(ReadingPlanMetaID)?.planName ?: getPlanName(ReadingPlanMetaID, true)
    }

    private fun getPlanName(ReadingPlanMetaID: Int, fromDB: Boolean): String {
        return when (fromDB) {
            true -> getMetaStringDB(ReadingPlanMetaID, ReadingPlanMeta.COLUMN_PLAN_NAME) ?: ""
            else -> ""
        }
    }

    fun getPlanStartDate(readingPlanMetaId: Int): Date? {
        val startDateInt: Long? = getMetaLongDB(readingPlanMetaId,ReadingPlanMeta.COLUMN_DATE_START)
        startDateInt ?: return null
        return Date(startDateInt)
    }

    /**
     * @param startDate If parameter is not passed, will set start date as today.
     */
    fun setPlanStartDate(readingPlanMetaId: Int, startDate: Date? = null) {
        val updateDate: Date = startDate ?: getTodayDate
        val values = ContentValues().apply {
            put(ReadingPlanMeta.COLUMN_DATE_START, updateDate.time)
        }
        val whereClause = "${ReadingPlanMeta.COLUMN_ID}=?"
        val whereArgs = arrayOf(readingPlanMetaId.toString())

        val rowsAffected = writableDatabase.update(ReadingPlanMeta.TABLE_NAME, values, whereClause, whereArgs)
        Log.d(TAG, """Set start date. $rowsAffected DB rows updated -- R.Plan.Id=$readingPlanMetaId
            -- whereClause=$whereClause -- whereArgs=$whereArgs""")
    }

    val getTodayDate: Date get() = Calendar.getInstance().time

    fun getPlanTotalDays(ReadingPlanMetaID: Int): Int {
        return getMetaIntegerDB(ReadingPlanMetaID,ReadingPlanMeta.COLUMN_DAYS_IN_PLAN) ?: 0
    }

    fun getPlanVersificationName(ReadingPlanMetaID: Int): String? {
        return getMetaStringDB(ReadingPlanMetaID, ReadingPlanMeta.COLUMN_VERSIFICATION_NAME)
    }

    fun getDayDateString(ReadingPlanMetaID: Int, dayNumber: Int): String? {
        return getDayStringDB(ReadingPlanMetaID,dayNumber,ReadingPlanDays.COLUMN_READING_DATE)
    }

    private fun getDayNumberOfReadings(ReadingPlanMetaID: Int, dayNumber: Int): Int {
        val readingsString: String? = getDayReadingChaptersString(ReadingPlanMetaID, dayNumber)
        readingsString ?: return 0

        return readingsString.split(",").count()
    }

    fun getDayReadingChaptersString(readingPlanMetaID: Int, dayNumber: Int): String? {
        return getDayStringDB(readingPlanMetaID, dayNumber, ReadingPlanDays.COLUMN_DAY_CHAPTERS)
    }

    private fun getDayReadingChaptersArrayAsAllRead(numberOfReadings: Int): List<ReadingPlanOneDayDB.ChapterRead?> {
        val chapterArray = ArrayList<ReadingPlanOneDayDB.ChapterRead?>()

        for (i in 0 until numberOfReadings) {
            chapterArray.add(ReadingPlanOneDayDB.ChapterRead(i + 1,true))
        }
        return chapterArray
    }

    fun getDayReadingStatus(ReadingPlanMetaID: Int, dayNumber: Int): String {
        return getDayStringDB(ReadingPlanMetaID,dayNumber,ReadingPlanDays.COLUMN_READ_STATUS) ?: ""
    }

    fun setDayReadingStatus(readingPlanMetaID: Int, dayNumber: Int, readingStatusJson: String) {

        val values = ContentValues().apply {
            put(ReadingPlanDays.COLUMN_READ_STATUS, readingStatusJson)
        }

        val whereClause = "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}=?"
        val whereArgs = arrayOf(readingPlanMetaID.toString(),dayNumber.toString())

        writableDatabase.update(ReadingPlanDays.TABLE_NAME, values, whereClause, whereArgs)
    }

    fun resetPlan(readingPlanMetaId: Int) {

        // Clear reading status on all days
        val values = ContentValues().apply {
            putNull(ReadingPlanDays.COLUMN_READ_STATUS)
        }
        val whereClause = "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=?"
        val whereArgs = arrayOf(readingPlanMetaId.toString())

        writableDatabase.update(ReadingPlanDays.TABLE_NAME, values, whereClause, whereArgs)
        Log.d(TAG, "Cleared reading statuses on all days on reset")

        // Update meta info, reset start date and current day
        val metaValues = ContentValues().apply {
            if (!getIsDateBasedPlan(readingPlanMetaId)) {
                put(ReadingPlanMeta.COLUMN_CURRENT_DAY, 0)
            }
            put(ReadingPlanMeta.COLUMN_DATE_START, getTodayDate.time)
        }
        val metaWhereClause = "${ReadingPlanMeta.COLUMN_ID}=?"
        writableDatabase.update(ReadingPlanMeta.TABLE_NAME, metaValues, metaWhereClause, whereArgs)
        Log.d(TAG, "Updated start date and current day on reset")
    }

    fun switchToReadingPlan(readingPlanInfo: ReadingPlanInformationDB) {
        Log.d(TAG, """Switching reading plan. R.Plan.Id=${readingPlanInfo.metaID} --
            startDate=${readingPlanInfo.startDate}""")
        currentActiveReadingPlanID = readingPlanInfo.metaID
        if (getPlanStartDate(readingPlanInfo.metaID) == null) {
            setPlanStartDate(readingPlanInfo.metaID)
        }
    }

    fun deletePlan(readingPlanMetaId: Int): Boolean {
        // Delete days related to plan
        writableDatabase.delete(
            ReadingPlanDays.TABLE_NAME,
            "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=?",
            arrayOf(readingPlanMetaId.toString())
        )

        // Delete plan meta information
        writableDatabase.delete(
            ReadingPlanMeta.TABLE_NAME,
            "${ReadingPlanMeta.COLUMN_ID}=?",
            arrayOf(readingPlanMetaId.toString())
        )

        // If active reading plan is this plan. Change to 0 to be forced to select another plan
        if (currentActiveReadingPlanID == readingPlanMetaId)
            currentActiveReadingPlanID = 0

        return true
    }

    private fun getDayStringDB(ReadingPlanMetaID: Int, ReadingPlanDayNumber: Int, getColumn: String): String? {
        val q = writableDatabase.query(ReadingPlanDays.TABLE_NAME,
            arrayOf(getColumn),
            "${ReadingPlanDays.COLUMN_READING_PLAN_META_ID}=? AND ${ReadingPlanDays.COLUMN_DAY_NUMBER}=?",
            arrayOf(ReadingPlanMetaID.toString(),ReadingPlanDayNumber.toString()),
            null,null,null)
        var result: String? = null
        if (q.moveToFirst()) {
            result = q.getString(0)
        }
        q.close()
        return result
    }

    private fun getMetaIntegerDB(
        ReadingPlanMetaID: Int, getColumn: String, selection: String? = null, selectionArgs: Array<String>? = null
    ): Int? {
        val q = writableDatabase.query(ReadingPlanMeta.TABLE_NAME,
            arrayOf(getColumn),
            selection ?: "${ReadingPlanMeta.COLUMN_ID}=?",
            selectionArgs ?: arrayOf(ReadingPlanMetaID.toString()),
            null,null,null)
        var result: Int? = null
        if (q.moveToFirst()) {
            Log.d(TAG, """getMetaIntegerDB rPlanId=$ReadingPlanMetaID --
                getColumn=$getColumn -- Result=${q.getInt(0)}""")
            result = when (q.isNull(0)) {
                false -> q.getInt(0)
                else -> result
            }
        }
        q.close()
        return result
    }

    private fun getMetaLongDB(
        ReadingPlanMetaID: Int, getColumn: String, selection: String? = null, selectionArgs: Array<String>? = null
    ): Long? {
        val q = writableDatabase.query(ReadingPlanMeta.TABLE_NAME,
            arrayOf(getColumn),
            selection ?: "${ReadingPlanMeta.COLUMN_ID}=?",
            selectionArgs ?: arrayOf(ReadingPlanMetaID.toString()),
            null,null,null)
        var result: Long? = null
        if (q.moveToFirst()) {
            result = when (q.isNull(0)) {
                false -> q.getLong(0)
                else -> result
            }
        }
        q.close()
        return result
    }

    private fun getMetaStringDB(ReadingPlanMetaID: Int, getColumn: String): String? {
        val q = writableDatabase.query(
            ReadingPlanMeta.TABLE_NAME,
            arrayOf(getColumn),
            "${ReadingPlanMeta.COLUMN_ID}=?",
            arrayOf(ReadingPlanMetaID.toString()),
            null,
            null,
            null
        )
        var result: String? = null
        if (q.moveToFirst()) {
            result = q.getString(0)
        }
        q.close()
        return result
    }

    /**
     * @param fileName If this param is given, [readingPlanMetaID] will not be used.
     * @param readingPlanMetaID If [fileName] param is not given, this param will be used to get fileName from DB.
     */
    private fun getPlanDetailLink(readingPlanMetaID: Int? = null, fileName: String? = null
    ): DailyReading.Companion.PlanDetails? {
        val useFileName = fileName ?: getPlanFileNameFromId(readingPlanMetaID!!)
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
 * @throws Exception when there is no [readingPlanMetaIdParam] provided AND ALSO can not get the current
 * active reading plan from DB. It usually simply means that no plans have been started as yet
 */
class ReadingPlanInformationDB(private val readingPlanMetaIdParam: Int?) {
    companion object {
        const val TAG = "ReadingPlanInfoDB"
        private const val INCLUSIVE_VERSIFICATION = SystemNRSVA.V11N_NAME
    }

    private val dbAdapter = ReadingPlanDBAdapter()

    val metaID: Int = readingPlanMetaIdParam ?: dbAdapter.currentActiveReadingPlanID
    val isDateBased: Boolean = dbAdapter.getIsDateBasedPlan(metaID)
    val fileName: String = dbAdapter.getPlanFileNameFromId(metaID)
    val planName: String = dbAdapter.getPlanName(metaID)
    val planDescription: String = dbAdapter.getPlanDescription(metaID)
    val startDate: Date? = dbAdapter.getPlanStartDate(metaID)
    val totalDays: Int = dbAdapter.getPlanTotalDays(metaID)
    val versificationName: String? = dbAdapter.getPlanVersificationName(metaID)
    val isCustomUserAdded: Boolean = dbAdapter.getIsCustomUserAddedPlan(fileName)

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
            metaId=$metaID
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
                          private var readingPlanMetaId: Int? = null,
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
        readingPlanInformationParam ?: ReadingPlanInformationDB(readingPlanMetaId)

    init {
        if (readingPlanMetaId == null) {
            readingPlanMetaId = readingPlanInfo.metaID
            dayNumber = readingPlanDayNumberParam ?: dbAdapter.getCurrentDayNumber(readingPlanMetaId!!)
            readingChaptersString = dbAdapter.getDayReadingChaptersString(readingPlanMetaId!!, dayNumber!!)
            readingStatusJSON = dbAdapter.getDayReadingStatus(readingPlanMetaId!!, dayNumber!!)
            readingDateForDateBasedPlan = dbAdapter.getDayDateString(readingPlanMetaId!!, dayNumber!!)
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

    val readingStatus = ReadingStatus.fromJsonToObject(readingPlanMetaId!!, numberOfReadings, readingStatusJSON)

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
    data class ReadingStatus(val readingPlanMetaId: Int,
                             val numberOfReadings: Int,
                             var chapterReadArray: List<ChapterRead?>?

    ) {
        companion object {
            fun fromJsonToObject(readingPlanMetaId: Int, numOfReadings: Int, jsonString: String?): ReadingStatus {
                if (jsonString == null) {
                    return ReadingStatus(readingPlanMetaId, numOfReadings,null)
                }
                return try {
                    JSON(strictMode = false).parse(serializer(), jsonString)
                } catch (ex: SerializationException) {
                    ReadingStatus(readingPlanMetaId, numOfReadings,null)
                } catch (ex: IllegalArgumentException) {
                    ReadingStatus(readingPlanMetaId, numOfReadings,null)
                }
            }
        }

        fun toJsonString(): String {
            return JSON.stringify(serializer(), this)
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
            dbAdapter.setDayReadingStatus(readingPlanMetaId, dayNumber, toJsonString())
        }
    }


    override fun toString(): String {
        return """metaId=$readingPlanMetaId
            dayNumber=$dayNumber
            readingChaptersString=$readingChaptersString
            readingDateForDateBasedPlan=$readingDateForDateBasedPlan
            readingStatusJSON=$readingStatusJSON""".trimIndent()
    }

}
