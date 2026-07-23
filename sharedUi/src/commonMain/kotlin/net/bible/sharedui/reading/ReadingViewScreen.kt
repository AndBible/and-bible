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
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState

/**
 * Top-level reading-view screen: the [ReadingToolbar] (unless [fullScreen]) stacked above the
 * split reading area ([SplitContent]) in a single `Column`. Plan A (prior task) was the reading
 * area only; Plan B (this task) folds the toolbar in, dropping it entirely — rather than merely
 * hiding it — when [fullScreen], so [SplitContent] reclaims the full height via
 * `Modifier.weight(1f)`.
 */
@Composable
fun ReadingViewScreen(
    layout: WindowLayoutState,
    toolbar: ToolbarState,
    toolbarIcons: ReadingToolbarIcons,
    toolbarCallbacks: ReadingToolbarCallbacks,
    fullScreen: Boolean,
    onWindowActivated: (String) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    pane: @Composable (windowId: String) -> Unit,
    searchMoreRecent: Boolean = true,
    overflowItems: List<OptionsMenuItem> = emptyList(),
    overflowExpanded: Boolean = false,
    onOverflowItemClick: (id: String) -> Unit = {},
    onOverflowDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (!fullScreen) {
            ReadingToolbar(
                state = toolbar,
                icons = toolbarIcons,
                callbacks = toolbarCallbacks,
                searchMoreRecent = searchMoreRecent,
                overflowItems = overflowItems,
                overflowExpanded = overflowExpanded,
                onOverflowItemClick = onOverflowItemClick,
                onOverflowDismiss = onOverflowDismiss,
            )
        }
        SplitContent(
            layout = layout,
            onWindowActivated = onWindowActivated,
            onSeparatorCommitted = onSeparatorCommitted,
            pane = pane,
            modifier = Modifier.weight(1f),
        )
    }
}
