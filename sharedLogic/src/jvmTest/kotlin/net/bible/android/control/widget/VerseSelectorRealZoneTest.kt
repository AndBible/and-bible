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
package net.bible.android.control.widget

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [VerseSelector] against the real tz database: the platform adapter is `TimeZone.getOffset`. */
class VerseSelectorRealZoneTest {
    private fun zone(id: String) = TimeZone.getTimeZone(id).let { tz -> LocalZone { tz.getOffset(it).toLong() } }

    @Test fun dailyPeriodIsLocalEpochDayInEveryZone() {
        for (id in listOf("Europe/Helsinki", "America/New_York", "Asia/Kolkata", "Pacific/Auckland")) {
            val z = ZoneId.of(id)
            var t = ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, z).toInstant()
            repeat(400) {
                val expected = t.atZone(z).toLocalDate().toEpochDay()
                assertEquals(expected, VerseSelector.periodIndex(t.toEpochMilli(), zone(id), VerseChangeFrequency.DAILY), "$id $t")
                t = t.plusSeconds(86_400L - 3_600L * (it % 3)) // wander across DST changes
            }
        }
    }

    @Test fun periodChangesExactlyAtNextPeriodStart() {
        // (zone, a day just before one of its 2026 clock changes): spring-forward and fall-back of both zones
        val starts = listOf(
            "Europe/Helsinki" to LocalDate.of(2026, 3, 27), // spring forward 29 Mar
            "Europe/Helsinki" to LocalDate.of(2026, 10, 23), // fall back 25 Oct
            "America/New_York" to LocalDate.of(2026, 3, 6), // spring forward 8 Mar
            "America/New_York" to LocalDate.of(2026, 10, 30), // fall back 1 Nov
        )
        for ((id, day) in starts) {
            val z = zone(id)
            for (f in listOf(VerseChangeFrequency.DAILY, VerseChangeFrequency.EVERY_3_HOURS)) {
                var now = day.atTime(5, 0).atZone(ZoneId.of(id)).toInstant().toEpochMilli()
                // 3 days of DAILY, 3 days of 3-hour slots: both cross the change
                val boundaries = if (f == VerseChangeFrequency.DAILY) 6 else 24
                repeat(boundaries) {
                    val next = VerseSelector.nextPeriodStart(now, z, f)!!
                    assertTrue(next > now)
                    assertEquals(VerseSelector.periodIndex(now, z, f), VerseSelector.periodIndex(next - 1, z, f), "$id $day $f before boundary")
                    assertEquals(VerseSelector.periodIndex(now, z, f) + 1, VerseSelector.periodIndex(next, z, f), "$id $day $f at boundary")
                    now = next
                }
            }
        }
    }

    @Test fun newYorkSpringForwardDayLosesAnHourAndFallBackDayGainsOne() {
        val ny = ZoneId.of("America/New_York")
        val z = zone("America/New_York")
        fun midnight(d: LocalDate) = d.atStartOfDay(ny).toInstant().toEpochMilli()
        // spring forward 8 Mar 2026: 23 h day; one DAILY period spans it
        val springStart = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, ny).toInstant().toEpochMilli()
        assertEquals(midnight(LocalDate.of(2026, 3, 9)), VerseSelector.nextPeriodStart(springStart, z, VerseChangeFrequency.DAILY))
        assertEquals(23 * 3_600_000L - 30 * 60_000L, midnight(LocalDate.of(2026, 3, 9)) - springStart)
        // fall back 1 Nov 2026: 25 h day
        val fallStart = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, ny).toInstant().toEpochMilli()
        assertEquals(midnight(LocalDate.of(2026, 11, 2)), VerseSelector.nextPeriodStart(fallStart, z, VerseChangeFrequency.DAILY))
        assertEquals(25 * 3_600_000L - 30 * 60_000L, midnight(LocalDate.of(2026, 11, 2)) - fallStart)
        // the repeated 01:xx hour stays in one daily period
        val during = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals(VerseSelector.periodIndex(fallStart, z, VerseChangeFrequency.DAILY), VerseSelector.periodIndex(during, z, VerseChangeFrequency.DAILY))
    }

    @Test fun helsinkiSpringForwardDayHasOnePeriodAndNextMidnightIsOnSummerTime() {
        val z = zone("Europe/Helsinki")
        val a = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, ZoneId.of("Europe/Helsinki")).toInstant().toEpochMilli()
        val b = ZonedDateTime.of(2026, 3, 29, 23, 30, 0, 0, ZoneId.of("Europe/Helsinki")).toInstant().toEpochMilli()
        assertEquals(VerseSelector.periodIndex(a, z, VerseChangeFrequency.DAILY), VerseSelector.periodIndex(b, z, VerseChangeFrequency.DAILY))
        val expectedMidnight = LocalDate.of(2026, 3, 30).atStartOfDay(ZoneId.of("Europe/Helsinki")).toInstant().toEpochMilli()
        assertEquals(expectedMidnight, VerseSelector.nextPeriodStart(a, z, VerseChangeFrequency.DAILY))
        // 00:30 local is 22 h 30 min before the next local midnight: the day lost an hour
        assertEquals(22 * 3_600_000L + 30 * 60_000L, expectedMidnight - a)
    }
}
