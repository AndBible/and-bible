/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.service.db.readingplan.ReadingPlanDBAdapter

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OneDaysReadingsDto(val day: Int,
                         private var mReadings: String?,
                         val readingPlanInfo: ReadingPlanInfoDto,
                         val planName: String?,
                         val planDescription: String?)
    : Comparable<OneDaysReadingsDto>
{
    private var mReadingKeys: List<Key>? = null
    var dateBasedReadingDate: Date? = null
    var dateBasedReadingDateStringFromFile: String? = null

    init {
        checkKeysGenerated()
    }

    val dayDesc: String
        get() = BibleApplication.application.getString(R.string.rdg_plan_day, Integer.toString(day))

    /** get a string representing the date this reading is planned for
     */
    val readingDateString: String
        get() {
            var dateString = ""
            val startDate = readingPlanInfo.startdate
            if (startDate != null) {
                val cal = Calendar.getInstance()
                cal.time = startDate
                cal.add(Calendar.DAY_OF_MONTH, day - 1)
                dateString = SimpleDateFormat.getDateInstance().format(cal.time)
            }
            return dateString
        }

    val readingsDesc: String
        get() {
            val readingsBldr = StringBuilder()
            for (i in mReadingKeys!!.indices) {
                if (i > 0) {
                    readingsBldr.append(", ")
                }
                readingsBldr.append(mReadingKeys!![i].name)
            }
            return readingsBldr.toString()
        }
    val numReadings: Int
        get() {
            return mReadingKeys!!.size
        }

    val readingKeys: List<Key>
        get() {
            return mReadingKeys!!
        }

    override fun toString(): String {
        return dayDesc
    }

    override fun compareTo(other: OneDaysReadingsDto): Int {
        return day - other.day
    }

    fun getReadingKey(no: Int): Key {
        return mReadingKeys!![no]
    }

    fun getReadingsString(): String? {
        return mReadings
    }

    @Synchronized
    private fun checkKeysGenerated() {
        if (mReadingKeys == null) {
            val readingKeyList = ArrayList<Key>()


            if (StringUtils.isNotEmpty(mReadings)) {
                // Check if string contains : (Would happen in case of date-based plan, shows Feb-1:Gen.1,Exo.1)
                if (StringUtils.contains(mReadings,";")) {
                    dateBasedReadingDateStringFromFile = mReadings?.replace(";.*".toRegex(),"") // like Feb-1
                    dateBasedReadingDate =
                        ReadingPlanDBAdapter.dateBasedFormatWithYear.parse(
                            dateBasedReadingDateStringFromFile + "/" + Calendar.getInstance().get(Calendar.YEAR)
                        )
                    mReadings = mReadings?.replace("^.*;".toRegex(),"")
                }

                val passageReader = PassageReader(readingPlanInfo.versification!!)
                val readingArray = mReadings!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (reading in readingArray) {
                    //use the v11n specified in the reading plan (default is KJV)
                    readingKeyList.add(passageReader.getKey(reading))
                }
            }
            mReadingKeys = readingKeyList
        }
    }
}
