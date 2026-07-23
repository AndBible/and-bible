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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.bible.sharedcore.window.WindowPaneMenuItem

/**
 * The per-window (☰) pane popup menu: a Material3 [DropdownMenu] listing [items] in order, with
 * in-place submenu navigation. Ported from classic `SplitBibleArea`'s inflated window options
 * `PopupMenu` (Plan B Task 4 builds the real [WindowPaneMenuItem] list from classic
 * `getItemOptions` and dispatches [onItemClick] by id). Stateless/controlled: [expanded] and
 * dismissal are owned by the caller, mirroring [ReadingOverflowMenu]'s contract.
 *
 * Structurally identical to [ReadingOverflowMenu] with one addition: a row whose
 * [WindowPaneMenuItem.submenu] is non-empty pushes that submenu onto an internal navigation
 * [path] instead of firing [onItemClick] (mirrors iOS's `BibleWindowPaneMenuPopup` submenu
 * stack), and a "‹ Back" row pops back to the parent level. The path resets whenever the menu
 * closes ([expanded] goes false) so the next open always starts at the root.
 */
@Composable
fun WindowPaneMenu(
    items: List<WindowPaneMenuItem>,
    expanded: Boolean,
    onItemClick: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var path by remember { mutableStateOf(listOf<WindowPaneMenuItem>()) }
    LaunchedEffect(expanded) {
        if (!expanded) path = emptyList()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        val level = path.lastOrNull()?.submenu ?: items
        WindowPaneMenuRows(
            items = level,
            showBack = path.isNotEmpty(),
            onBack = { path = path.dropLast(1) },
            onEnterSubmenu = { path = path + it },
            onItemClick = onItemClick,
        )
    }
}

/**
 * One level's row content, factored out of [WindowPaneMenu] so it can be rendered outside a
 * [DropdownMenu]'s [androidx.compose.ui.window.Popup] too (each [DropdownMenuItem] is a plain
 * composable, not scoped to a menu container). Deliberately public (not `internal`): it's called
 * directly, inside a plain `Column`, by `WindowPaneMenuGoldenTest` (in the `:app` module, so
 * `internal` visibility would not reach it) as a golden-capture surrogate for the real popup —
 * force-opening a Compose `DropdownMenu` under Robolectric/Roborazzi has repeatedly,
 * intermittently hung this repo's golden capture (see [ReadingOverflowMenuRows]'s doc), so this
 * single implementation is shared by both the real popup (one level at a time) and the golden's
 * non-popup surrogate.
 *
 * [showBack] renders a leading "‹ Back" row (calling [onBack] on click) when a submenu is open.
 * Each item renders its label (suffixed " …" when [WindowPaneMenuItem.opensDialog]) with a
 * trailing check mark (when checkable+checked) or "›" (when it has a non-empty [submenu]);
 * clicking a submenu row calls [onEnterSubmenu] instead of [onItemClick].
 */
@Composable
fun WindowPaneMenuRows(
    items: List<WindowPaneMenuItem>,
    showBack: Boolean,
    onBack: () -> Unit,
    onEnterSubmenu: (WindowPaneMenuItem) -> Unit,
    onItemClick: (id: String) -> Unit,
) {
    if (showBack) {
        DropdownMenuItem(text = { Text("‹ Back") }, onClick = onBack)
    }
    items.forEach { item ->
        val hasSubmenu = item.submenu.isNotEmpty()
        DropdownMenuItem(
            text = { Text(if (item.opensDialog) "${item.label} …" else item.label) },
            enabled = item.enabled,
            trailingIcon = {
                if (item.checkable && item.checked) {
                    Icon(Icons.Default.Check, contentDescription = null)
                } else if (hasSubmenu) {
                    Text("›")
                }
            },
            onClick = { if (hasSubmenu) onEnterSubmenu(item) else onItemClick(item.id) },
        )
    }
}
