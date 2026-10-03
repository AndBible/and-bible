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

package net.bible.sharedcore.window

/**
 * A single row of the per-window (☰) pane popup menu, ported from classic `SplitBibleArea`'s
 * inflated window options menu (`getItemOptions`/`PopupMenu`). Structurally identical to
 * `net.bible.sharedcore.reading.OptionsMenuItem` (the reading-view toolbar's overflow menu) with
 * one addition: [submenu] — a non-empty list turns the row into a submenu entry (e.g. the
 * "Synchronise" row exposing sync-group choices, mirroring classic `SubMenuPreference` /
 * iOS's `BibleWindowPaneMenuPopup` submenu stack) instead of an immediately-actionable item.
 *
 * [id] identifies the menu action and is what id-keyed click dispatch in the host switches on
 * (Plan B Task 4 builds the real item list from classic `getItemOptions` and dispatches
 * [id]-keyed clicks). [checkable]/[checked] mirror an `android:checkable="true"` menu item (e.g.
 * the "Pinned" toggle); [opensDialog] marks an item that opens a further dialog/sub-screen rather
 * than acting immediately (rendered with a trailing " …", matching the classic menu's
 * "..."-suffixed labels). A row with a non-empty [submenu] is rendered with a trailing "›" and,
 * on click, navigates into that submenu in place rather than firing [id].
 */
data class WindowPaneMenuItem(
    val id: String,
    val label: String,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val opensDialog: Boolean = false,
    val submenu: List<WindowPaneMenuItem> = emptyList(),
    /**
     * Stable icon key for the leading glyph, resolved to a `Painter` by the host — the same seam
     * `DrawerItem.iconKey`/`ReadingDrawerContent`'s `icon` lambda already uses (the host maps each
     * key to an `R.drawable` id via a table like `ComposeReadingViewHost.drawerIconResIds`; the
     * per-id table for this menu is a later task). A string, not a resource id, so this module
     * stays iOS-clean. `null` = no icon, matching classic's iconless `window_popup_menu.xml` items
     * (e.g. the dynamic "Move to"/"Synchronise"/"Copy settings to" submenu rows).
     */
    val iconKey: String? = null,
)
