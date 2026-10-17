/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.platform

import net.bible.sharedcore.log.Log
import net.bible.sharedlogic.platform.LocalCalendar
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** [LocalCalendar] on `java.util.Calendar` and `SimpleDateFormat`, the code the reading plans used inline before. */
class AndroidLocalCalendar : LocalCalendar {
    override fun startOfToday(): Long = Calendar.getInstance().let { date ->
        date.set(Calendar.HOUR_OF_DAY, 0)
        date.set(Calendar.MINUTE, 0)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        date.time.time
    }

    override fun startOfDay(epochMs: Long): Long = Calendar.getInstance().let { date ->
        date.timeInMillis = epochMs
        date.set(Calendar.HOUR_OF_DAY, 0)
        date.set(Calendar.MINUTE, 0)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        date.time.time
    }

    override fun addDays(epochMs: Long, days: Int): Long = Calendar.getInstance().apply {
        time = Date(epochMs)
        add(Calendar.DATE, days)
    }.time.time

    override fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    override fun formatDefaultDate(epochMs: Long): String = SimpleDateFormat.getDateInstance().format(Date(epochMs))

    override fun parseMonthDayYear(text: String): Long {
        val dateBasedWithYearUsaFormat = SimpleDateFormat("MMM-d/yyyy", Locale.US)
        val dateBasedWithYear = SimpleDateFormat("MMM-d/yyyy", Locale.getDefault())
        return try {
            (dateBasedWithYearUsaFormat.parse(text) ?: dateBasedWithYear.parse(text)).time
        } catch (e: ParseException) {
            Log.w(TAG, "Unable to parse date ($text) in US format. Trying with default locale", e)
            dateBasedWithYear.parse(text)!!.time
        }
    }

    private fun at(epochMs: Long) = Calendar.getInstance().apply { timeInMillis = epochMs }
    private fun Calendar.zeroTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    override fun startOfLocalDay(epochMs: Long) = at(epochMs).apply { zeroTime() }.timeInMillis
    override fun plusLocalDays(epochMs: Long, days: Int) = at(epochMs).apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
    override fun plusWeeks(epochMs: Long, weeks: Int) = at(epochMs).apply { add(Calendar.WEEK_OF_YEAR, weeks) }.timeInMillis
    override fun startOfLocalWeek(epochMs: Long) = at(epochMs).apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        zeroTime()
    }.timeInMillis
    override fun localMonth(epochMs: Long) = at(epochMs).get(Calendar.MONTH)
    override fun shortMonthName(epochMs: Long): String =
        at(epochMs).getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""

    private companion object {
        private const val TAG = "OneDaysReadingDto"
    }
}
