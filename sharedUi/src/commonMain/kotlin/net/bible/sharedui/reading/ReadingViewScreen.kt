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

import androidx.compose.foundation.layout.BoxScope
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
 *
 * [tabBar] is an optional trailing slot rendered below [SplitContent] (intrinsic height; the
 * split keeps `Modifier.weight(1f)` so the rail doesn't shrink it further). It's `null` by
 * default, in which case nothing is rendered there — the restore-rail host (a later task) is
 * the only caller expected to pass it.
 *
 * [agentLog] is an optional slot rendered between [SplitContent] and [tabBar] (intrinsic height,
 * same as [tabBar]) — i.e. it sits directly above the restore rail. It's `null` by default, in
 * which case nothing is rendered there; the host is expected to pass the live agent-log panel
 * (`AgentLogPanel`) here. Ordering contract for later slots stacked in this same gap: a future
 * (Batch 12f) speak-transport bar stacks directly under [agentLog] (i.e. between it and [tabBar]).
 *
 * [paneOverlay] is forwarded verbatim to [SplitContent]'s slot of the same name — an optional
 * per-pane overlay (e.g. the floating ☰ window button), composed inside every visible pane. `null`
 * by default, in which case nothing extra is drawn.
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
    tabBar: (@Composable () -> Unit)? = null,
    agentLog: (@Composable () -> Unit)? = null,
    paneOverlay: (@Composable BoxScope.(windowId: String) -> Unit)? = null,
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
            paneOverlay = paneOverlay,
        )
        agentLog?.invoke()
        tabBar?.invoke()
    }
}
