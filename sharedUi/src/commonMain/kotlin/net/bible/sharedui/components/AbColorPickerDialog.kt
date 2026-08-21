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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/** The presets/custom page content, with [working] (the in-progress colour) and [presetsPage]
 *  (which page to show) hoisted to the caller: both the dialog and a sheet page need to render
 *  the OK affordance and the page switch in their own chrome. */
@Composable
fun AbColorPickerContent(
    initialColor: Int,
    working: Int,
    onWorkingChange: (Int) -> Unit,
    presetsPage: Boolean,
    modifier: Modifier = Modifier,
) {
    // The custom page is taller than the sliders it replaces; scroll rather than clip on a
    // small screen (spec §9).
    Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).then(modifier)) {
        if (presetsPage) {
            ColorPickerPresetsPage(
                initialColor = initialColor,
                color = working,
                onColorChange = onWorkingChange,
            )
        } else {
            ColorPickerCustomPage(
                initialColor = initialColor,
                color = working,
                onColorChange = onWorkingChange,
            )
        }
    }
}

/** Classic's colour picker: a presets page and a custom page, one working colour, committed only
 *  on OK.
 *
 *  The caller sees nothing until [onConfirm]; dismissing (Cancel, back, or an outside tap) calls
 *  [onDismiss] and changes nothing. That is classic's contract, and it is a deliberate change from
 *  batch 7a's `AbColorPicker`, which emitted every intermediate value straight through — see spec
 *  §4.4.
 *
 *  The page switch is classic's neutral button: it re-labels itself to the page it would switch to,
 *  and does not dismiss. Material 3's `AlertDialog` exposes only `confirmButton`/`dismissButton`
 *  slots, so all three buttons live in one `Row` inside `confirmButton`. */
@Composable
fun AbColorPickerDialog(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val opaqueInitial = remember(initialColor) { initialColor or (0xFF shl 24) }
    var working by remember(opaqueInitial) { mutableStateOf(opaqueInitial) }
    var presetsPage by remember { mutableStateOf(true) } // classic opens on presets

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.colorPickerTitle) },
        text = {
            AbColorPickerContent(
                initialColor = opaqueInitial,
                working = working,
                onWorkingChange = { working = it },
                presetsPage = presetsPage,
            )
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { presetsPage = !presetsPage }) {
                    Text(if (presetsPage) strings.colorPickerCustom else strings.colorPickerPresets)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    TextButton(onClick = { onConfirm(working) }) { Text(strings.okay) }
                }
            }
        },
    )
}
