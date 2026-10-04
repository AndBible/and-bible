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
package net.bible.sharedcore.readingplan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyReadingListControllerTest {
    private val sample = listOf(DayEntry(1, "Day 1", "Gen 1-2; Matt 1"), DayEntry(2, "Day 2", "Gen 3-4; Matt 2"))

    @Test fun load_populates_days() {
        val c = DailyReadingListController({ sample }, {})
        assertEquals(sample, c.days.value)
        assertNull(c.error.value)
    }

    @Test fun select_forwards_day() {
        var picked = -1
        val c = DailyReadingListController({ sample }, { picked = it })
        c.select(2)
        assertEquals(2, picked)
    }

    @Test fun throwing_load_sets_error() {
        val c = DailyReadingListController({ throw RuntimeException("boom") }, {})
        assertEquals(ReadingPlanError.FAILED, c.error.value)
        c.dismissError()
        assertNull(c.error.value)
    }
}
