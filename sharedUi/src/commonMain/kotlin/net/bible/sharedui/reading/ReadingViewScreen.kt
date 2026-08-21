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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.reading.agentLogOwnsNavBarInset
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
 * [agentLog] is an optional slot rendered as a bottom-anchored OVERLAY over the split rather than
 * in-flow below it (round 12b §4). The in-flow stack reserves only the panel's COLLAPSED height, so
 * a collapsed panel covers nothing and an expanded one grows upward over the panes instead of
 * reflowing them. It's `null` by default, in which case nothing is rendered there; the host is
 * expected to pass the live agent-log panel (`AgentLogPanel`) here.
 *
 * [speakBar] is an optional slot (intrinsic height) rendered in-flow at the very bottom, below the
 * split and below [agentLog]'s reservation — the Batch-12f speak-transport bar. It's `null` by
 * default, in which case nothing is rendered there; the host is expected to pass the live
 * `SpeakTransportBar` here. [agentLog]'s overlay is bottom-padded by this bar's measured height, so
 * the panel sits exactly on top of it rather than over it.
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
    /**
     * The agent-log panel, rendered as a bottom-anchored OVERLAY rather than an in-flow child
     * (round 12b §4). The parameters are what only this screen knows: whether the panel owns the
     * navigation-bar inset, how far up it may be dragged (`maxHeightDp`, the distance from the top of
     * the reading area to the top of the bottom bars — i.e. the bottom of the toolbar), and the
     * callback by which it reports its collapsed height so this screen can reserve exactly that much.
     */
    agentLog: (@Composable (
        applyNavBarInset: Boolean,
        maxHeightDp: Float,
        onCollapsedHeightMeasured: (Float) -> Unit,
    ) -> Unit)? = null,
    /** The speak transport bar. Same `applyNavBarInset` contract as [agentLog]. */
    speakBar: (@Composable (applyNavBarInset: Boolean) -> Unit)? = null,
    /**
     * Whether [agentLog] will actually render. The slot self-hides, so it cannot report this, and
     * this screen needs it to decide inset ownership (and, from round 12b Task 6, how much space to
     * reserve for the collapsed panel).
     */
    agentLogVisible: Boolean = false,
    /** Whether [speakBar] will actually render — see [agentLogVisible]. */
    speakBarVisible: Boolean = false,
    searchBar: ReadingSearchBarState? = null,
    searchBarCallbacks: ReadingSearchBarCallbacks? = null,
    paneOverlay: (@Composable BoxScope.(windowId: String) -> Unit)? = null,
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
    overflowIcon: @Composable (iconKey: String) -> Painter? = { null },
    /** Per-pane background colour, passed straight to [SplitContent] — see its kdoc (A/B batch 4a F5). */
    paneBackground: (windowId: String) -> Color? = { null },
) {
    val density = LocalDensity.current
    // Round 12b §4: the agent panel overlays the content when expanded instead of shrinking the
    // panes, so it lives in layer 2 of a Box while layer 1 reserves only its COLLAPSED height.
    // Collapsed, the reservation equals the panel and nothing is covered; expanded, the panel grows
    // upward out of a reservation that does not grow with it, so the panes never reflow. The panes
    // are WebViews -- re-measuring them once per drag frame would mean a JS relayout per frame.
    var splitHeightDp by remember { mutableStateOf(0f) }
    var bottomBarsHeightDp by remember { mutableStateOf(0f) }
    var collapsedAgentHeightDp by remember { mutableStateOf(0f) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                // Measured so the panel's drag ceiling can be "the bottom of the toolbar": that is
                // this height plus the panel's own reservation right below it.
                modifier = Modifier.weight(1f).onSizeChanged {
                    splitHeightDp = with(density) { it.height.toDp() }.value
                },
                paneOverlay = paneOverlay,
                bottomOverlay = bottomOverlay,
                railOverlay = tabBar?.let { bar -> { Box(Modifier.align(Alignment.BottomEnd)) { bar() } } },
                paneBackground = paneBackground,
            )
            // The overlay's footprint. Zero when the panel is hidden.
            if (agentLogVisible) Spacer(Modifier.height(collapsedAgentHeightDp.dp))
            // Measured so layer 2 can sit exactly on top of the speak bar rather than over it.
            Box(
                Modifier.onSizeChanged {
                    bottomBarsHeightDp = with(density) { it.height.toDp() }.value
                }
            ) {
                speakBar?.invoke(speakBarVisible)
            }
        }
        if (agentLog != null && agentLogVisible) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = bottomBarsHeightDp.dp)
            ) {
                // Round 12b §3: the bottom-most VISIBLE bar consumes the bottom navigation-bar inset
                // inside its own painted surface. The speak bar is below the panel, so it wins
                // whenever it is up.
                agentLog(
                    agentLogOwnsNavBarInset(agentLogVisible, speakBarVisible),
                    splitHeightDp + collapsedAgentHeightDp,
                ) { measured -> collapsedAgentHeightDp = measured }
            }
        }
    }
}
