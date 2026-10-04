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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.reading.agentPanelDragCeiling
import net.bible.sharedcore.reading.agentLogOwnsNavBarInset
import net.bible.sharedcore.reading.railOwnsNavBarInset
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState

/**
 * Top-level reading-view screen: a two-layer `Box`. Layer 1 is the in-flow `Column` — the
 * [ReadingToolbar] (unless [fullScreen]) above the split reading area ([SplitContent]) above the
 * bottom bars; layer 2 is [agentLog]'s bottom-anchored overlay (round 12b §4 — it was a single
 * `Column` with the panel in-flow until then). Plan A (prior task) was the reading area only; Plan B
 * folds the toolbar in, dropping it entirely — rather than merely hiding it — when [fullScreen], so
 * [SplitContent] reclaims the full height via `Modifier.weight(1f)`.
 *
 * [tabBar] is an optional slot (its argument is whether it owns the nav bar's bottom inset; the host
 * pads with `readingRailInsetPadding(applyNavBarInset)`) floated over the split's bottom-end corner rather than rendered
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
 * [tabBar]'s floating rail is composed OVER the split rather than joining this in-flow stack, and it
 * sits higher on screen than a COLLAPSED [agentLog] and than [speakBar] — the same stacking classic
 * uses, where `restoreButtonsContainer` is lifted clear of the transport bar via
 * `translationY(-bottomOffset2)` (`SplitBibleArea.kt:619`) rather than being pushed down by it. That
 * parity is per-bar height, so it survives the panel becoming an overlay: the rail clears the space
 * the collapsed panel reserves. An EXPANDED panel does cover the rail, since layer 2 is drawn after
 * layer 1 and the rail lives inside [SplitContent] — a deliberate consequence of overlaying rather
 * than reflowing (the alternative, reflowing the panes to keep the rail visible, is the WebView
 * relayout the overlay exists to avoid), not a regression.
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
    /**
     * The keyboard shrink, supplied by the host (F59). Applied as a plain bottom padding on the
     * reading column -- never `Modifier.imePadding()`: that would lift the column a second time on
     * top of this padding and consume `ime`. While it is positive the column already clears the nav bar,
     * so the strip, Speak bar and agent panel are told not to pad it again (`applyNavBarInset` false).
     * `0.dp` on hosts that pad their own container.
     */
    imeBottomPadding: Dp = 0.dp,
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
    tabBar: (@Composable (applyNavBarInset: Boolean) -> Unit)? = null,
    /**
     * The agent-log panel, rendered as a bottom-anchored OVERLAY rather than an in-flow child
     * (round 12b §4). The parameters are what only this screen knows: whether the panel owns the
     * navigation-bar inset, how far up it may be dragged (`maxHeightDp`, the distance from the top of
     * the reading area to the top of the bottom bars — i.e. the bottom of the toolbar), the panel's
     * measured collapsed height, and the callback by which it reports that height so this screen can
     * reserve exactly that much.
     *
     * The panel's own drag callbacks are NOT threaded through here, deliberately: `onHeightDrag`,
     * `onHeightDragStarted`, `onToggleExpanded`, `onStop` and `onClose` are all controller-side, and
     * this screen has nothing to contribute to them. What it threads is what only it knows.
     *
     * `collapsedHeightDp` is handed DOWN rather than remembered by the caller for a specific reason
     * (fix round 1, Important 1). The slot is composed inside `if (agentLog != null &&
     * agentLogVisible)`, so it leaves the composition every time the panel hides — while this screen
     * survives that hide. `AgentLogController.hide()` (and the
     * auto-hide branch) clears `visible` but deliberately leaves `expanded` true, so the next run
     * auto-shows an already-EXPANDED panel, in a fresh slot composition, and the panel never reports
     * a collapsed height while expanded. A copy remembered in the slot would therefore be 0 for that
     * whole showing: the drag's collapse-snap and its lower clamp would both work against 0 instead
     * of the header height, and a downward drag would shrink the panel to a sliver and leave it
     * "expanded" there. This screen's own measurement survives the hide, so it is the single source
     * of truth — do not "simplify" this back into a `remember` at the call site.
     *
     * **What it does not survive** (whole-branch review, Minor 1 — this kdoc used to say the screen
     * "stays composed for as long as the reading view is mounted", which is not true).
     * `ComposeReadingViewHost.kt:2841` wraps the whole `ReadingViewScreen` call in `key(gen)`, and
     * `rebuild()` bumps `gen` on a workspace switch or a forced reload — which discards
     * [collapsedAgentHeightDp] along with the rest of this screen's state, while the controller (owned
     * by `install`, outside the key) keeps `expanded = true`. That is the very failure mode above,
     * for one window: the reservation is back to 0 and the drag's clamp/snap work against 0 until the
     * next collapse re-measures. It is narrower than the remembered-copy version (which lasted a whole
     * showing rather than until the next collapse) and it is not what this parameter is for, but it is
     * a real remaining window, not a case this design closes.
     */
    agentLog: (@Composable (
        applyNavBarInset: Boolean,
        maxHeightDp: Float,
        collapsedHeightDp: Float,
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
    /**
     * Painted under the side insets the split pads; the active pane's colour, so a side nav bar or
     * cutout band does not show the scaffold surface (F107).
     */
    edgeBackground: Color? = null,
) {
    val density = LocalDensity.current
    // Whether the column carries IME padding is this screen's own decision (not Compose's `ime`
    // inset), so the bars drop their nav-bar inset exactly when the column already clears it.
    val columnPadsIme = imeBottomPadding > 0.dp
    val railNavInset = railOwnsNavBarInset(agentLogVisible, speakBarVisible) && !columnPadsIme
    val agentNavInset = agentLogOwnsNavBarInset(agentLogVisible, speakBarVisible) && !columnPadsIme
    // Round 12b §4: the agent panel overlays the content when expanded instead of shrinking the
    // panes, so it lives in layer 2 of a Box while layer 1 reserves only its COLLAPSED height.
    // Collapsed, the reservation equals the panel and nothing is covered; expanded, the panel grows
    // upward out of a reservation that does not grow with it, so the panes never reflow. The panes
    // are WebViews -- re-measuring them once per drag frame would mean a JS relayout per frame.
    var splitHeightDp by remember { mutableStateOf(0f) }
    var bottomBarsHeightDp by remember { mutableStateOf(0f) }
    var collapsedAgentHeightDp by remember { mutableStateOf(0f) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(bottom = imeBottomPadding)) {
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
                modifier = Modifier.weight(1f)
                    // F107: painted BEFORE the padding, so it fills the bands the padding leaves.
                    .then(edgeBackground?.let { Modifier.background(it) } ?: Modifier)
                    // Correction C2: the reading tree is edge-to-edge, and nothing else pads a side nav
                    // bar (landscape 3-button) or a side cutout. The toolbar pads its own row the same
                    // way (ReadingToolbar's systemBars ∪ displayCutout, Horizontal).
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
                    )
                    .onSizeChanged { splitHeightDp = with(density) { it.height.toDp() }.value },
                paneOverlay = paneOverlay,
                bottomOverlay = bottomOverlay,
                railOverlay = tabBar?.let { bar -> { Box(Modifier.align(Alignment.BottomEnd)) { bar(railNavInset) } } },
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
                // `speakBarVisible`, not `!agentLogOwnsNavBarInset(...) && speakBarVisible`. The value
                // wanted is the latter — "is the speak bar the bottom-most visible bar" — and it
                // reduces to the former ONLY because there are exactly two bottom bars and the speak
                // bar is the lower of them, so it owns the inset whenever it is visible at all
                // (`agentLogOwnsNavBarInset` is `agentLogVisible && !speakBarVisible`, which is false
                // whenever `speakBarVisible` is true). Add a THIRD bottom bar below the speak bar and
                // this line becomes wrong while still compiling: go through the shared predicate then,
                // as the agent-log call site three lines down already does.
                speakBar?.invoke(speakBarVisible && !columnPadsIme)
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
                    agentNavInset,
                    agentPanelDragCeiling(splitHeightDp, collapsedAgentHeightDp),
                    collapsedAgentHeightDp,
                ) { measured -> collapsedAgentHeightDp = measured }
            }
        }
    }
}
