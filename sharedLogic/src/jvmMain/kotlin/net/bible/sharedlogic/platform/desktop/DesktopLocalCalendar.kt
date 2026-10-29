/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedlogic.platform.desktop

import net.bible.sharedlogic.platform.LocalCalendar
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * [LocalCalendar] on `java.time`: Gregorian in the current default time zone, with the locale's first weekday. Day
 * arithmetic keeps the local wall-clock time across DST changes; a local time in a spring-forward gap moves forward by
 * the gap, a time in a fall-back overlap takes the earlier offset (what `GregorianCalendar` does on Android).
 * [withLocalYmd] is lenient like `Calendar.set`: 31 February becomes 3 March.
 */
class DesktopLocalCalendar(
    private val localeProvider: () -> Locale = { Locale.getDefault() },
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : LocalCalendar {
    private fun zoned(epochMs: Long): ZonedDateTime = Instant.ofEpochMilli(epochMs).atZone(zoneProvider())
    private fun ZonedDateTime.ms(): Long = toInstant().toEpochMilli()
    private fun LocalDate.midnight(): Long = atStartOfDay(zoneProvider()).ms()

    override fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

    override fun startOfDay(epochMs: Long): Long = zoned(epochMs).toLocalDate().midnight()

    override fun addDays(epochMs: Long, days: Int): Long = zoned(epochMs).plusDays(days.toLong()).ms()

    override fun localYmd(epochMs: Long): Triple<Int, Int, Int> =
        zoned(epochMs).let { Triple(it.year, it.monthValue, it.dayOfMonth) }

    override fun withLocalYmd(epochMs: Long, year: Int, month1to12: Int, day: Int): Long {
        val time = zoned(epochMs).toLocalTime()
        val date = LocalDate.of(year, 1, 1).plusMonths(month1to12 - 1L).plusDays(day - 1L)
        return ZonedDateTime.of(LocalDateTime.of(date, time), zoneProvider()).ms()
    }

    override fun currentYear(): Int = LocalDate.now(zoneProvider()).year

    override fun formatDefaultDate(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(localeProvider()).format(zoned(epochMs))

    /** `MMM-d/yyyy` with US month names first, then the current locale's; [ParseException] when neither accepts it. */
    override fun parseMonthDayYear(text: String): Long {
        fun parse(locale: Locale) = SimpleDateFormat("MMM-d/yyyy", locale)
            .apply { timeZone = java.util.TimeZone.getTimeZone(zoneProvider()) }
            .parse(text)
        return try {
            parse(Locale.US).time
        } catch (e: ParseException) {
            parse(localeProvider()).time
        }
    }

    override fun startOfLocalDay(epochMs: Long): Long = startOfDay(epochMs)

    override fun plusLocalDays(epochMs: Long, days: Int): Long = addDays(epochMs, days)

    override fun plusWeeks(epochMs: Long, weeks: Int): Long = zoned(epochMs).plusWeeks(weeks.toLong()).ms()

    override fun startOfLocalWeek(epochMs: Long): Long {
        val first = WeekFields.of(localeProvider()).firstDayOfWeek
        return zoned(epochMs).toLocalDate().with(TemporalAdjusters.previousOrSame(first)).midnight()
    }

    /** Month 0-11. */
    override fun localMonth(epochMs: Long): Int = zoned(epochMs).monthValue - 1

    override fun shortMonthName(epochMs: Long): String =
        DateTimeFormatter.ofPattern("LLL", localeProvider()).format(zoned(epochMs))
}
