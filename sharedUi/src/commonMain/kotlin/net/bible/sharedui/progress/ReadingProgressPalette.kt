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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.theme.LocalDisplayColorMode

/**
 * Compose [Color] palette for reading-progress heatmaps (chapter/book heat maps, memorization
 * heatmap, calendar heatmap, target-percentage dot). Colour constants and blend math mirror
 * the classic `ReadingProgressColors`/`CalendarHeatmapView` (`app/src/main/java/net/bible/android/
 * view/activity/progress/`) exactly, so the Compose and classic UIs render identical colours.
 *
 * Every helper is `@Composable` and reads [LocalDisplayColorMode] so the result degrades for
 * e-ink automatically: colours stay as designed in `NORMAL`/`COLOR_EINK`, and are converted to
 * grayscale in `BW` (`accentArgbFor`). Blends are computed with Compose's [lerp] on the two
 * un-degraded anchor colours, and the *result* of the blend is then degraded — never the
 * anchors individually — so a mid-blend hue is preserved faithfully before graying.
 */

private const val COLOR_EMPTY = 0xFFE8E8E8.toInt()

// Memorization heatmap (green scale).
private const val COLOR_MEM_LOW = 0xFFC6E48B.toInt()
private const val COLOR_MEM_MEDIUM = 0xFF7BC96F.toInt()
private const val COLOR_MEM_HIGH = 0xFF239A3B.toInt()
private const val COLOR_MEM_FULL = 0xFF196127.toInt()

private const val COLOR_TARGET_DOT = 0xFF9C27B0.toInt()

// Count-mode chapter heat map: pale yellow -> fixed orange at HEAT_MID_COUNT -> deep red at max(max,10).
private const val COLOR_HEAT_MIN = 0xFFFFF9C4.toInt()
private const val COLOR_HEAT_MID = 0xFFFF6D00.toInt()
private const val COLOR_HEAT_MAX = 0xFFB71C1C.toInt()

// Count-mode book heat map: light blue -> dark blue at 100% -> red at the current scale max.
private const val COLOR_COUNT_BOOK_BLUE_LOW = 0xFFE3F2FD.toInt()
private const val COLOR_COUNT_BOOK_BLUE_HIGH = 0xFF1565C0.toInt()
private const val COLOR_COUNT_BOOK_RED = 0xFFB71C1C.toInt()

// Calendar heatmap (GitHub-style greens), levels 0..4.
private val CALENDAR_LEVEL_COLORS = intArrayOf(
    0xFFEBEDF0.toInt(),
    0xFF9BE9A8.toInt(),
    0xFF40C463.toInt(),
    0xFF30A14E.toInt(),
    0xFF216E39.toInt(),
)

/** Neutral "no activity" heat-map cell colour, e-ink aware. */
@Composable
fun colorEmpty(): Color {
    val mode = LocalDisplayColorMode.current
    return Color(accentArgbFor(COLOR_EMPTY, mode))
}

/**
 * Heat colour for a chapter button. Mirrors `ReadingProgressColors.countToHeatColor`: 3 fixed
 * anchors — pale yellow at 1, orange at [ReadingProgressScale.HEAT_MID_COUNT], deep red at
 * `max(maxCount, 10)`. The colours at 1 and `HEAT_MID_COUNT` are always identical regardless of
 * the chosen max.
 */
@Composable
fun countHeatColor(count: Int, maxCount: Int): Color {
    val mode = LocalDisplayColorMode.current
    if (count == 0) return Color(accentArgbFor(COLOR_EMPTY, mode))
    val effectiveMax = maxCount.coerceAtLeast(10)
    val midCount = ReadingProgressScale.HEAT_MID_COUNT
    val blended = if (count <= midCount) {
        val ratio = (count - 1).toFloat() / (midCount - 1).coerceAtLeast(1)
        lerp(Color(COLOR_HEAT_MIN), Color(COLOR_HEAT_MID), ratio.coerceIn(0f, 1f))
    } else {
        val ratio = (count - midCount).toFloat() / (effectiveMax - midCount).coerceAtLeast(1)
        lerp(Color(COLOR_HEAT_MID), Color(COLOR_HEAT_MAX), ratio.coerceIn(0f, 1f))
    }
    return Color(accentArgbFor(blended.toArgb(), mode))
}

/**
 * Colour for a book button. Mirrors `ReadingProgressColors.countBookProgressToColor`.
 * [readPercent] = totalReads / totalChapters (1.0 = 100%). Light blue -> dark blue at 100% ->
 * red at [effectiveMaxPercent] * 100%.
 */
@Composable
fun bookProgressColor(readPercent: Float, effectiveMaxPercent: Float): Color {
    val mode = LocalDisplayColorMode.current
    if (readPercent <= 0f) return Color(accentArgbFor(COLOR_EMPTY, mode))
    val blended = if (readPercent <= 1.0f) {
        lerp(Color(COLOR_COUNT_BOOK_BLUE_LOW), Color(COLOR_COUNT_BOOK_BLUE_HIGH), readPercent)
    } else {
        val ratio = ((readPercent - 1.0f) / (effectiveMaxPercent - 1.0f)).coerceIn(0f, 1f)
        lerp(Color(COLOR_COUNT_BOOK_BLUE_HIGH), Color(COLOR_COUNT_BOOK_RED), ratio)
    }
    return Color(accentArgbFor(blended.toArgb(), mode))
}

/**
 * Memorization heatmap colour for a 0..4 level (see [ReadingProgressScale.memorizationLevel]):
 * empty / low / medium / high / full green.
 */
@Composable
fun memorizationColor(level: Int): Color {
    val mode = LocalDisplayColorMode.current
    val base = when (level) {
        0 -> COLOR_EMPTY
        1 -> COLOR_MEM_LOW
        2 -> COLOR_MEM_MEDIUM
        3 -> COLOR_MEM_HIGH
        else -> COLOR_MEM_FULL
    }
    return Color(accentArgbFor(base, mode))
}

/**
 * Calendar heatmap colour for a 0..4 level (see [ReadingProgressScale.heatLevel]): the GitHub-style
 * green scale.
 */
@Composable
fun calendarLevelColor(level: Int): Color {
    val mode = LocalDisplayColorMode.current
    val base = CALENDAR_LEVEL_COLORS[level.coerceIn(0, CALENDAR_LEVEL_COLORS.size - 1)]
    return Color(accentArgbFor(base, mode))
}

/** Marker dot colour for a reading-plan/target percentage overlay. */
@Composable
fun targetDot(): Color {
    val mode = LocalDisplayColorMode.current
    return Color(accentArgbFor(COLOR_TARGET_DOT, mode))
}

/**
 * White for dark backgrounds, dark grey for light ones, using WCAG relative luminance so text
 * stays readable over any heat-map colour. Mirrors `ReadingProgressColors.textColorForBackground`.
 * Not `@Composable` — it is a pure function of an already-resolved (e-ink-aware) background.
 */
fun textColorForBackground(bg: Color): Color =
    if (bg.luminance() < 0.45f) Color.White else Color.DarkGray
