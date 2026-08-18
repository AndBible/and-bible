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

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.reading.QuickDocMenuItem
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.navigation.LocalCategoryIcon

/**
 * The reading-view toolbar's quick-document picker: a Material3 [DropdownMenu] anchored to the
 * Bible/Commentary toolbar button (see [ReadingToolbar]). Ported from classic
 * `MainBibleActivity.menuForDocs`'s `PopupMenu` (the 2-doc shortcut + sort + current-disabled are
 * decided upstream by `:sharedCore` `QuickDocPicker.action`). Stateless/controlled: [expanded] and
 * dismissal are host-owned. The current document's row is disabled (`item.enabled == false`).
 */
@Composable
fun QuickDocMenu(
    expanded: Boolean,
    items: List<QuickDocMenuItem>,
    onSelect: (id: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        QuickDocMenuRows(items, onSelect)
    }
}

/**
 * The menu's row content, factored out of [QuickDocMenu] so it can be rendered outside a
 * [DropdownMenu]'s [androidx.compose.ui.window.Popup] too (each [androidx.compose.material3.DropdownMenuItem] is a plain
 * composable, not scoped to a menu container). Deliberately public (not `internal`): it's called
 * directly, inside a plain `Column`, by `QuickDocMenuGoldenTest` (in the `:app` module, so
 * `internal` visibility would not reach it) as a golden-capture surrogate for the real popup:
 * force-opening a Compose `DropdownMenu` under Robolectric/Roborazzi has repeatedly, intermittently
 * hung this repo's golden capture (see [ReadingOverflowMenuRows]'s kdoc) — so this single
 * implementation is shared by both the real popup and the golden's non-popup surrogate, keeping the
 * rendered rows byte-for-byte the same rather than duplicating the item-building logic.
 *
 * Each row's leading icon comes from the [LocalCategoryIcon] seam, keyed on [QuickDocMenuItem]'s
 * `category` — so any host rendering this (including the golden harness) must provide it.
 */
@Composable
fun QuickDocMenuRows(items: List<QuickDocMenuItem>, onSelect: (id: String) -> Unit) {
    val categoryIcon = LocalCategoryIcon.current
    items.forEach { item ->
        AbMenuItem(
            text = item.label,
            onClick = { onSelect(item.id) },
            icon = { Icon(painter = categoryIcon(item.category), contentDescription = null) },
            enabled = item.enabled,
        )
    }
}
