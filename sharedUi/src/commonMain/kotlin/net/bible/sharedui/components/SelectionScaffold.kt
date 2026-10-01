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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.SyncSystemBars

/**
 * Scaffold whose top bar has three modes, in this precedence: selection > search > normal.
 * In selection mode the bar shows a close (✕) navigation icon, the selected-item count as the title,
 * and [selectionActions] instead of the normal [actions] — selection wins outright and is unaware of
 * search. Otherwise [search] and [searchCallbacks], when BOTH non-null, hand the whole bar to the
 * inline search field (see [AbTopAppBar]); the screen's search mode lives in its controller, so
 * leaving selection mode brings the search bar back with its query intact and nothing has to be
 * preserved across the swap.
 *
 * All three of this app's raw `TopAppBar(...)` call sites (this one, [AbTopAppBar]'s normal branch
 * and its search branch) set `titleContentColor`/`navigationIconContentColor`/
 * `actionIconContentColor` explicitly to `onSurface`, so a bar never draws its own title and icons
 * in two different M3 tokens — copy this pattern for a fourth call site rather than leaving colors
 * at the M3 default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbSelectionScaffold(
    title: String,
    selectionMode: Boolean,
    selectedCount: Int,
    onNavigateUp: () -> Unit,
    onExitSelection: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    selectionActions: @Composable RowScope.() -> Unit = {},
    search: AbTopBarSearchState? = null,
    searchCallbacks: AbTopBarSearchCallbacks? = null,
    searchActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            if (selectionMode) {
                // The normal branch syncs the bars through AbTopAppBar; this branch drew a raw
                // TopAppBar and synced nothing, so the status bar kept the NORMAL bar's colour and
                // icon appearance for as long as selection was active (host-inset-ownership spec,
                // section 3.4). Same container colour the bar below actually uses.
                SyncSystemBars(
                    container = TopAppBarDefaults.topAppBarColors().containerColor,
                    fillWindowBackground = true,
                )
                TopAppBar(
                    title = { Text("$selectedCount") },
                    navigationIcon = {
                        IconButton(onClick = onExitSelection) {
                            Icon(Icons.Filled.Close, contentDescription = LocalStrings.current.cancel)
                        }
                    },
                    actions = selectionActions,
                    // Same fix as AbTopAppBar's normal branch: the M3 default splits this bar's
                    // title (onSurface) from its navigation/action icons (onSurfaceVariant).
                    colors = TopAppBarDefaults.topAppBarColors(
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    // The Compose hosts do NOT inset their content frame -- they set
                    // disableBaseSetupUi = true and call applyComposeHostWindowSetup(), which omits
                    // ActivityBase's content-root padding on purpose (host-inset-ownership spec,
                    // 2026-09-18). So this bar applies Material's real window insets. Zeroing them
                    // here, as this line did until 2026-09-18, would put the bar under the status
                    // bar; adding the host padding back would double it. The two go together.
                )
            } else {
                AbTopAppBar(
                    title = { AbTopBarTitle(title) },
                    onNavigateUp = onNavigateUp,
                    actions = actions,
                    search = search,
                    searchCallbacks = searchCallbacks,
                    searchActions = searchActions,
                )
            }
        },
        // Same content insets as AbScaffold (`abScaffoldContentInsets`, fix batch 2).
        contentWindowInsets = abScaffoldContentInsets(),
        content = content,
    )
}
