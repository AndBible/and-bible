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

package net.bible.sharedui.reading

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * One rendered level's icon-resolution result, shared by [WindowPaneMenuRows] and
 * [ReadingOverflowMenuRows] (previously duplicated byte-for-byte in both files — whole-batch
 * review Task 6 minor). [rows] pairs each item with its RESOLVED icon (`null` when the item has no
 * [WindowPaneMenuItem][net.bible.sharedcore.window.WindowPaneMenuItem]/
 * [OptionsMenuItem][net.bible.sharedcore.reading.OptionsMenuItem] `iconKey`, or the key failed to
 * resolve via the host's `icon` lambda). [reserveIconSlot] is `true` when ANY row in [rows]
 * resolved an icon — classic's `MenuPopupHelper.setForceShowIcon(true)` reserves the leading-icon
 * frame for EVERY row in a popup once any row in it has an icon (`ListMenuItemView.setIcon`: a
 * `null` icon gets `mEmptyIcon`/`INVISIBLE`, not `GONE`), so a mixed icon/icon-less level still
 * keeps every label on the same left edge, while a purely icon-less level reserves nothing.
 *
 * Deliberately keyed on the RESOLVED icon (`icon(key) != null`), not the raw `iconKey` string —
 * material3's own `DropdownMenuItemContent` inserts the leading-icon `Box` on
 * `leadingIcon != null` (an `ifnull` branch on the lambda reference itself, independently
 * decompiled and confirmed during this batch's F5b review), so a key that fails to resolve must
 * not reserve a slot any more than a genuinely absent key would.
 *
 * Computed fresh per call, so each rendered level (e.g. [WindowPaneMenu]'s root vs. a pushed
 * submenu) decides independently — never shared across levels.
 */
internal data class MenuIconRows<T>(val rows: List<Pair<T, Painter?>>, val reserveIconSlot: Boolean)

/**
 * Resolves [items]' icon keys (via [iconKeyOf]) to `Painter`s through [icon], and computes the
 * per-level [MenuIconRows.reserveIconSlot] flag — see [MenuIconRows]'s kdoc for the full rationale.
 */
@Composable
internal fun <T> resolveMenuIconRows(
    items: List<T>,
    iconKeyOf: (T) -> String?,
    icon: @Composable (iconKey: String) -> Painter?,
): MenuIconRows<T> {
    val rows = items.map { item -> item to iconKeyOf(item)?.let { key -> icon(key) } }
    val reserveIconSlot = rows.any { (_, resolved) -> resolved != null }
    return MenuIconRows(rows, reserveIconSlot)
}
