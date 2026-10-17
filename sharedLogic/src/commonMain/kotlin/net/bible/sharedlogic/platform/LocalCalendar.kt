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

package net.bible.sharedlogic.platform

/**
 * Local-time-zone calendar arithmetic as a platform port. The Android implementation is the `java.util.Calendar` /
 * `SimpleDateFormat` code the reading plans used before, so DST behaviour (a missing local midnight, 23 and 25
 * hour days) stays exactly as it was.
 */
interface LocalCalendar {
    /** Epoch ms of local midnight today. */
    fun startOfToday(): Long

    /** Epoch ms of local midnight of the day containing [epochMs]. */
    fun startOfDay(epochMs: Long): Long

    /** [epochMs] plus [days] calendar days: the local wall-clock time is kept across DST changes. */
    fun addDays(epochMs: Long, days: Int): Long

    /** The current year in the local time zone. */
    fun currentYear(): Int

    /** [epochMs] as a date in the platform's default date format and locale. */
    fun formatDefaultDate(epochMs: Long): String

    /**
     * Parses text like `Feb-1/2026` (`MMM-d/yyyy`), first with US month names, then with the default locale's.
     * Throws the platform's parse exception (`java.text.ParseException` on Android) when neither accepts it.
     */
    fun parseMonthDayYear(text: String): Long
}
