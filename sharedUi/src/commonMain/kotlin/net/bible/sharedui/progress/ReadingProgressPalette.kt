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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.LocalAbColors
import net.bible.sharedui.theme.LocalDisplayColorMode

/**
 * Compose [Color] palette for reading-progress heatmaps (chapter/book heat maps, memorization
 * heatmap, calendar heatmap, target-percentage dot).
 *
 * **Palette B** (maintainer decision 2026-07-28, `docs/superpowers/specs/
 * 2026-07-28-compose-palette-b-theme-neutrals-design.md`): the *heat ramps* are classic's exactly —
 * same constants, same blend math as `ReadingProgressColors`/`CalendarHeatmapView`
 * (`app/src/main/java/net/bible/android/view/activity/progress/`) — but every **no-data** cell and
 * every cell's **text** colour come from the Material 3 theme instead of classic's fixed greys.
 * Classic painted `#E8E8E8`/`#EBEDF0` empties, which read as near-white patches on a dark theme.
 * This is a deliberate divergence from classic, which is left untouched as the flag-OFF fallback.
 *
 * Heat-ramp helpers read [LocalDisplayColorMode] so they degrade for e-ink: colours stay as designed
 * in `NORMAL`/`COLOR_EINK`, and are converted to grayscale in `BW` (`accentArgbFor`). Blends are
 * computed with Compose's [lerp] on the two un-degraded anchor colours, and the *result* of the blend
 * is then degraded — never the anchors individually — so a mid-blend hue is preserved faithfully
 * before graying. The theme-derived neutrals need no such wrapping: `AbTheme` greyscales the entire
 * `ColorScheme` in `BW` **and** `COLOR_EINK`.
 */

// Memorization heatmap (green scale), levels 1..4; level 0 is the theme neutral.
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

// Calendar heatmap (GitHub-style greens), levels 1..4; level 0 is the theme neutral.
private val CALENDAR_LEVEL_COLORS = intArrayOf(
    0xFF9BE9A8.toInt(),
    0xFF40C463.toInt(),
    0xFF30A14E.toInt(),
    0xFF216E39.toInt(),
)

/**
 * The background colour of a heat-map cell together with the content (text) colour that belongs
 * with it. The two are chosen as a pair because an empty cell's content colour comes from the theme
 * (`onSurfaceVariant`) while a coloured cell's comes from [textColorForBackground] — it is not
 * derivable from the background alone.
 */
data class HeatColors(val background: Color, val content: Color)

/** Neutral "no activity" heat-map cell colour: the theme's `surfaceVariant`. */
@Composable
private fun colorEmpty(): Color =
    if (isPureMonochrome()) LocalAbColors.current.monoDisabled else MaterialTheme.colorScheme.surfaceVariant

/** [HeatColors] for a no-data cell: theme neutral background, theme content colour. */
@Composable
private fun emptyHeatColors(): HeatColors =
    HeatColors(colorEmpty(), MaterialTheme.colorScheme.onSurfaceVariant)

/** Pure monochrome collapses ramps to partial/full; exact values remain in cell labels and details. */
@Composable
private fun monoHeatColors(full: Boolean): HeatColors =
    if (full) HeatColors(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surface)
    else HeatColors(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)

/** [HeatColors] for a coloured (non-empty) cell: classic's luminance rule picks the text colour. */
private fun heatColors(background: Color): HeatColors =
    HeatColors(background, textColorForBackground(background))

/**
 * Heat colours for a chapter button. The ramp mirrors `ReadingProgressColors.countToHeatColor`:
 * 3 fixed anchors — pale yellow at 1, orange at [ReadingProgressScale.HEAT_MID_COUNT], deep red at
 * `max(maxCount, 10)`. The colours at 1 and `HEAT_MID_COUNT` are always identical regardless of the
 * chosen max. `count == 0` yields the theme neutral (palette B).
 */
