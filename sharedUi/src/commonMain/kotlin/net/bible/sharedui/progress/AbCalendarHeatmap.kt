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

package net.bible.sharedui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.progress.CalendarHeatmap

private val CellPadding = 2.dp
private val LabelWidth = 24.dp
private val HeaderHeight = 16.dp
private val CellCorner = 2.dp
private const val DAYS_IN_WEEK = 7

/**
 * A GitHub-style calendar heatmap: one column per week, one row per day-of-week, cells coloured
 * by [net.bible.sharedui.progress.calendarLevelColor], whose level 0 is the theme's neutral.
 * General-purpose — knows nothing about reading progress beyond the [CalendarHeatmap] shape it
 * is handed. Mirrors the classic
 * `CalendarHeatmapView` (`app/src/main/java/net/bible/android/view/activity/progress/
 * CalendarHeatmapView.kt`) for cell sizing/spacing/label geometry.
 *
 * Horizontally scrollable, auto-scrolled to the most recent week (the right edge) on first
 * composition of a given [heatmap] — matches the classic `fullScroll(FOCUS_RIGHT)` behaviour.
 * Tapping a cell invokes [onDayClick] with that day's timestamp, but only for cells with
 * `count > 0` (empty days are not clickable, matching the classic view).
 */
@Composable
fun AbCalendarHeatmap(
    heatmap: CalendarHeatmap,
    modifier: Modifier = Modifier,
    cellDp: Dp = 14.dp,
    onDayClick: (dayTimestamp: Long) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(heatmap.weeks) { scrollState.scrollTo(scrollState.maxValue) }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val levelColors = (0..4).map { calendarLevelColor(it) }

    val step = cellDp + CellPadding
    val totalWidth = LabelWidth + step * heatmap.weeks + CellPadding
    val totalHeight = HeaderHeight + step * DAYS_IN_WEEK + CellPadding

    Canvas(
        modifier = modifier
            .horizontalScroll(scrollState)
            .size(width = totalWidth, height = totalHeight)
            .pointerInput(heatmap, cellDp) {
                val stepPx = step.toPx()
                val labelWidthPx = LabelWidth.toPx()
                val headerHeightPx = HeaderHeight.toPx()
                detectTapGestures { offset ->
                    if (offset.x < labelWidthPx || offset.y < headerHeightPx) return@detectTapGestures
                    val week = ((offset.x - labelWidthPx) / stepPx).toInt()
                    val day = ((offset.y - headerHeightPx) / stepPx).toInt()
                    val cell = heatmap.cells.firstOrNull { it.weekIndex == week && it.dayIndex == day }
                    if (cell != null && cell.count > 0) onDayClick(cell.dayTimestamp)
                }
            },
    ) {
        val stepPx = step.toPx()
        val labelWidthPx = LabelWidth.toPx()
        val headerHeightPx = HeaderHeight.toPx()
        val cellPx = cellDp.toPx()
        val cornerPx = CellCorner.toPx()
        val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

        heatmap.dayOfWeekLabels.forEachIndexed { day, label ->
            if (label.isNotEmpty()) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(0f, headerHeightPx + day * stepPx),
                    style = labelStyle,
                )
            }
        }

        heatmap.monthLabels.forEach { month ->
            drawText(
                textMeasurer = textMeasurer,
                text = month.name,
                topLeft = Offset(labelWidthPx + month.weekIndex * stepPx, 0f),
                style = labelStyle,
            )
        }

        heatmap.cells.forEach { cell ->
            val x = labelWidthPx + cell.weekIndex * stepPx
            val y = headerHeightPx + cell.dayIndex * stepPx
            drawRoundRect(
                color = levelColors[cell.level.coerceIn(0, levelColors.lastIndex)],
                topLeft = Offset(x, y),
                size = Size(cellPx, cellPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
            )
        }
    }
}
