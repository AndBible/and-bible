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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.theme.colorShades
import net.bible.sharedcore.theme.isLightColor
import net.bible.sharedcore.theme.presetColors

/** Classic's presets page: a grid of the Material palette (plus the working and original colours,
 *  plus black — see `presetColors`), and under it the twelve shades of whichever swatch the grid
 *  selection last landed on.
 *
 *  [initialColor] is the colour the dialog opened with and [color] the working colour; both go into
 *  the preset list exactly as classic's `loadPresets` does. The GRID is checked by VALUE: a swatch
 *  is checked when it equals [color], which reproduces classic's `selectNone()` (picking a shade
 *  clears the grid check) without a second piece of state to keep in step. The SHADE ROW is
 *  checked by INDEX instead — see the comment on `shadeIndex` below for why.
 *
 *  Public, not internal, because the golden tests live in `:app` — a different module. */
@Composable
fun ColorPickerPresetsPage(
    initialColor: Int,
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Classic rebuilds the preset list when the presets PAGE is created, not on every tap, so the
    // unshifted swatch does not jump around while the user picks. `remember` with no key does the
    // same: switching pages leaves composition and rebuilds on return.
    val presets = remember { presetColors(color, initialColor) }
    var shadeBase by remember { mutableStateOf(color) }
    val shades = remember(shadeBase) { colorShades(shadeBase) }

    // Shade selection is tracked by INDEX, not by value: `shadeArgb` clamps at the channel
    // extremes, so for a base colour already near black/white several shade percentages collapse
    // to the same ARGB (e.g. black's seven negative-percent shades all become 0xFF000000). A
    // value-based `checked` would then mark all of them at once. Classic avoids this by tracking
    // the tapped VIEW (`cpv == v` in `ColorPickerDialog.createColorShades`); an index is the
    // closest Compose analogue. Do not "simplify" this back to `shade == color` — the grid above
    // stays value-based deliberately (see the class doc), only the shade row needs this.
    var shadeIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                ColorSwatchCircle(
                    color = preset,
                    checked = preset == color,
                    size = 50.dp,
                    onClick = { shadeBase = preset; shadeIndex = null; onColorChange(preset) },
                )
            }
        }

        HorizontalDivider()

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shades.forEachIndexed { index, shade ->
                ColorSwatchCircle(
                    color = shade,
                    checked = index == shadeIndex,
                    size = 40.dp,
                    onClick = { shadeIndex = index; onColorChange(shade) },
                )
            }
        }
    }
}

/** One swatch. The check mark is black on a light swatch and white on a dark one — classic's
 *  0.65-luminance rule (`ColorPaletteAdapter`), computed from the swatch and not from the theme, so
 *  it stays legible in every display mode. Bordered with classic's fixed 1dp grey (`ColorPanelView
 *  .onDraw`, CIRCLE branch, lines 142-155; `PICKER_BORDER_COLOR` in `ColorPickerCustomPage.kt`) so
 *  a white or black swatch — including a run of identical clamped shades, see `colorShades` — still
 *  reads as a separate disc instead of blurring into the dialog background or its neighbours. */
@Composable
private fun ColorSwatchCircle(color: Int, checked: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(AbColor.toComposeColor(color), CircleShape)
            .clip(CircleShape)
            .border(PICKER_BORDER_WIDTH, PICKER_BORDER_COLOR, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isLightColor(color)) Color.Black else Color.White,
            )
        }
    }
}
