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

data class DaySlot(val weekIndex: Int, val dayIndex: Int, val dayTimestamp: Long)

data class MonthLabel(val weekIndex: Int, val name: String)

data class CalendarSkeleton(
    val slots: List<DaySlot>,
    val monthLabels: List<MonthLabel>,
    val weeks: Int,
    val dayOfWeekLabels: List<String>,
)

data class HeatmapCell(val weekIndex: Int, val dayIndex: Int, val dayTimestamp: Long, val count: Int, val level: Int)

data class CalendarHeatmap(
    val cells: List<HeatmapCell>,
    val monthLabels: List<MonthLabel>,
    val weeks: Int,
    val dayOfWeekLabels: List<String>,
)

data class ReadingSummary(val chaptersRead: Int, val activeDays: Int, val overallPermille: Int, val overallPercent: Float)

data class BookHeat(
    val bookId: String,
    val shortName: String,
    val isNT: Boolean,
    val readPercent: Float,
    val isComplete: Boolean,
    val hasTarget: Boolean = false,
)

data class ChapterHeat(val chapter: Int, val count: Int, val level: Int, val hasTarget: Boolean = false)

data class ChapterDetail(
    val bookId: String,
    val title: String,
    val chapters: List<ChapterHeat>,
    val maxCount: Int,
    val countScaleSteps: List<Int>,
)

data class ReadHistoryEntry(val id: String, val bookId: String, val chapter: Int, val readAt: Long, val bookInitials: String)

enum class ReadingTab { READING, MEMORIZE }

data class ReadingProgressModel(
    val tab: ReadingTab,
    val summary: ReadingSummary,
    val otBooks: List<BookHeat>,
    val ntBooks: List<BookHeat>,
    val bookPercentScaleMax: Float,
    val bookPercentScaleSteps: List<Int>,
    val chapterDetail: ChapterDetail?,
    val calendar: CalendarHeatmap,
    val cycle: Int,
    val latestCycle: Int,
) {
    val canPrevCycle get() = cycle > 1
    val canNextCycle get() = cycle < latestCycle
    val showNewCycle get() = cycle >= latestCycle
}
