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

import net.bible.service.common.CommonUtils
import net.bible.service.db.readingplan.ReadingPlanDbAdapter

import org.crosswire.jsword.versification.Versification

import java.util.Date

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ReadingPlanInfoDto(var planCode: String) {
    var planName: String? = null
    var planDescription: String? = null
    var versification: Versification? = null
    var numberOfPlanDays: Int = 0
    var isDateBasedPlan: Boolean = false
    val rAdapter = ReadingPlanDbAdapter.instance

    /** a persistent start date
     * return the date the plan was started or null if not started
     */
    val startdate: Date?
        get() {
            val startDate = rAdapter.getReadingStartDate(planCode)
            startDate ?: return null
            return Date(startDate)
        }

    /** set a persistent start date
     */
    fun start() {
        startOn(CommonUtils.truncatedDate, false)
    }

    fun setStartDate(startDate: Date) {
        startOn(startDate, true)
    }

    private fun startOn(date: Date, force: Boolean) {

        // if changing plan
        if (startdate == null || force)
            rAdapter.setReadingStartDate(planCode, date.time)
    }

    override fun toString(): String {
        return "$planName"
    }

}
