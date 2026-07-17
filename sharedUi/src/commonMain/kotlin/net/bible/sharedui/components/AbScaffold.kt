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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Reusable Material3 top app bar: a title slot, optional up-navigation, and trailing actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbTopAppBar(
    title: @Composable () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(AbActionIconSize),
                    )
                }
            }
        },
        actions = actions,
        // F2: the Compose hosts run inside an AppCompatActivity (ActivityBase) whose content
        // frame already insets for the status bar (like the classic View screens). The M3
        // default here would add the status-bar inset a SECOND time → the bar sat one bar-
        // height too low. Zero the M3 inset so the single AppCompat/system inset positions it.
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

/** Scaffold + a simple string-title top app bar. Backward-compatible with the Batch 1 call sites. */
@Composable
fun AbScaffold(
    title: String,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { AbTopAppBar(title = { Text(title) }, onNavigateUp = onNavigateUp, actions = actions) },
        // F2: see AbTopAppBar — the AppCompat host frame provides the system insets, so the
        // Scaffold must not add them again (would double the top/bottom gap).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

/** Scaffold with a fully custom top bar (e.g. a clickable two-line title). */
@Composable
fun AbScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        // F2: see the string-title overload — avoid double system insets under the AppCompat host.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
