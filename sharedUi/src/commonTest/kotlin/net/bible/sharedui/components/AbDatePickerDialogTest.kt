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

package net.bible.sharedui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 30b: [ymdToUtcMidnightMillis]/[utcMidnightMillisToYmd] round-trip pure conversion, run on
 * every `:sharedUi` target (this is `commonTest`, so it type-checks and runs on the iOS target too,
 * not just the JVM). The one thing worth pinning explicitly: a date in the second half of the month
 * near a UTC+ boundary must come back unchanged -- the classic bug this pair is designed to avoid is
 * converting a real LOCAL instant to/from UTC millis (which drifts a day in UTC+ zones); this
 * encoding never touches an instant at all, only (year, month, day) <-> a day count.
 */
class AbDatePickerDialogTest {
    @Test fun roundTrips_epoch() {
        val millis = ymdToUtcMidnightMillis(1970, 1, 1)
        assertEquals(0L, millis)
        assertEquals(Triple(1970, 1, 1), utcMidnightMillisToYmd(millis))
    }

    @Test fun roundTrips_today() {
        val (y, m, d) = Triple(2026, 9, 25)
        val millis = ymdToUtcMidnightMillis(y, m, d)
        assertEquals(Triple(y, m, d), utcMidnightMillisToYmd(millis))
    }

    @Test fun roundTrips_lastDayOfMonth_utcPlusBoundary() {
        // The date this pair exists to protect: 2026-09-30 23:00 in a real UTC+ instant would be
        // 2026-10-01 in UTC -- but this pair never converts an instant, only a (y, m, d) triple, so
        // the round trip is exact regardless of what zone the caller's "now" is in.
        val (y, m, d) = Triple(2026, 9, 30)
        val millis = ymdToUtcMidnightMillis(y, m, d)
        assertEquals(Triple(y, m, d), utcMidnightMillisToYmd(millis))
    }

    @Test fun roundTrips_leapDay() {
        val (y, m, d) = Triple(2024, 2, 29)
        val millis = ymdToUtcMidnightMillis(y, m, d)
        assertEquals(Triple(y, m, d), utcMidnightMillisToYmd(millis))
    }

    @Test fun roundTrips_yearBoundary() {
        val (y, m, d) = Triple(2025, 12, 31)
        val millis = ymdToUtcMidnightMillis(y, m, d)
        assertEquals(Triple(y, m, d), utcMidnightMillisToYmd(millis))
        val next = millis + 24 * 60 * 60 * 1000L
        assertEquals(Triple(2026, 1, 1), utcMidnightMillisToYmd(next))
    }

    @Test fun roundTrips_beforeEpoch() {
        val (y, m, d) = Triple(1900, 3, 15)
        val millis = ymdToUtcMidnightMillis(y, m, d)
        assertEquals(Triple(y, m, d), utcMidnightMillisToYmd(millis))
    }
}
