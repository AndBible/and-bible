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

import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.passage.Key

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OneDaysReadingsDto(val day: Int, private val mReadings: String, val readingPlanInfo: ReadingPlanInfoDto) :
		Comparable<OneDaysReadingsDto>
{
    private var mReadingKeys: List<Key>? = null

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
            checkKeysGenerated()
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
            checkKeysGenerated()
            return mReadingKeys!!.size
        }

    val readingKeys: List<Key>
        get() {
            checkKeysGenerated()
            return mReadingKeys!!
        }

    override fun toString(): String {
        return dayDesc
    }

    override fun compareTo(other: OneDaysReadingsDto): Int {
        return day - other.day
    }

    fun getReadingKey(no: Int): Key {
        checkKeysGenerated()
        return mReadingKeys!![no]
    }

    @Synchronized
    private fun checkKeysGenerated() {
        if (mReadingKeys == null) {
            val readingKeyList = ArrayList<Key>()

            if (StringUtils.isNotEmpty(mReadings)) {
                val passageReader = PassageReader(readingPlanInfo.versification!!)
                val readingArray = mReadings.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (reading in readingArray) {
                    //use the v11n specified in the reading plan (default is KJV)
                    readingKeyList.add(passageReader.getKey(reading))
                }
            }
            mReadingKeys = readingKeyList
        }
    }
}
