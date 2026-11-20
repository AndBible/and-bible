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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Zones and instants for tests that do not need a platform time zone database. */
internal object WidgetTestTime {
    private const val H = 3_600_000L

    /** Days since 1970-01-01 of a civil date (Howard Hinnant's algorithm). */
    fun epochDay(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun utc(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long = epochDay(y, m, d) * 24 * H + h * H + min * 60_000L

    private fun lastSunday(y: Int, m: Int): Long {
        val lastDay = if (m == 3) 31 else 31 // March and October both have 31 days
        val dow = (((epochDay(y, m, lastDay) + 4) % 7 + 7) % 7).toInt() // 0 = Sunday (1970-01-01 was Thursday)
        return epochDay(y, m, lastDay - dow)
    }

    /** EU rules: [std] hours from UTC, +1 h between 01:00 UTC on the last Sunday of March and of October. */
    fun euZone(stdHours: Int) = LocalZone { t ->
        val y = 1970 + (t / (365.2425 * 24 * H)).toInt()
        val start = lastSunday(y, 3) * 24 * H + H
        val end = lastSunday(y, 10) * 24 * H + H
        (stdHours + if (t in start until end) 1 else 0) * H
    }

    val helsinki = euZone(2)
    val minus5 = LocalZone { -5 * H }
}

class VerseSelectorTest {
    private val hel = WidgetTestTime.helsinki
    private val daily = VerseChangeFrequency.DAILY
    private val threeH = VerseChangeFrequency.EVERY_3_HOURS
    private fun at(zone: LocalZone, y: Int, m: Int, d: Int, h: Int, min: Int = 0, f: VerseChangeFrequency = daily) =
        VerseSelector.periodIndex(WidgetTestTime.utc(y, m, d, h, min) - zone.offsetMsAt(WidgetTestTime.utc(y, m, d, h, min)), zone, f)

    @Test fun sameDayGivesSamePeriod() {
        assertEquals(at(hel, 2026, 11, 20, 8), at(hel, 2026, 11, 20, 23, 59))
    }

    @Test fun midnightAdvancesPeriodByOne() {
        assertEquals(at(hel, 2026, 11, 20, 23, 59) + 1, at(hel, 2026, 11, 21, 0, 0))
    }

    @Test fun dstSpringForwardDayIsOnePeriod() {
        assertEquals(at(hel, 2026, 3, 29, 0, 30), at(hel, 2026, 3, 29, 23, 30))
        assertEquals(at(hel, 2026, 3, 29, 23, 30) + 1, at(hel, 2026, 3, 30, 0, 30))
    }

    @Test fun dstFallBackDayIsOnePeriod() {
        assertEquals(at(hel, 2026, 10, 25, 0, 30), at(hel, 2026, 10, 25, 23, 30))
    }

    @Test fun timeZoneChangeUsesLocalDate() {
        val instant = WidgetTestTime.utc(2026, 11, 20, 23, 0)
        val helDay = VerseSelector.periodIndex(instant, hel, daily)
        val nyDay = VerseSelector.periodIndex(instant, WidgetTestTime.minus5, daily)
        assertEquals(WidgetTestTime.epochDay(2026, 11, 21), helDay)
        assertEquals(WidgetTestTime.epochDay(2026, 11, 20), nyDay)
    }

    @Test fun everyThreeHoursHasEightPeriodsPerDay() {
        val z = WidgetTestTime.minus5
        assertEquals(at(z, 2026, 11, 20, 0, 0, threeH), at(z, 2026, 11, 20, 2, 59, threeH))
        assertEquals(at(z, 2026, 11, 20, 0, 0, threeH) + 1, at(z, 2026, 11, 20, 3, 0, threeH))
        assertEquals(at(z, 2026, 11, 20, 0, 0, threeH) + 7, at(z, 2026, 11, 20, 23, 0, threeH))
        assertEquals(at(z, 2026, 11, 20, 0, 0, threeH) + 8, at(z, 2026, 11, 21, 0, 0, threeH))
    }

    @Test fun onShuffleIsAlwaysZero() {
        assertEquals(0L, VerseSelector.periodIndex(WidgetTestTime.utc(2026, 11, 20, 5), hel, VerseChangeFrequency.ON_SHUFFLE))
    }

    @Test fun nextPeriodStartIsNextLocalMidnight() {
        val now = WidgetTestTime.utc(2026, 11, 20, 10) // Helsinki 12:00
        // Helsinki midnight 21 Nov = 22:00 UTC on 20 Nov
        assertEquals(WidgetTestTime.utc(2026, 11, 20, 22), VerseSelector.nextPeriodStart(now, hel, daily))
    }

    @Test fun nextPeriodStartAcrossSpringForward() {
        val now = WidgetTestTime.utc(2026, 3, 28, 23) // Helsinki 01:00 on 29 Mar (winter time)
        // The midnight that starts 30 Mar is on summer time (+3): 21:00 UTC on 29 Mar... and 29 Mar's own end.
        assertEquals(WidgetTestTime.utc(2026, 3, 29, 21), VerseSelector.nextPeriodStart(now, hel, daily))
    }

    @Test fun threeHourBoundaryInsideTheSpringForwardGapIsTheJumpInstant() {
        // Helsinki 02:30 on 29 Mar; the 03:00 boundary does not exist, the clock jumps to 04:00 at 01:00 UTC
        val now = WidgetTestTime.utc(2026, 3, 29, 0, 30)
        val next = VerseSelector.nextPeriodStart(now, hel, threeH)
        assertEquals(WidgetTestTime.utc(2026, 3, 29, 1, 0), next)
        assertEquals(VerseSelector.periodIndex(now, hel, threeH) + 1, VerseSelector.periodIndex(next!!, hel, threeH))
    }

    @Test fun threeHourBoundaryInRepeatedHourIsTheFirstOccurrence() {
        // Helsinki 02:30 on 25 Oct (summer time, +3); 03:00 happens twice, the period starts at the first one
        val now = WidgetTestTime.utc(2026, 10, 24, 23, 30)
        assertEquals(WidgetTestTime.utc(2026, 10, 25, 0, 0), VerseSelector.nextPeriodStart(now, hel, threeH))
    }

    @Test fun nextPeriodStartIsNextThreeHourBoundary() {
        val z = WidgetTestTime.minus5
        val now = WidgetTestTime.utc(2026, 11, 20, 10, 30) // local 05:30
        assertEquals(WidgetTestTime.utc(2026, 11, 20, 11), VerseSelector.nextPeriodStart(now, z, threeH)) // local 06:00
    }

    @Test fun nextPeriodStartIsStrictlyInTheFuture() {
        val z = WidgetTestTime.minus5
        val boundary = WidgetTestTime.utc(2026, 11, 20, 11) // exactly local 06:00
        assertEquals(boundary + 3 * 3_600_000L, VerseSelector.nextPeriodStart(boundary, z, threeH))
    }

    @Test fun nextPeriodStartIsNullOnShuffle() {
        assertNull(VerseSelector.nextPeriodStart(0L, hel, VerseChangeFrequency.ON_SHUFFLE))
    }

    @Test fun pickIsDeterministic() {
        assertEquals(VerseSelector.pick(30, 42L, 20_000L, 0), VerseSelector.pick(30, 42L, 20_000L, 0))
    }

    @Test fun pickDiffersBySeed() {
        val differing = (0 until 30).count { VerseSelector.pick(30, 1L, 20_000L + it, 0) != VerseSelector.pick(30, 2L, 20_000L + it, 0) }
        assertTrue(differing >= 20, "differing=$differing")
    }

    @Test fun shuffleChangesPick() {
        val differing = (0 until 30).count { VerseSelector.pick(30, 7L, 20_000L + it, 0) != VerseSelector.pick(30, 7L, 20_000L + it, 1) }
        assertTrue(differing >= 25, "differing=$differing")
    }

    @Test fun pickCoversPool() {
        val counts = IntArray(7)
        for (p in 0 until 700) counts[VerseSelector.pick(7, 99L, 20_000L + p, 0)!!]++
        counts.forEachIndexed { i, c -> assertTrue(c in 70..130, "index $i hit $c times") }
    }

    @Test fun emptyPoolGivesNull() {
        assertNull(VerseSelector.pick(0, 1L, 1L, 0))
    }

    @Test fun pickStaysInRange() {
        for (p in 0 until 1000) assertTrue(VerseSelector.pick(13, -5L, p.toLong(), p % 4)!! in 0 until 13)
        assertNotEquals(null, VerseSelector.pick(1, 1L, 1L, 0))
    }
}
