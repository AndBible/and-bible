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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState

/**
 * Top-level reading-view screen: the [ReadingToolbar] (unless [fullScreen]) stacked above the
 * split reading area ([SplitContent]) in a single `Column`. Plan A (prior task) was the reading
 * area only; Plan B (this task) folds the toolbar in, dropping it entirely — rather than merely
 * hiding it — when [fullScreen], so [SplitContent] reclaims the full height via
 * `Modifier.weight(1f)`.
 *
 * [tabBar] is an optional slot floated over the split's bottom-end corner rather than rendered
 * in-flow in this `Column`: it is forwarded into [SplitContent]'s `railOverlay` (a sibling of the
 * panes container, inside `SplitContent`'s own `BoxWithConstraints`, aligned `BottomEnd`), so it
 * overlaps the panes instead of taking a layout band from them — matching classic
 * `restoreButtonsContainer`'s constraint-only-to-`bottom`+`end` container
 * (`res/layout/split_bible_area.xml:31-38`), never a full-width row. It's `null` by default, in
 * which case nothing is rendered there — the restore-rail host (a later task) is the only caller
 * expected to pass it.
 *
 * [agentLog] is an optional slot rendered directly below [SplitContent], in-flow (intrinsic
 * height; the split keeps `Modifier.weight(1f)` so it doesn't shrink further). It's `null` by
 * default, in which case nothing is rendered there; the host is expected to pass the live
 * agent-log panel (`AgentLogPanel`) here.
 *
 * [speakBar] is an optional slot (intrinsic height) rendered directly under [agentLog], in-flow
 * and below the split — the Batch-12f speak-transport bar. It's `null` by default, in which case
 * nothing is rendered there; the host is expected to pass the live `SpeakTransportBar` here.
 * [tabBar]'s floating rail is composed OVER the split rather than joining this in-flow stack, and
 * it sits higher on screen than [agentLog]/[speakBar] — the same stacking classic uses, where
 * `restoreButtonsContainer` is lifted clear of the transport bar via
 * `translationY(-bottomOffset2)` (`SplitBibleArea.kt:619`) rather than being pushed down by it.
 *
 * [paneOverlay] is forwarded verbatim to [SplitContent]'s slot of the same name — an optional
 * per-pane overlay (e.g. the floating ☰ window button), composed inside every visible pane. `null`
 * by default, in which case nothing extra is drawn.
 *
 * [bottomOverlay] is forwarded verbatim to [SplitContent]'s slot of the same name — an optional
 * bottom-centre overlay shared across the whole split (e.g. the fullscreen bible-reference
 * overlay), composed as a sibling of the panes rather than inside any one of them. `null` by
 * default, in which case nothing extra is drawn.
 *
 * [bibleQuickDoc]/[commentaryQuickDoc]/[onQuickDocSelect]/[onQuickDocDismiss] are forwarded
 * verbatim to [ReadingToolbar] — host-owned state for the Bible/Commentary quick-document picker
 * menus (Batch 12g). All four default to collapsed/empty/no-op so existing call sites and their
 * goldens are unaffected.
 *
 * [overflowIcon] is forwarded verbatim to [ReadingToolbar]'s `overflowIcon` parameter — the host
 * lambda resolving each overflow-menu row's [OptionsMenuItem.iconKey] to a `Painter`. Defaulted to
 * always-`null` so existing call sites and their goldens are unaffected.
 *
 * [searchBar]/[searchBarCallbacks] are forwarded verbatim to [ReadingToolbar] — a non-null pair puts
 * the toolbar into F6's search mode. Both default to `null` so existing call sites and their goldens
 * are unaffected. Search mode does NOT alter the `if (!fullScreen)` guard: opening search leaves
 * fullscreen (Task 8) rather than drawing a toolbar over it.
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
    bibleQuickDoc: QuickDocMenuState = QuickDocMenuState(),
    commentaryQuickDoc: QuickDocMenuState = QuickDocMenuState(),
    onQuickDocSelect: (id: String) -> Unit = {},
    onQuickDocDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    tabBar: (@Composable () -> Unit)? = null,
    agentLog: (@Composable () -> Unit)? = null,
    speakBar: (@Composable () -> Unit)? = null,
    /**
     * Whether to reserve the bottom navigation-bar inset below the last bottom bar. The host
     * computes it with `bottomInsetReserved(agentLogVisible, speakBarVisible)`: the [agentLog] and
     * [speakBar] slots both hide themselves, so neither can tell whether it is the bottom-most one,
     * and this screen is the only place that knows their order. Defaulted to `false` so existing
     * call sites and their goldens are unaffected, and so a pane-only reading view keeps extending
     * under the navigation bar as classic does.
     */
    reserveBottomInset: Boolean = false,
    searchBar: ReadingSearchBarState? = null,
    searchBarCallbacks: ReadingSearchBarCallbacks? = null,
    paneOverlay: (@Composable BoxScope.(windowId: String) -> Unit)? = null,
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
    overflowIcon: @Composable (iconKey: String) -> Painter? = { null },
    /** Per-pane background colour, passed straight to [SplitContent] — see its kdoc (A/B batch 4a F5). */
    paneBackground: (windowId: String) -> Color? = { null },
) {
    Column(modifier.fillMaxSize()) {
        if (!fullScreen) {
            ReadingToolbar(
                state = toolbar,
                icons = toolbarIcons,
                callbacks = toolbarCallbacks,
                searchBar = searchBar,
                searchBarCallbacks = searchBarCallbacks,
                searchMoreRecent = searchMoreRecent,
                overflowItems = overflowItems,
                overflowExpanded = overflowExpanded,
                onOverflowItemClick = onOverflowItemClick,
                onOverflowDismiss = onOverflowDismiss,
                bibleQuickDoc = bibleQuickDoc,
                commentaryQuickDoc = commentaryQuickDoc,
                onQuickDocSelect = onQuickDocSelect,
                onQuickDocDismiss = onQuickDocDismiss,
                overflowIcon = overflowIcon,
            )
        }
        SplitContent(
            layout = layout,
            onWindowActivated = onWindowActivated,
            onSeparatorCommitted = onSeparatorCommitted,
            pane = pane,
            modifier = Modifier.weight(1f),
            paneOverlay = paneOverlay,
            bottomOverlay = bottomOverlay,
            railOverlay = tabBar?.let { bar -> { Box(Modifier.align(Alignment.BottomEnd)) { bar() } } },
            paneBackground = paneBackground,
        )
        agentLog?.invoke()
        speakBar?.invoke()
        if (reserveBottomInset) {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
