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

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure scale/level math for reading-progress heatmaps (colours themselves live in :sharedUi). */
object ReadingProgressScale {
    const val HEAT_MID_COUNT = 5

    fun resolveBookPercentScaleMax(maxReadPercent: Float?): Float {
        val actualMax = maxReadPercent ?: 0f
        if (actualMax <= 1.0f) return 1.0f
        // Rounds up to the next quarter; an exact quarter (e.g. 1.75) is left unchanged (classic parity).
        return ceil(actualMax * 4f) / 4f
    }

    fun buildBookPercentScaleSteps(maxReadPercent: Float): List<Int> {
        val maxPercent = (maxReadPercent * 100).roundToInt().coerceAtLeast(100)
        return (25..maxPercent step 25).toList()
    }

    fun countScaleSteps(maxCount: Int): List<Int> {
        val effectiveMax = maxCount.coerceAtLeast(10)
        return if (effectiveMax <= 10) {
            (1..effectiveMax).toList()
        } else {
            val n = 10
            val evenly = (0 until n).map { i -> 1 + (i.toLong() * (effectiveMax - 1) / (n - 1)).toInt() }.toSet()
            (evenly + setOf(1, HEAT_MID_COUNT, effectiveMax)).sorted().take(n)
        }
    }

    /** 0..4 heat level from a per-day / per-cell count. Mirrors CalendarHeatmapView.getColorForCount. */
    fun heatLevel(count: Int, maxCount: Int): Int {
        if (count == 0) return 0
        val fraction = count.toFloat() / maxCount.coerceAtLeast(1)
        return when {
            fraction <= 0.25f -> 1
            fraction <= 0.50f -> 2
            fraction <= 0.75f -> 3
            else -> 4
        }
    }

    /** 0..4 memorization level. Mirrors ReadingProgressColors.memorizationProgressToColor. */
    fun memorizationLevel(progress: Float): Int = when {
        progress <= 0f -> 0
        progress < 0.25f -> 1
        progress < 0.50f -> 2
        progress < 0.75f -> 3
        else -> 4
    }
}
