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

package net.bible.service.readingplan

import android.annotation.SuppressLint
import net.bible.android.BibleApplication
import net.bible.android.activity.R

import org.crosswire.jsword.passage.Key

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OneDaysReadingsDto(val day: Int, private val readingsString: String?, val readingPlanInfo: ReadingPlanInfoDto)
    : Comparable<OneDaysReadingsDto>
{
    @SuppressLint("SimpleDateFormat")
    private val dateBasedFormatWithYear = SimpleDateFormat("MMM-d/yyyy")

    private var readingKeys: List<Key>? = null
    /** reading date for date-based plan, else null
     */
    var readingDate: Date? = null

    init {
        checkKeysGenerated()
    }

    val dayDesc: String get() = BibleApplication.application.getString(R.string.rdg_plan_day, day.toString())
    val isDateBasedPlan: Boolean get() = readingPlanInfo.isDateBasedPlan

    /** get a string representing the date this reading is planned for
     */
    val readingDateString: String
        get() {
            val readingDate = readingDate
            return if (readingDate != null) {
                SimpleDateFormat.getDateInstance().format(readingDate)
            } else {
                var dateString = ""
                val startDate = readingPlanInfo.startdate
                if (startDate != null) {
                    val cal = Calendar.getInstance()
                    cal.time = startDate
                    cal.add(Calendar.DAY_OF_MONTH, day - 1)
                    dateString = SimpleDateFormat.getDateInstance().format(cal.time)
                }
                dateString
            }
        }

    val readingsDesc: String
        get() {
            val readingsBldr = StringBuilder()
            for (i in readingKeys!!.indices) {
                if (i > 0) {
                    readingsBldr.append(", ")
                }
                readingsBldr.append(readingKeys!![i].name)
            }
            return readingsBldr.toString()
        }
    val numReadings: Int
        get() {
            return readingKeys!!.size
        }

    val getReadingKeys: List<Key>
        get() {
            return readingKeys!!
        }

    override fun toString(): String {
        return dayDesc
    }

    override fun compareTo(other: OneDaysReadingsDto): Int {
        return day - other.day
    }

    /**
     * @param readingNo 1-based reading number
     */
    fun getReadingKey(readingNo: Int): Key {
        return readingKeys!![readingNo-1]
    }

    @Synchronized
    private fun checkKeysGenerated() {
        if (readingKeys == null) {
            val readingKeyList = ArrayList<Key>()

            if (!readingsString.isNullOrEmpty()) {
                val passageReader = PassageReader(readingPlanInfo.versification!!)

                val readingArray: Array<String>
                if (readingsString.contains(';')) {
                    // date-based reading plan
                    val dateBasedDay = readingsString.replace(";.*".toRegex(), "") // like Feb-1
                    readingDate = dateFormatterPlanStringToDate(dateBasedDay)
                    readingArray = readingsString.replace("^.*;".toRegex(), "")
                        .split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                } else {
                    readingArray = readingsString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                }

                for (reading in readingArray) {
                    //use the v11n specified in the reading plan (default is KJV)
                    readingKeyList.add(passageReader.getKey(reading))
                }
            }
            readingKeys = readingKeyList
        }
    }

    /**
     * @param dateString Must be in this format: Feb-1, Mar-22, Dec-11, etc
     */
    private fun dateFormatterPlanStringToDate(dateString: String): Date {
        val calYear = Calendar.getInstance().get(Calendar.YEAR)
        return dateBasedFormatWithYear.parse("$dateString/$calYear")
    }
}
