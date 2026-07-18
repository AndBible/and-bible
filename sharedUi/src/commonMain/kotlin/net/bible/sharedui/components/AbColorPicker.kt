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

package net.bible.sharedui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** ARGB `Int` <-> Compose [Color] bridge, plus the fixed preset palette offered by
 *  [AbColorPicker]. Kept in `commonMain`: everything here is plain [Color]/[Int] math, no
 *  `android.graphics.*` — safe for the iOS target.
 *
 *  The palette mirrors `BookmarkStyle`'s colours in
 *  `app/src/main/java/net/bible/android/database/bookmarks/BookmarkEntities.kt` (each built via
 *  `Color.argb(255, r, g, b)`), minus `SPEAK` (that entry is a hard-coded internal style for Speak
 *  bookmarks, explicitly excluded from user-facing style lists) and with the `YELLOW_STAR`/
 *  `YELLOW_HIGHLIGHT` duplicate collapsed to one swatch. `BLUE_HIGHLIGHT` (== `defaultLabelColor`)
 *  is included. */
object AbColor {
    val palette: List<Int> = listOf(
        0xFFFFFF00.toInt(), // YELLOW_STAR / YELLOW_HIGHLIGHT
        0xFFD50000.toInt(), // RED_HIGHLIGHT
        0xFF00FF00.toInt(), // GREEN_HIGHLIGHT
        0xFF91A7FF.toInt(), // BLUE_HIGHLIGHT (defaultLabelColor)
        0xFFFFA500.toInt(), // ORANGE_HIGHLIGHT
        0xFF800080.toInt(), // PURPLE_HIGHLIGHT
        0xFF806380.toInt(), // UNDERLINE
    )

    fun toComposeColor(argb: Int): Color = Color(argb)
    fun toArgb(c: Color): Int = c.toArgb()
}

/** A dependency-free colour picker working on ARGB [Int] (matches `Label.color`): a row of preset
 *  swatches ([AbColor.palette]) plus hand-rolled hue/saturation/value sliders for a custom colour.
 *  Picking a swatch or moving a slider both call [onColorChange] immediately (no separate confirm
 *  step) — the caller (e.g. `LabelEditScreen`) owns the committed value. */
@Composable
fun AbColorPicker(color: Int, onColorChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRowPalette(selected = color, onSelect = onColorChange)

        // Re-derive HSV only when the incoming color actually changes (e.g. a preset tap, or the
        // initial value) - not on every recomposition - so dragging a slider doesn't fight the
        // rounding of its own hsvToArgb -> rgbToHsv round trip.
        val hsv = remember(color) { rgbToHsv(color) }
        var hue by remember(color) { mutableStateOf(hsv[0]) }
        var sat by remember(color) { mutableStateOf(hsv[1]) }
        var value by remember(color) { mutableStateOf(hsv[2]) }
        fun emit() = onColorChange(hsvToArgb(hue, sat, value))

        LabeledSlider("H", hue, 0f, 360f) { hue = it; emit() }
        LabeledSlider("S", sat, 0f, 1f, isPercent = true) { sat = it; emit() }
        LabeledSlider("V", value, 0f, 1f, isPercent = true) { value = it; emit() }

        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(AbColor.toComposeColor(color)),
        )
    }
}

/** [AbColor.palette] rendered as clickable colour circles; the selected swatch gets a ring. */
@Composable
private fun FlowRowPalette(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (argb in AbColor.palette) {
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clickable { onSelect(argb) }
                    .padding(2.dp)
                    .background(AbColor.toComposeColor(argb), CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** Label + right-aligned readout on the top line, an M3 [Slider] below - same shape as
 *  [AbSliderRow] but taking/emitting [Float] (HSV components aren't whole numbers).
 *  [isPercent] formats the readout as a `0..100` percentage (for the `0f..1f` saturation/value
 *  sliders) instead of a raw integer (used for the `0..360` hue slider, in degrees). */
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueMin: Float,
    valueMax: Float,
    isPercent: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val readout = if (isPercent) (value * 100).roundToInt().toString() else value.toInt().toString()
            Text(readout, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueMin..valueMax)
    }
}

/** Decomposes an ARGB [Int] into `[hue in 0..360, saturation in 0..1, value in 0..1]`, hand-rolled
 *  (no `android.graphics.Color`) so this stays iOS-clean. Standard RGB->HSV conversion. */
private fun rgbToHsv(argb: Int): FloatArray {
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
    val value = maxC
    return floatArrayOf(hue, sat, value)
}

/** Inverse of [rgbToHsv]: builds an ARGB [Int] from hue/saturation/value, alpha fixed at `0xFF`.
 *  Hand-rolled HSV->RGB conversion (no `android.graphics.Color`), so this stays iOS-clean. */
private fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val c = v * s
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = v - c

    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val r = (((r1 + m) * 255f).roundToIntClamped())
    val g = (((g1 + m) * 255f).roundToIntClamped())
    val b = (((b1 + m) * 255f).roundToIntClamped())

    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun Float.roundToIntClamped(): Int = kotlin.math.round(this).toInt().coerceIn(0, 255)
