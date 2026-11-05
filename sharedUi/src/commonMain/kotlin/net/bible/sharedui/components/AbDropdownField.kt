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

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import net.bible.sharedui.theme.isPureMonochrome
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3 exposed-dropdown filter field: read-only text field that opens a menu of [options].
 *
 * For short option lists only (the menu composes every item eagerly); use `AbSearchablePicker` for
 * long lists. [horizontalPadding]/[verticalPadding] default to the full-width screen-margin values;
 * a caller placing this in a tight row can shrink them. [enabled] = false disables the anchor field
 * (dimmed, matching [OutlinedTextField]'s own disabled look) and prevents the menu from opening —
 * for a read-only host screen (e.g. PromptEdit's Prompt/Advanced tabs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AbDropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 4.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            colors = abOutlinedTextFieldColors(),
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        val mono = isPureMonochrome()
        val menuContent: @Composable ColumnScope.() -> Unit = {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(optionLabel(opt)) }, onClick = { onSelect(opt); expanded = false })
            }
        }
        if (useInstantMenus()) {
            val density = LocalDensity.current
            val imeBottom = WindowInsets.ime.getBottom(density)
            val windowHeight = LocalWindowInfo.current.containerSize.height
            val position = remember(density, imeBottom, windowHeight) {
                InstantFieldMenuPosition(with(density) { 48.dp.roundToPx() }, windowHeight, imeBottom)
            }
            AbDropdownMenu(
                expanded = expanded && enabled, onDismissRequest = { expanded = false },
                // Material's public scope modifier tracks anchor width and visible-window/IME height.
                modifier = Modifier.exposedDropdownSize(),
                positionProvider = position,
                content = menuContent,
            )
        } else {
            ExposedDropdownMenu(
                expanded = expanded && enabled, onDismissRequest = { expanded = false },
                tonalElevation = if (mono) 0.dp else MenuDefaults.TonalElevation,
                shadowElevation = if (mono) 0.dp else MenuDefaults.ShadowElevation,
                border = if (mono) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                content = menuContent,
            )
        }
    }
}

/** Exposed menus choose below/above the field, never centred over it, and avoid the keyboard. */
internal data class InstantFieldMenuPosition(val margin: Int, val windowHeight: Int, val imeBottom: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Popup hosts may already exclude the IME from windowSize; do not subtract it twice.
        val visibleHeight = if (windowHeight > 0) minOf(windowSize.height, windowHeight - imeBottom) else windowSize.height
        val horizontal = InstantMenuPosition(0, 0, margin)
            .calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize).x
        val height = popupContentSize.height
        val y = listOf(anchorBounds.bottom, anchorBounds.top - height)
            .firstOrNull { it >= margin && it + height <= visibleHeight - margin }
            ?: if (height >= visibleHeight - 2 * margin || anchorBounds.center.y < visibleHeight / 2) margin
            else visibleHeight - margin - height
        return IntOffset(horizontal, y)
    }
}
