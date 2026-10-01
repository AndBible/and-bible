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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/**
 * Shared size for top-bar action / overflow icons. M3's default is 24dp; classic AndBible uses
 * larger action-bar icons (F14), so we bump these a notch for a closer visual match. Kept in one
 * place so the whole top bar stays consistent and this is easy to tune on device later.
 */
val AbActionIconSize = 28.dp

/**
 * A top-bar action button with an enlarged (28dp) icon, for use in a TopAppBar `actions` slot.
 * Behaviourally identical to a plain `IconButton { Icon(...) }` — only the icon size differs.
 */
@Composable
fun AbActionIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(AbActionIconSize))
    }
}

/**
 * A top-bar overflow (3-dot) menu with an enlarged icon. Callers supply their existing
 * `DropdownMenuItem`s as [content]; open/close state is handled internally. The [content] lambda
 * receives a `close` callback so items can dismiss the menu on tap (call it from their `onClick`).
 *
 * [contentDescription] null means the shared "Menu" label (F84); every call site passes null today.
 *
 * [initiallyExpanded] seeds the internal open state — normally left `false` (the menu starts
 * closed); a test-only hook (same `initiallyXxxOpen` pattern as [net.bible.sharedui.search.SearchScreen]'s
 * `initiallyRecentMenuOpen`/`initiallySettingsOpen`) so a golden test can capture the menu OPEN
 * without simulating a click on the 3-dot icon.
 */
@Composable
fun AbOverflowMenu(
    contentDescription: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    AbActionIcon(Icons.Filled.MoreVert, contentDescription = contentDescription ?: LocalStrings.current.menu) { expanded = true }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { content { expanded = false } }
}