@Composable
fun countHeatColors(count: Int, maxCount: Int): HeatColors {
    if (count == 0) return emptyHeatColors()
    if (isPureMonochrome()) return monoHeatColors(count >= maxCount.coerceAtLeast(10))
    val mode = LocalDisplayColorMode.current
    val effectiveMax = maxCount.coerceAtLeast(10)
    val midCount = ReadingProgressScale.HEAT_MID_COUNT
    val blended = if (count <= midCount) {
        val ratio = (count - 1).toFloat() / (midCount - 1).coerceAtLeast(1)
        lerp(Color(COLOR_HEAT_MIN), Color(COLOR_HEAT_MID), ratio.coerceIn(0f, 1f))
    } else {
        val ratio = (count - midCount).toFloat() / (effectiveMax - midCount).coerceAtLeast(1)
        lerp(Color(COLOR_HEAT_MID), Color(COLOR_HEAT_MAX), ratio.coerceIn(0f, 1f))
    }
    return heatColors(Color(accentArgbFor(blended.toArgb(), mode)))
}

/**
 * Heat colours for a book button. The ramp mirrors `ReadingProgressColors.countBookProgressToColor`.
 * [readPercent] = totalReads / totalChapters (1.0 = 100%). Light blue -> dark blue at 100% -> red at
 * [effectiveMaxPercent] * 100%. A book with nothing read yields the theme neutral (palette B).
 */
@Composable
fun bookProgressColors(readPercent: Float, effectiveMaxPercent: Float): HeatColors {
    if (readPercent <= 0f) return emptyHeatColors()
    if (isPureMonochrome()) return monoHeatColors(readPercent >= 1f)
    val mode = LocalDisplayColorMode.current
    val blended = if (readPercent <= 1.0f) {
        lerp(Color(COLOR_COUNT_BOOK_BLUE_LOW), Color(COLOR_COUNT_BOOK_BLUE_HIGH), readPercent)
    } else {
        val ratio = ((readPercent - 1.0f) / (effectiveMaxPercent - 1.0f)).coerceIn(0f, 1f)
        lerp(Color(COLOR_COUNT_BOOK_BLUE_HIGH), Color(COLOR_COUNT_BOOK_RED), ratio)
    }
    return heatColors(Color(accentArgbFor(blended.toArgb(), mode)))
}

/**
 * Memorization heatmap colours for a 0..4 level (see [ReadingProgressScale.memorizationLevel]):
 * level 0 is the theme neutral (palette B), levels 1..4 are classic's low/medium/high/full greens.
 */
@Composable
fun memorizationColors(level: Int): HeatColors {
    if (level <= 0) return emptyHeatColors()
    if (isPureMonochrome()) return monoHeatColors(level >= 4)
    val mode = LocalDisplayColorMode.current
    val base = when (level) {
        1 -> COLOR_MEM_LOW
        2 -> COLOR_MEM_MEDIUM
        3 -> COLOR_MEM_HIGH
        else -> COLOR_MEM_FULL
    }
    return heatColors(Color(accentArgbFor(base, mode)))
}

/**
 * Calendar heatmap colour for a 0..4 level (see [ReadingProgressScale.heatLevel]): level 0 is the
 * theme neutral (palette B), levels 1..4 are classic's GitHub-style green scale. Calendar cells carry
 * no text, so this returns a bare [Color] rather than [HeatColors].
 */
@Composable
fun calendarLevelColor(level: Int): Color {
    if (level <= 0) return colorEmpty()
    if (isPureMonochrome()) return monoHeatColors(level >= 4).background
    val mode = LocalDisplayColorMode.current
    val base = CALENDAR_LEVEL_COLORS[(level - 1).coerceAtMost(CALENDAR_LEVEL_COLORS.lastIndex)]
    return Color(accentArgbFor(base, mode))
}

/** Marker dot colour for a reading-plan/target percentage overlay. */
@Composable
fun targetDot(): Color {
    if (isPureMonochrome()) return MaterialTheme.colorScheme.onSurface
    val mode = LocalDisplayColorMode.current
    return Color(accentArgbFor(COLOR_TARGET_DOT, mode))
}

/**
 * White for dark backgrounds, dark grey for light ones, using WCAG relative luminance so text stays
 * readable over any heat-ramp colour. Mirrors `ReadingProgressColors.textColorForBackground`. Applies
 * to *coloured* cells only — a no-data cell takes `onSurfaceVariant` from the theme instead (see
 * [HeatColors]). Not `@Composable` — it is a pure function of an already-resolved (e-ink-aware)
 * background.
 */
fun textColorForBackground(bg: Color): Color =
    if (bg.luminance() < 0.45f) Color.White else Color.DarkGray
