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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.strings.LocalStrings

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
 * stack), and a "Back" row pops back to the parent level. The path resets whenever the menu
 * closes ([expanded] goes false) so the next open always starts at the root.
 *
 * [icon] resolves each row's [WindowPaneMenuItem.iconKey] to a `Painter` — the same host-lambda
 * seam [ReadingDrawerContent]'s `icon` parameter uses, keeping this file free of Android types.
 * Defaulted to always-`null` so existing call sites and their goldens are unaffected.
 */
@Composable
fun WindowPaneMenu(
    items: List<WindowPaneMenuItem>,
    expanded: Boolean,
    onItemClick: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (iconKey: String) -> Painter? = { null },
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
            icon = icon,
        )
    }
}

/**
 * One level's row content, factored out of [WindowPaneMenu] so it can be rendered outside a
 * [DropdownMenu]'s [androidx.compose.ui.window.Popup] too (each [androidx.compose.material3.DropdownMenuItem] is a plain
 * composable, not scoped to a menu container). Deliberately public (not `internal`): it's called
 * directly, inside a plain `Column`, by `WindowPaneMenuGoldenTest` (in the `:app` module, so
 * `internal` visibility would not reach it) as a golden-capture surrogate for the real popup —
 * force-opening a Compose `DropdownMenu` under Robolectric/Roborazzi has repeatedly,
 * intermittently hung this repo's golden capture (see [ReadingOverflowMenuRows]'s doc), so this
 * single implementation is shared by both the real popup (one level at a time) and the golden's
 * non-popup surrogate.
 *
 * [showBack] renders a leading "Back" row (calling [onBack] on click, with a leading back-arrow
 * icon of its own) when a submenu is open — it is emitted before [items] is resolved, so it never
 * participates in [reserveIconSlot] below (classic has no such row at all, so there is nothing to
 * mirror). Each item renders its label (suffixed " …" when [WindowPaneMenuItem.opensDialog]) with
 * a trailing check mark (when checkable+checked) or a chevron (when it has a non-empty [submenu]);
 * clicking a submenu row calls [onEnterSubmenu] instead of [onItemClick].
 *
 * [icon] resolves [WindowPaneMenuItem.iconKey] to a leading `Painter` (see [WindowPaneMenu]'s
 * kdoc). Classic's `MenuPopupHelper.setForceShowIcon(true)` (`SplitBibleArea.kt:859`,
 * `MainBibleActivity.kt:1417` — the pane-menu and overflow-menu call sites respectively) makes
 * Android's menu row view reserve the icon frame for EVERY row once ANY row in that popup has an
 * icon, so an icon-less row still keeps a blank icon-sized gap and every row's label lands on the
 * same left edge (see `ListMenuItemView.setIcon`: a `null` icon gets `mEmptyIcon`/`INVISIBLE`, not
 * `GONE`). This matters here: [net.bible.android.view.activity.page.OptionsMenuStateBuilder.build]
 * appends the dynamic, icon-less `textOptionItem` rows into the SAME flat top-level list as the
 * nine iconed static entries, so the reading-view overflow menu genuinely mixes icon and icon-less
 * rows at one level whenever the user has any display-setting history - this is not just a golden
 * test convenience. [reserveIconSlot] mirrors that per-level: computed from THIS level's own
 * resolved icons (a `null` [WindowPaneMenuItem.iconKey] or an unresolved key both count as "no
 * icon" for this row, but if ANY row in [items] resolves one, every row - including the icon-less
 * ones - gets a same-size leading slot; a purely icon-less level reserves nothing, keeping today's
 * compact look). Each level is rendered by its own [WindowPaneMenuRows] call (root vs. a pushed
 * [WindowPaneMenuItem.submenu]), so the decision is naturally per-level already. The resolve +
 * reservation computation itself is [resolveMenuIconRows], shared with [ReadingOverflowMenuRows]
 * (previously duplicated byte-for-byte between the two files).
 */
@Composable
fun WindowPaneMenuRows(
    items: List<WindowPaneMenuItem>,
    showBack: Boolean,
    onBack: () -> Unit,
    onEnterSubmenu: (WindowPaneMenuItem) -> Unit,
    onItemClick: (id: String) -> Unit,
    icon: @Composable (iconKey: String) -> Painter? = { null },
) {
    if (showBack) {
        AbMenuItem(
            text = LocalStrings.current.menuBack,
            onClick = onBack,
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        )
    }
    val resolved = resolveMenuIconRows(items, WindowPaneMenuItem::iconKey, icon)
    resolved.rows.forEach { (item, painter) ->
        val hasSubmenu = item.submenu.isNotEmpty()
        AbMenuItem(
            text = if (item.opensDialog) "${item.label} …" else item.label,
            onClick = { if (hasSubmenu) onEnterSubmenu(item) else onItemClick(item.id) },
            icon = painter?.let { p -> { Icon(painter = p, contentDescription = null) } },
            reserveIconSlot = resolved.reserveIconSlot && painter == null,
            checkable = item.checkable,
            checked = item.checked,
            enabled = item.enabled,
            trailing = if (hasSubmenu) {
                { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
            } else null,
        )
    }
}
