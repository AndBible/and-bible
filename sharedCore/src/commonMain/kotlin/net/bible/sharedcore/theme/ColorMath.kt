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

package net.bible.sharedcore.theme

import net.bible.service.common.DisplayColorMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Luminance-weighted grayscale of an ARGB int, alpha preserved. */
fun toGrayscaleArgb(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val y = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
    return (a shl 24) or (y shl 16) or (y shl 8) or y
}

/** Accent color for a mode: colored in NORMAL/COLOR_EINK, grayscale in BW. */
fun accentArgbFor(baseArgb: Int, mode: DisplayColorMode): Int =
    if (mode == DisplayColorMode.BW) toGrayscaleArgb(baseArgb) else baseArgb

/** Opaque black, classic's 20th preset. */
const val BLACK_ARGB: Int = 0xFF000000.toInt()

/** Classic's default palette, verbatim from `ColorPickerDialog.MATERIAL_COLORS` (19 entries, in
 *  its order — the grid's order is part of the ported look). AndBible never passes its own
 *  presets, so this is the only palette the picker offers. */
val MATERIAL_PRESETS: List<Int> = listOf(
    0xFFF44336.toInt(), // RED 500
    0xFFE91E63.toInt(), // PINK 500
    0xFFFF2C93.toInt(), // LIGHT PINK 500
    0xFF9C27B0.toInt(), // PURPLE 500
    0xFF673AB7.toInt(), // DEEP PURPLE 500
    0xFF3F51B5.toInt(), // INDIGO 500
    0xFF2196F3.toInt(), // BLUE 500
    0xFF03A9F4.toInt(), // LIGHT BLUE 500
    0xFF00BCD4.toInt(), // CYAN 500
    0xFF009688.toInt(), // TEAL 500
    0xFF4CAF50.toInt(), // GREEN 500
    0xFF8BC34A.toInt(), // LIGHT GREEN 500
    0xFFCDDC39.toInt(), // LIME 500
    0xFFFFEB3B.toInt(), // YELLOW 500
    0xFFFFC107.toInt(), // AMBER 500
    0xFFFF9800.toInt(), // ORANGE 500
    0xFF795548.toInt(), // BROWN 500
    0xFF607D8B.toInt(), // BLUE GREY 500
    0xFF9E9E9E.toInt(), // GREY 500
)

/** Classic's twelve shade percentages (`ColorPickerDialog.getColorShades`), light to dark. */
val SHADE_PERCENTS: List<Double> =
    listOf(0.9, 0.7, 0.5, 0.333, 0.166, -0.125, -0.25, -0.375, -0.5, -0.675, -0.7, -0.775)

/** Classic's `shadeColor`: positive [percent] blends towards white, negative towards black.
 *
 *  The rounding is `floor(x + 0.5)`, not `kotlin.math.round`, on purpose: classic uses Java's
 *  `Math.round`, which rounds half **up** (towards +inf), while `kotlin.math.round` rounds half
 *  away from zero. They differ on exactly the negative half-cases this function produces for
 *  negative percentages (e.g. `-25.5` -> `-25` vs `-26`), which would move every dark shade of a
 *  colour with an odd channel by one step. */
