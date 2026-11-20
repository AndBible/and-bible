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
        for (id in listOf("Europe/Helsinki", "America/New_York")) {
            val z = zone(id)
            for (f in listOf(VerseChangeFrequency.DAILY, VerseChangeFrequency.EVERY_3_HOURS)) {
                // walk through the 2026 spring-forward and fall-back weeks
                var now = ZonedDateTime.of(2026, 3, 27, 5, 0, 0, 0, ZoneId.of(id)).toInstant().toEpochMilli()
                var boundaries = 0
                while (boundaries < 40) {
                    val next = VerseSelector.nextPeriodStart(now, z, f)!!
                    assertTrue(next > now)
                    assertEquals(VerseSelector.periodIndex(now, z, f), VerseSelector.periodIndex(next - 1, z, f), "$id $f before boundary")
                    assertEquals(VerseSelector.periodIndex(now, z, f) + 1, VerseSelector.periodIndex(next, z, f), "$id $f at boundary")
                    now = next
                    boundaries++
                }
            }
        }
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
