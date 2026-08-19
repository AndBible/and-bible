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

package net.bible.sharedui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.AbColorPickerDialog
import net.bible.sharedui.components.ColorPickerPresetsPage
import net.bible.sharedui.strings.LocalStrings

/**
 * The label's identity editor, opened from [LabelEditScreen]'s identity row: the name field, the
 * colour presets grid (plus a "custom…" escape hatch into [AbColorPickerDialog]), and — when
 * [iconVisible] — the custom-icon grid. Edits apply live; there is no OK/Cancel, consistent with
 * the colour picker's own live-commit behaviour elsewhere on this screen.
 *
 * Every icon cell renders through the host's [iconSlot] (i.e. `AndroidLabelIcon` on the Android
 * side), never a raw drawable — this is the structural fix for the two icons (`icon_circle_
 * question`, `icon_robot`) whose vector `fillColor` is `darker_gray`: going through the host's
 * tinted renderer means no drawable's own fill colour is ever read again.
 *
 * The grid tints every cell with the surrounding surface's own content colour, never the label's
 * colour: a pale label made every glyph near-invisible on this sheet's light surface (round-9a
 * whole-branch review M1), and the cell's selection is already carried by its background
 * highlight, so the tint does not need to carry the label's identity too.
 *
 * Split into [LabelIdentitySheet] (the `ModalBottomSheet` wrapper, owning dismissal) and
 * [LabelIdentitySheetContent] (the column of controls) so a golden test can capture the content
 * directly — a `ModalBottomSheet`'s own entrance animation makes a capture flaky. Same split as
 * `SearchSettingsSheet` / `SearchSheetContent`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelIdentitySheet(
    name: String,
    nameEditable: Boolean,
    colorArgb: Int,
    customIcon: String?,
    iconKeys: List<String?>,
    iconVisible: Boolean,
    onName: (String) -> Unit,
    onColor: (Int) -> Unit,
    onCustomIcon: (String?) -> Unit,
    onDismiss: () -> Unit,
    iconSlot: @Composable (String?, Color) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LabelIdentitySheetContent(
                name = name,
                nameEditable = nameEditable,
                colorArgb = colorArgb,
                customIcon = customIcon,
                iconKeys = iconKeys,
                iconVisible = iconVisible,
                onName = onName,
                onColor = onColor,
                onCustomIcon = onCustomIcon,
                iconSlot = iconSlot,
            )
        }
    }
}

/** The controls themselves, with no sheet chrome of its own — see [LabelIdentitySheet]'s doc for
 *  why this is a separate composable. */
@Composable
fun LabelIdentitySheetContent(
    name: String,
    nameEditable: Boolean,
    colorArgb: Int,
    customIcon: String?,
    iconKeys: List<String?>,
    iconVisible: Boolean,
    onName: (String) -> Unit,
    onColor: (Int) -> Unit,
    onCustomIcon: (String?) -> Unit,
    modifier: Modifier = Modifier,
    iconSlot: @Composable (String?, Color) -> Unit,
) {
    val strings = LocalStrings.current
    var customPickerOpen by remember { mutableStateOf(false) }

    Column(modifier) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            enabled = nameEditable,
            singleLine = true,
            label = { Text(strings.labelNameHint) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            strings.colorPickerTitle,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        ColorPickerPresetsPage(initialColor = colorArgb, color = colorArgb, onColorChange = onColor)
        TextButton(onClick = { customPickerOpen = true }) { Text(strings.colorPickerCustom) }

        if (iconVisible) {
            Text(
                strings.selectCustomIconLabel,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                iconKeys.forEach { key ->
                    val chosen = key == customIcon
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            )
                            .selectable(
                                selected = chosen,
                                onClick = { onCustomIcon(key) },
                                role = Role.RadioButton,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Cells go through the HOST's slot, so every glyph is tinted by the host's
                        // AndroidLabelIcon. That is the fix for the two icons whose vector fillColor
                        // is darker_gray rather than black: no drawable's own fill colour is read.
                        iconSlot(key, LocalContentColor.current)
                    }
                }
            }
        }
    }

    if (customPickerOpen) {
        AbColorPickerDialog(
            initialColor = colorArgb,
            onConfirm = { onColor(it); customPickerOpen = false },
            onDismiss = { customPickerOpen = false },
        )
    }
}