fun shadeArgb(argb: Int, percent: Double): Int {
    val t = if (percent < 0) 0.0 else 255.0
    val p = if (percent < 0) -percent else percent
    fun ch(shift: Int): Int {
        val c = (argb shr shift) and 0xFF
        return (floor((t - c) * p + 0.5) + c).toInt().coerceIn(0, 255)
    }
    val a = (argb ushr 24) and 0xFF
    return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** The twelve shades of [argb] shown under the preset grid, lightest first. */
fun colorShades(argb: Int): List<Int> = SHADE_PERCENTS.map { shadeArgb(argb, it) }

/** Classic's preset list for one opening of the presets page: the working colour is unshifted to
 *  the front if the palette lacks it, then the colour the dialog opened with, and black is appended
 *  only while the list is still exactly as long as [MATERIAL_PRESETS] — classic's
 *  `if (isMaterialColors && presets.length == 19) push(black)`. */
fun presetColors(current: Int, original: Int = current): List<Int> {
    fun unshiftIfMissing(list: List<Int>, value: Int) = if (value in list) list else listOf(value) + list
    var list = unshiftIfMissing(MATERIAL_PRESETS, current)
    if (original != current) list = unshiftIfMissing(list, original)
    return if (list.size == MATERIAL_PRESETS.size) list + BLACK_ARGB else list
}

/** WCAG relative luminance, replicating `androidx.core.graphics.ColorUtils.calculateLuminance`
 *  (sRGB decoded through the standard piecewise transfer function, then 0.2126/0.7152/0.0722).
 *  Deliberately NOT [toGrayscaleArgb]'s 601-style weighting on gamma-encoded channels: that is a
 *  different function for a different job, and swapping them would move the check-mark colour on
 *  mid-tone swatches. */
fun relativeLuminance(argb: Int): Double {
    fun lin(shift: Int): Double {
        val v = ((argb shr shift) and 0xFF) / 255.0
        return if (v < 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
}

/** Classic's check-mark rule: a swatch at or above 0.65 luminance gets a black check mark. */
fun isLightColor(argb: Int): Boolean = relativeLuminance(argb) >= 0.65

/** Six uppercase hex digits, alpha dropped — what the custom page's field shows.
 *  Hand-rolled rather than `String.format`, which is JVM-only. */
fun hexOf(argb: Int): String = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** Parses the custom page's hex field: an optional `#`, then exactly 3 or 6 hex digits; alpha is
 *  forced opaque. Returns `null` for anything else, and the caller leaves the colour alone —
 *  classic instead invented a colour for 1/2/4/5/7-digit input, which this port drops (spec §2). */
fun parseHexColor(text: String): Int? {
    val s = text.trim().removePrefix("#")
    if (s.length != 3 && s.length != 6) return null
    if (!s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val six = if (s.length == 3) s.map { "$it$it" }.joinToString("") else s
    return six.toLong(16).toInt() or (0xFF shl 24)
}

/** ARGB -> `(hue 0..360, saturation 0..1, value 0..1)`. Originally written hand-rolled, to stay off
 *  `android.graphics`, for a `:sharedUi` colour-picker composable added in batch 7a (since deleted
 *  by batch 4c's classic-parity port); moved here to `:sharedCore` so the maths stays testable. */
fun argbToHsv(argb: Int): Triple<Float, Float, Float> {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val hue = when {
        delta == 0f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = if (maxC == 0f) 0f else delta / maxC
    return Triple(hue, sat, maxC)
}

/** Inverse of [argbToHsv]; alpha is always `0xFF`. */
fun hsvToArgb(hue: Float, sat: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val c = value * sat
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = floor((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

// The four mappings below are the custom page's geometry, kept out of the composable so they can be
// tested: a `pointerInput` lambda is invisible to both unit tests and goldens. They clamp to the
// panel edge exactly as classic's `pointToSatVal`/`pointToHue` do, so a drag that leaves the panel
// pins the value instead of wrapping.

/** Saturation left->right, value bottom->top, both clamped to `0..1`. */
fun satValFromOffset(x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> {
    val sat = if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)
    val value = if (height <= 0f) 0f else 1f - (y / height).coerceIn(0f, 1f)
    return sat to value
}

/** Hue 360 at the top of the strip, 0 at the bottom (classic's orientation). */
fun hueFromOffset(y: Float, height: Float): Float =
    if (height <= 0f) 0f else (360f - (y / height) * 360f).coerceIn(0f, 360f)

/** Where the square's tracker goes for a given saturation/value. */
fun satValToOffset(sat: Float, value: Float, width: Float, height: Float): Pair<Float, Float> =
    (sat * width) to ((1f - value) * height)

/** Where the strip's tracker goes for a given hue. */
fun hueToOffset(hue: Float, height: Float): Float = height - (hue * height / 360f)
