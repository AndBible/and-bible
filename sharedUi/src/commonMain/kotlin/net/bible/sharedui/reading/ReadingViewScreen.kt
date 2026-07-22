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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.window.WindowLayoutState

/**
 * Top-level reading-view screen. Plan A (this task) is the reading area only — a `Column`
 * wrapping [SplitContent]; Plan B (Batch 12b next step) inserts the app's toolbar above it in
 * the same `Column`, so the container shape is deliberately kept even though it wraps a single
 * child for now.
 */
@Composable
fun ReadingViewScreen(
    layout: WindowLayoutState,
    onWindowActivated: (String) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    pane: @Composable (windowId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SplitContent(
            layout = layout,
            onWindowActivated = onWindowActivated,
            onSeparatorCommitted = onSeparatorCommitted,
            pane = pane,
        )
    }
}
