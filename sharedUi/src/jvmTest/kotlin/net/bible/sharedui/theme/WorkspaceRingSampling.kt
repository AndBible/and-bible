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
package net.bible.sharedui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Colours equal within anti-aliasing tolerance. */
internal fun Color.near(other: Color, tolerance: Float = 0.1f): Boolean =
    abs(red - other.red) <= tolerance && abs(green - other.green) <= tolerance && abs(blue - other.blue) <= tolerance

/**
 * Samples [samples] points on the circle of radius [r] around ([cx], [cy]) and returns the fraction
 * that are (near) [ring]. A dashed ring gives a fraction clearly between 0 and 1; a solid ring of
 * that colour gives ~1; no ring gives 0.
 */
internal fun PixelMap.ringFraction(cx: Float, cy: Float, r: Float, ring: Color, samples: Int = 120): Float {
    var hits = 0
    for (i in 0 until samples) {
        val a = 2 * PI * i / samples
        val x = (cx + r * cos(a)).roundToInt().coerceIn(0, width - 1)
        val y = (cy + r * sin(a)).roundToInt().coerceIn(0, height - 1)
        if (this[x, y].near(ring)) hits++
    }
    return hits.toFloat() / samples
}
