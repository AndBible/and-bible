/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.readingplan

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.CoreStrings

import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.VersificationsMapper

import net.bible.android.database.DbDate
import net.bible.sharedlogic.platform.LocalCalendar
import net.bible.sharedlogic.sync.withMonitor

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class OneDaysReadingsDto(val day: Int, private val readingsString: String?, val readingPlanInfo: ReadingPlanInfoDto, private val coreStrings: CoreStrings, private val calendar: LocalCalendar)
    : Comparable<OneDaysReadingsDto>
{
    private var readingKeys: List<Key>? = null
    /** Set on copies made by [forVersification]; mapped on first access so day/numReadings callers never pay for it. */
    private var mappedKeys: Lazy<List<Key>>? = null
    /** reading date for date-based plan, else null
     */
    var readingDate: DbDate? = null

    init {
        checkKeysGenerated()
    }

    val dayDesc: String get() = coreStrings.readingPlanDay(day.toString())
    val isDateBasedPlan: Boolean get() = readingPlanInfo.isDateBasedPlan

    /** get a string representing the date this reading is planned for
     */
    val readingDateString: String
        get() {
            val readingDate = readingDate
            return if (readingDate != null) {
                calendar.formatDefaultDate(readingDate.getTime())
            } else {
                var dateString = ""
                val startDate = readingPlanInfo.startDate
                if (startDate != null) {
                    dateString = calendar.formatDefaultDate(calendar.addDays(startDate.getTime(), day - 1))
                }
                dateString
            }
        }

    val readingsDesc: String
        get() {
            val readingsBldr = StringBuilder()
            val readingKeys = keys()
            for (i in readingKeys.indices) {
                if (i > 0) {
                    readingsBldr.append(", ")
                }
                readingsBldr.append(readingKeys[i].getName())
            }
            return readingsBldr.toString()
        }
    val numReadings: Int
        get() {
            return keys().size
        }

    val getReadingKeys: List<Key>
        get() {
            return keys()
        }

    private fun keys(): List<Key> = mappedKeys?.value ?: readingKeys!!

    /**
     * Keeps plan numbering and read-status indices intact while displaying the reading Bible's references.
     * Returns this when the plan already uses [versification]. A key that cannot be mapped is kept as is.
     */
    fun forVersification(versification: Versification): OneDaysReadingsDto {
        if (readingPlanInfo.versification == versification) return this
        return OneDaysReadingsDto(day, null, readingPlanInfo, coreStrings, calendar).also { converted ->
            converted.readingDate = readingDate
            val original = keys()
            converted.mappedKeys = lazy {
                original.map {
                    try {
                        VersificationsMapper.instance().map(KeyUtil.getPassage(it), versification)
                    } catch (e: Exception) {
                        Log.w("OneDaysReadingsDto", "Cannot map ${it.getName()} to ${versification.name}", e)
                        it
                    }
                }
            }
        }
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
        return keys()[readingNo-1]
    }

    private fun checkKeysGenerated() = withMonitor(this) {
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
    private fun dateFormatterPlanStringToDate(dateString: String): DbDate {
        val calYear = calendar.currentYear()
        // the port tries US month names, then the default locale's (and logs the fallback)
        return DbDate(calendar.parseMonthDayYear("$dateString/$calYear"))
    }
}
