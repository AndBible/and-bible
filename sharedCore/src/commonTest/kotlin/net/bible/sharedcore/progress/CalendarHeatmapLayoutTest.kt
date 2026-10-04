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

package net.bible.sharedcore.progress

import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarHeatmapLayoutTest {
    private fun skeleton() = CalendarSkeleton(
        slots = listOf(
            DaySlot(0, 0, 1000L), DaySlot(0, 1, 2000L), DaySlot(1, 0, 3000L),
        ),
        monthLabels = listOf(MonthLabel(0, "Jan")),
        weeks = 2,
        dayOfWeekLabels = listOf("", "M", "", "W", "", "F", ""),
    )

    @Test fun assemble_joins_counts_and_levels() {
        val heat = CalendarHeatmapLayout.assemble(skeleton(), mapOf(1000L to 4, 3000L to 1))
        // maxCount = 4 -> 4/4=1.0 => level 4 ; 1/4=0.25 => level 1 ; missing => 0
        assertEquals(3, heat.cells.size)
        assertEquals(HeatmapCell(0, 0, 1000L, 4, 4), heat.cells[0])
        assertEquals(HeatmapCell(0, 1, 2000L, 0, 0), heat.cells[1])
        assertEquals(HeatmapCell(1, 0, 3000L, 1, 1), heat.cells[2])
        assertEquals(listOf(MonthLabel(0, "Jan")), heat.monthLabels)
        assertEquals(2, heat.weeks)
    }

    @Test fun assemble_empty_counts_all_level_zero() {
        val heat = CalendarHeatmapLayout.assemble(skeleton(), emptyMap())
        assertEquals(true, heat.cells.all { it.count == 0 && it.level == 0 })
    }
}
