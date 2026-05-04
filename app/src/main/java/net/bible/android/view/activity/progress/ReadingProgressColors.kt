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

package net.bible.android.view.activity.progress

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Color and scale helpers for reading-progress heatmaps.
 *
 * These are pure functions with no Android lifecycle dependencies, so they are unit-testable
 * without Robolectric (Color is a stubbed class in unit-test runs but the arithmetic still
 * works for the scale-step logic which does not actually invoke Color).
 */
object ReadingProgressColors {

    val COLOR_EMPTY = Color.parseColor("#E8E8E8")

    // Memorization heatmap (green scale)
    val COLOR_MEM_LOW = Color.parseColor("#C6E48B")
    val COLOR_MEM_MEDIUM = Color.parseColor("#7BC96F")
    val COLOR_MEM_HIGH = Color.parseColor("#239A3B")
    val COLOR_MEM_FULL = Color.parseColor("#196127")

    val COLOR_TARGET_DOT = Color.parseColor("#9C27B0")

    // Count-mode chapter heat map: amber → fixed orange at HEAT_MID_COUNT → deep red at max(max,10)
    const val HEAT_MID_COUNT = 5
    val COLOR_HEAT_MIN = Color.parseColor("#FFF9C4")
    val COLOR_HEAT_MID = Color.parseColor("#FF6D00")
    val COLOR_HEAT_MAX = Color.parseColor("#B71C1C")

    // Count-mode book heat map: light blue → dark blue at 100 % → red at the current scale max
    val COLOR_COUNT_BOOK_BLUE_LOW = Color.parseColor("#E3F2FD")
    val COLOR_COUNT_BOOK_BLUE_HIGH = Color.parseColor("#1565C0")
    val COLOR_COUNT_BOOK_RED = Color.parseColor("#B71C1C")

    fun memorizationProgressToColor(progress: Float): Int = when {
        progress <= 0f -> COLOR_EMPTY
        progress < 0.25f -> COLOR_MEM_LOW
        progress < 0.50f -> COLOR_MEM_MEDIUM
        progress < 0.75f -> COLOR_MEM_HIGH
        else -> COLOR_MEM_FULL
    }

    /**
     * Returns [Color.WHITE] for dark backgrounds and [Color.DKGRAY] for light ones,
     * using WCAG relative luminance so text stays readable over any heat-map color.
     */
    fun textColorForBackground(bgColor: Int): Int =
        if (ColorUtils.calculateLuminance(bgColor) < 0.45) Color.WHITE else Color.DKGRAY

    /**
     * Heat color for a chapter button.
     * 3 fixed anchors: pale yellow at 1, orange at [HEAT_MID_COUNT], deep red at max(maxCount,10).
     * Colors at 1 and [HEAT_MID_COUNT] are always identical regardless of the chosen max.
     */
    fun countToHeatColor(count: Int, maxCount: Int): Int {
        if (count == 0) return COLOR_EMPTY
        val effectiveMax = maxCount.coerceAtLeast(10)
        return when {
            count <= HEAT_MID_COUNT -> {
                val ratio = (count - 1).toFloat() / (HEAT_MID_COUNT - 1).coerceAtLeast(1)
                ColorUtils.blendARGB(COLOR_HEAT_MIN, COLOR_HEAT_MID, ratio.coerceIn(0f, 1f))
            }
            else -> {
                val ratio = (count - HEAT_MID_COUNT).toFloat() / (effectiveMax - HEAT_MID_COUNT).coerceAtLeast(1)
                ColorUtils.blendARGB(COLOR_HEAT_MID, COLOR_HEAT_MAX, ratio.coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Color for a book button.
     * [readPercent] = totalReads / totalChapters (1.0 = 100 %).
     * Light blue → dark blue at 100 % → red at [effectiveMaxPercent] * 100 %.
     */
    fun countBookProgressToColor(readPercent: Float, effectiveMaxPercent: Float): Int {
        if (readPercent <= 0f) return COLOR_EMPTY
        return when {
            readPercent <= 1.0f -> ColorUtils.blendARGB(COLOR_COUNT_BOOK_BLUE_LOW, COLOR_COUNT_BOOK_BLUE_HIGH, readPercent)
            else -> {
                val ratio = ((readPercent - 1.0f) / (effectiveMaxPercent - 1.0f)).coerceIn(0f, 1f)
                ColorUtils.blendARGB(COLOR_COUNT_BOOK_BLUE_HIGH, COLOR_COUNT_BOOK_RED, ratio)
            }
        }
    }

    /**
     * Round the maximum read percentage up to the next 25 % boundary so the scale lands on
     * clean labels. Below 100 % we always show 0–100.
     */
    fun resolveBookPercentScaleMax(maxReadPercent: Float?): Float {
        val actualMax = maxReadPercent ?: 0f
        if (actualMax <= 1.0f) return 1.0f
        // Rounds 1.33 up to 1.50, etc.
        return ceil(actualMax * 4f) / 4f
    }

    /**
     * Build the list of percent-step labels (25, 50, 75, ..., max) for the heatmap legend.
     * Always uses 25 % increments — matches [resolveBookPercentScaleMax]'s rounding granularity.
     */
    fun buildBookPercentScaleSteps(maxReadPercent: Float): List<Int> {
        val maxPercent = (maxReadPercent * 100).roundToInt().coerceAtLeast(100)
        return (25..maxPercent step 25).toList()
    }
}
