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
package net.bible.sharedlogic.platform.ios

import net.bible.sharedlogic.platform.LocalCalendar
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitWeekOfYear
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCalendarUnitYearForWeekOfYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeIntervalSince1970

/**
 * [LocalCalendar] on a Gregorian `NSCalendar` in the current time zone: DST-aware (calendar-day arithmetic keeps the wall-clock time) and
 * with the locale's first weekday. The calendar is read on every call so a changed time zone or locale is seen.
 */
class IosLocalCalendar(
    private val localeProvider: () -> NSLocale = { NSLocale.currentLocale },
) : LocalCalendar {
    /**
     * A fresh Gregorian calendar in the current default time zone with the locale's first weekday. Pinned to
     * Gregorian like Android's `Calendar.getInstance()`, so a Buddhist or Islamic device calendar never makes
     * `currentYear()` 2569.
     */
    private val calendar: NSCalendar
        get() = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
            locale = localeProvider()
            timeZone = NSTimeZone.defaultTimeZone
        }

    private fun date(epochMs: Long) = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
    private fun NSDate.toEpochMs(): Long = (timeIntervalSince1970 * 1000.0).toLong()

    override fun startOfToday(): Long = startOfDay(NSDate().toEpochMs())

    override fun startOfDay(epochMs: Long): Long = calendar.startOfDayForDate(date(epochMs)).toEpochMs()

    override fun addDays(epochMs: Long, days: Int): Long = plusUnit(epochMs, NSCalendarUnitDay, days)

    override fun currentYear(): Int = calendar.component(NSCalendarUnitYear, fromDate = NSDate()).toInt()

    override fun formatDefaultDate(epochMs: Long): String = NSDateFormatter().apply {
        locale = localeProvider()
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterNoStyle
    }.stringFromDate(date(epochMs))

    /**
     * `MMM-d/yyyy` with US month names first, then the current locale's; neither parsing is an [IllegalArgumentException].
     * For the locale, the format-context (`MMM`) and standalone (`LLL`) patterns are tried strictly then leniently, and
     * finally the month name is matched against the locale's own month symbols (`NSDateFormatter` does not always
     * accept an abbreviation that ends in a dot, such as Finnish `helmik.`).
     */
    override fun parseMonthDayYear(text: String): Long {
        val locale = localeProvider()
        fun parse(loc: NSLocale, pattern: String, lenient: Boolean) = NSDateFormatter().apply {
            this.locale = loc
            calendar = this@IosLocalCalendar.calendar
            dateFormat = pattern
            setLenient(lenient)
        }.dateFromString(text)?.toEpochMs()
        parse(NSLocale.localeWithLocaleIdentifier("en_US_POSIX"), "MMM-d/yyyy", false)?.let { return it }
        for (lenient in listOf(false, true)) {
            for (pattern in listOf("MMM-d/yyyy", "LLL-d/yyyy")) parse(locale, pattern, lenient)?.let { return it }
        }
        return parseByMonthSymbols(text, locale) ?: throw IllegalArgumentException("Unable to parse date ($text) as MMM-d/yyyy")
    }

    private fun parseByMonthSymbols(text: String, locale: NSLocale): Long? {
        val match = Regex("""^\s*(.+?)-(\d{1,2})/(\d{4})\s*$""").matchEntire(text) ?: return null
        val (name, day, year) = match.destructured
        fun norm(s: String) = s.trim().trimEnd('.').lowercase()
        val formatter = NSDateFormatter().apply { this.locale = locale }
        val lists = listOf(formatter.shortMonthSymbols, formatter.shortStandaloneMonthSymbols, formatter.monthSymbols, formatter.standaloneMonthSymbols)
        for (symbols in lists) {
            val index = symbols.indexOfFirst { norm(it as String) == norm(name) }
            if (index < 0) continue
            val parts = NSDateComponents().apply {
                this.year = year.toLong(); month = (index + 1).toLong(); this.day = day.toLong()
            }
            return calendar.dateFromComponents(parts)?.toEpochMs()
        }
        return null
    }

    override fun startOfLocalDay(epochMs: Long): Long = startOfDay(epochMs)

    override fun plusLocalDays(epochMs: Long, days: Int): Long = plusUnit(epochMs, NSCalendarUnitDay, days)

    override fun plusWeeks(epochMs: Long, weeks: Int): Long = plusUnit(epochMs, NSCalendarUnitWeekOfYear, weeks)

    override fun startOfLocalWeek(epochMs: Long): Long {
        val cal = calendar
        val parts = cal.components(NSCalendarUnitYearForWeekOfYear or NSCalendarUnitWeekOfYear, fromDate = date(epochMs))
        parts.weekday = cal.firstWeekday.toLong()
        // Unset hour/minute/second components default to zero, so this is local midnight of the week's first day.
        return cal.dateFromComponents(parts)?.toEpochMs() ?: startOfDay(epochMs)
    }

    /** Month 0-11. */
    override fun localMonth(epochMs: Long): Int = calendar.component(NSCalendarUnitMonth, fromDate = date(epochMs)).toInt() - 1

    override fun shortMonthName(epochMs: Long): String = NSDateFormatter().apply {
        locale = localeProvider()
        calendar = this@IosLocalCalendar.calendar
        dateFormat = "LLL"
    }.stringFromDate(date(epochMs))

    private fun plusUnit(epochMs: Long, unit: ULong, amount: Int): Long =
        calendar.dateByAddingUnit(unit, amount.toLong(), toDate = date(epochMs), options = 0u)?.toEpochMs() ?: epochMs
}
