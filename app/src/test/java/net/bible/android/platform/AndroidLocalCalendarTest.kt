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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

/**
 * [AndroidLocalCalendar] against the old inline `java.util.Calendar` / `SimpleDateFormat` expressions the reading-plan
 * code used before the port (Review Focus 2: DST). Every assertion evaluates the old expression in the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidLocalCalendarTest {
    private val calendar = AndroidLocalCalendar()

    private fun inZone(zone: String, locale: Locale? = null, block: () -> Unit) {
        val savedZone = TimeZone.getDefault()
        val savedLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            if (locale != null) Locale.setDefault(locale)
            block()
        } finally {
            TimeZone.setDefault(savedZone)
            Locale.setDefault(savedLocale)
        }
    }

    private fun at(zone: String, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    /** The old truncation of ReadingPlanDates.kt / ReadingPlanControl.currentPlanDay, for the instant [now]. */
    private fun oldTruncate(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun oldAdd(start: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DATE, days)
    }.timeInMillis

    @Test fun startOfTodayMatchesCalendarTruncation() {
        for (zone in listOf("Europe/Helsinki", "America/Sao_Paulo")) inZone(zone) {
            val before = oldTruncate(System.currentTimeMillis())
            val actual = calendar.startOfToday()
            val after = oldTruncate(System.currentTimeMillis())
            // the clock may cross midnight between the reads; the port must equal one of the two bounds
            assert(actual == before || actual == after) { "$zone: $actual not in {$before, $after}" }
            assertEquals(oldTruncate(actual), actual)
        }
    }

    @Test fun addDaysAcrossSpringForwardKeepsWallClock() = inZone("Europe/Helsinki") {
        val start = at("Europe/Helsinki", 2026, 3, 28)
        for (days in listOf(1, 2, -1)) assertEquals(oldAdd(start, days), calendar.addDays(start, days))
        // wall clock kept: the day of the switch is 23 hours long
        assertEquals(23 * 3_600_000L, calendar.addDays(start, 2) - calendar.addDays(start, 1))
    }

    @Test fun addDaysAcrossFallBack() = inZone("Europe/Helsinki") {
        val start = at("Europe/Helsinki", 2026, 10, 24)
        for (days in listOf(1, 2, -1)) assertEquals(oldAdd(start, days), calendar.addDays(start, days))
        assertEquals(25 * 3_600_000L, calendar.addDays(start, 2) - calendar.addDays(start, 1))
    }

    @Test fun addDaysAcrossSaoPauloGap() = inZone("America/Sao_Paulo") {
        val start = at("America/Sao_Paulo", 2018, 11, 3, 12)
        for (days in listOf(1, 2, -3)) assertEquals(oldAdd(start, days), calendar.addDays(start, days))
    }

    /** ReadingPlanControl.getDueDay with the port against the same formula over Calendar values. */
    @Test fun dayNumberAroundDstEqualsOldFormula() {
        val cases = listOf(
            "Europe/Helsinki" to at("Europe/Helsinki", 2026, 3, 28),
            "Europe/Helsinki" to at("Europe/Helsinki", 2026, 10, 24),
            "America/Sao_Paulo" to at("America/Sao_Paulo", 2018, 11, 3),
        )
        for ((zone, start) in cases) inZone(zone) {
            val dayMs = 1000.0 * 60.0 * 60.0 * 24.0
            for (hour in 0 until 72) {
                val now = start + hour * 3_600_000L
                val old = ((oldTruncate(now) - start) / dayMs).roundToLong() + 1
                val new = ((calendar.startOfDay(now) - start) / dayMs).roundToLong() + 1
                assertEquals("$zone hour $hour", old, new)
            }
        }
    }

    @Test fun sinceMidnightGapDay() = inZone("America/Sao_Paulo") {
        // 2018-11-04: clocks jumped 00:00 -> 01:00, local midnight does not exist
        val noon = at("America/Sao_Paulo", 2018, 11, 4, 15)
        assertEquals(oldTruncate(noon), calendar.startOfDay(noon))
        val justAfterGap = at("America/Sao_Paulo", 2018, 11, 4, 1, 30)
        assertEquals(oldTruncate(justAfterGap), calendar.startOfDay(justAfterGap))
    }

    @Test fun currentYearMatchesCalendar() {
        assertEquals(Calendar.getInstance().get(Calendar.YEAR), calendar.currentYear())
    }

    @Test fun parseMonthDayYearUsThenDefault() = inZone("Europe/Helsinki", Locale("fi")) {
        assertEquals(SimpleDateFormat("MMM-d/yyyy", Locale.US).parse("Feb-1/2026")!!.time, calendar.parseMonthDayYear("Feb-1/2026"))

        // an abbreviation only the default (Finnish) locale knows
        val fi = java.text.DateFormatSymbols.getInstance(Locale("fi")).shortMonths[2] // the form SimpleDateFormat parses
        val text = "$fi-1/2026"
        assertThrows(ParseException::class.java) { SimpleDateFormat("MMM-d/yyyy", Locale.US).parse(text) }
        assertEquals(SimpleDateFormat("MMM-d/yyyy", Locale.getDefault()).parse(text)!!.time, calendar.parseMonthDayYear(text))

        assertThrows(ParseException::class.java) { calendar.parseMonthDayYear("Xyz-1/2026") }
    }

    @Test fun formatDefaultDateMatchesDateInstance() {
        val millis = at("Europe/Helsinki", 2026, 3, 29, 12)
        for (locale in listOf(Locale.US, Locale("fi", "FI"), Locale("ar"))) inZone("Europe/Helsinki", locale) {
            assertEquals(SimpleDateFormat.getDateInstance().format(Date(millis)), calendar.formatDefaultDate(millis))
        }
    }
}
