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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/**
 * Scaffold whose top bar switches between a normal [AbTopAppBar] and a selection bar.
 * In selection mode the bar shows a close (✕) navigation icon, the selected-item count as
 * the title, and [selectionActions] instead of the normal [actions].
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
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("$selectedCount") },
                    navigationIcon = {
                        IconButton(onClick = onExitSelection) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = selectionActions,
                )
            } else {
                AbTopAppBar(title = { Text(title) }, onNavigateUp = onNavigateUp, actions = actions)
            }
        },
        content = content,
    )
}
