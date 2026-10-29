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

import java.text.ParseException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopLocalCalendarTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val newYork = ZoneId.of("America/New_York")
    private fun cal(zone: ZoneId = helsinki, tag: String = "fi-FI") =
        DesktopLocalCalendar({ Locale.forLanguageTag(tag) }, { zone })
    private fun ms(zone: ZoneId, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun dstChangeDayInHelsinkiIsOnly23HoursLongButStartOfDayStillMatches() {
        // 2026-03-29: clocks go 03:00 -> 04:00 in Helsinki.
        val noon = ms(helsinki, 2026, 3, 29, 12)
        assertEquals(ms(helsinki, 2026, 3, 29), cal().startOfDay(noon))
        assertEquals(23L * 3_600_000, ms(helsinki, 2026, 3, 30) - ms(helsinki, 2026, 3, 29))
        assertEquals(ms(helsinki, 2026, 3, 30), cal().addDays(ms(helsinki, 2026, 3, 29), 1))
    }

    @Test fun addDaysKeepsWallClockHourAcrossSpringForwardAndFallBack() {
        assertEquals(ms(helsinki, 2026, 3, 30, 10), cal().addDays(ms(helsinki, 2026, 3, 28, 10), 2))
        assertEquals(ms(helsinki, 2026, 10, 27, 10), cal().addDays(ms(helsinki, 2026, 10, 24, 10), 3)) // fall back 2026-10-25
        assertEquals(ms(newYork, 2026, 3, 9, 8), cal(newYork).plusWeeks(ms(newYork, 2026, 3, 2, 8), 1))
        assertEquals(ms(helsinki, 2026, 3, 30, 10), cal().plusLocalDays(ms(helsinki, 2026, 3, 29, 10), 1))
    }

    @Test fun ymdRoundTripKeepsTimeOfDayAndMilliseconds() {
        val start = ms(helsinki, 2026, 5, 10, 13, 45) + 123
        val moved = cal().withLocalYmd(start, 2026, 12, 24)
        assertEquals(Triple(2026, 12, 24), cal().localYmd(moved))
        assertEquals(ms(helsinki, 2026, 12, 24, 13, 45) + 123, moved)
    }

    @Test fun withLocalYmdIntoSpringForwardGapMovesForwardLikeGregorianCalendar() {
        val start = ms(newYork, 2026, 3, 1, 2, 30)
        assertEquals(ms(newYork, 2026, 3, 8, 3, 30), cal(newYork, "en-US").withLocalYmd(start, 2026, 3, 8))
    }

    @Test fun withLocalYmdIsLenientForImpossibleDays() {
        val start = ms(helsinki, 2026, 1, 1, 9)
        assertEquals(Triple(2026, 3, 3), cal().localYmd(cal().withLocalYmd(start, 2026, 2, 31)))
    }

    @Test fun parseMonthDayYearIsLocalMidnightAndGarbageThrows() {
        assertEquals(ms(helsinki, 2026, 2, 1), cal().parseMonthDayYear("Feb-1/2026"))
        assertFailsWith<ParseException> { cal().parseMonthDayYear("not a date") }
    }

    @Test fun parseMonthDayYearFallsBackToLocaleMonthNames() {
        val finnishFebruary = java.text.DateFormatSymbols(Locale.forLanguageTag("fi-FI")).shortMonths[1] // JDK-version dependent spelling, parsed by SimpleDateFormat
        assertTrue(finnishFebruary != "Feb")
        assertEquals(ms(helsinki, 2026, 2, 1), cal().parseMonthDayYear("$finnishFebruary-1/2026"))
    }

    @Test fun weeksStartOnTheLocalesFirstDay() {
        val wednesday = ms(helsinki, 2026, 7, 15, 12)
        assertEquals(ms(helsinki, 2026, 7, 13), cal(tag = "fi-FI").startOfLocalWeek(wednesday)) // Monday
        assertEquals(ms(helsinki, 2026, 7, 12), cal(tag = "en-US").startOfLocalWeek(wednesday)) // Sunday
        assertEquals(ms(helsinki, 2026, 7, 13), cal(tag = "fi-FI").startOfLocalWeek(ms(helsinki, 2026, 7, 13, 0)))
    }

    @Test fun monthIsZeroBasedAndNamed() {
        val july = ms(helsinki, 2026, 7, 15)
        assertEquals(6, cal().localMonth(july))
        assertEquals("Jul", cal(tag = "en-US").shortMonthName(july))
        assertEquals("heinä", cal().shortMonthName(july))
    }

    @Test fun currentYearAndTodayAreConsistent() {
        val c = cal()
        assertTrue(c.currentYear() >= 2026)
        assertEquals(c.startOfDay(System.currentTimeMillis()), c.startOfToday())
        assertTrue(c.formatDefaultDate(ms(helsinki, 2026, 3, 8)).isNotBlank())
    }
}
