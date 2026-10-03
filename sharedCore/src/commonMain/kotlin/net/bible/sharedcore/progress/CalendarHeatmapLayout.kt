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

/** Joins per-day counts onto a platform-produced [CalendarSkeleton] and buckets each cell to a heat level. */
object CalendarHeatmapLayout {
    fun assemble(skeleton: CalendarSkeleton, dailyCounts: Map<Long, Int>): CalendarHeatmap {
        val maxCount = dailyCounts.values.maxOrNull() ?: 1
        val cells = skeleton.slots.map { slot ->
            val count = dailyCounts[slot.dayTimestamp] ?: 0
            HeatmapCell(slot.weekIndex, slot.dayIndex, slot.dayTimestamp, count, ReadingProgressScale.heatLevel(count, maxCount))
        }
        return CalendarHeatmap(cells, skeleton.monthLabels, skeleton.weeks, skeleton.dayOfWeekLabels)
    }
}
