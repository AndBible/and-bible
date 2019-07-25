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

import net.bible.service.common.CommonUtils

import org.apache.commons.lang3.time.DateUtils
import org.crosswire.jsword.versification.Versification

import java.util.Calendar
import java.util.Date

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ReadingPlanInfoDto(var code: String) {
    var planName: String? = null
    var planDescription: String? = null
    var versification: Versification? = null
    var numberOfPlanDays: Int = 0

    /** a persistent start date
     * return the date the plan was started or null if not started
     */
    val startdate: Date?
        get() {
            val startDate = CommonUtils.sharedPreferences.getLong(code + READING_PLAN_START_EXT, 0) as Long
            return if (startDate == 0L) {
                null
            } else {
                Date(startDate)
            }
        }

    /** set a persistent start date
     */
    fun start() {
        startOn(CommonUtils.truncatedDate, false)
    }

    fun setStartToJan1() {
        val jan1 = DateUtils.truncate(Date(), Calendar.YEAR)

        startOn(jan1, true)
    }

    private fun startOn(date: Date, force: Boolean) {

        // if changing plan
        if (startdate == null || force) {

            CommonUtils.sharedPreferences
                    .edit()
                    .putLong(code + READING_PLAN_START_EXT, date.time)
                    .apply()
        }
    }

    /** set a persistent start date
     */
    fun reset() {

        // if changing plan
        if (startdate == null) {
            CommonUtils.sharedPreferences
                    .edit()
                    .remove(code + READING_PLAN_START_EXT)
                    .apply()
        }
    }

    override fun toString(): String {
        return "$planName"
    }

    companion object {

        val READING_PLAN_START_EXT = "_start"
    }
}
