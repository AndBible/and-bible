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

package net.bible.android.view.compose.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedcore.ai.reading.AgentLogEntryVd
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentLogUiState
import net.bible.sharedcore.ai.reading.agentPanelHeight
import net.bible.sharedcore.ai.reading.LogEntryKind
import net.bible.sharedcore.ai.reading.LogEntryStatus
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.ai.reading.AgentLogPanel
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.WindowTabBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers [ReadingViewScreen]'s Plan-B addition over [SplitContent]/[net.bible.sharedui.reading.ReadingToolbar]
 * (both already golden-covered on their own in [ReadingSplitGoldenTest] / [ReadingToolbarGoldenTest]):
 * the `fullScreen` flag that drops the toolbar row entirely rather than merely hiding it, so the
 * split reclaims the full height.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingViewScreenGoldenTest {

    // Same drawables as ReadingToolbarGoldenTest (main_bible_view.xml's toolbarLayout buttons).
    @Composable
    private fun icons() = ReadingToolbarIcons(
        home = painterResource(R.drawable.ic_menu),
        search = painterResource(R.drawable.ic_search_24dp),
        speak = painterResource(R.drawable.ic_baseline_headphones_24),
        strongs = painterResource(R.drawable.ic_strongs_hebrew),
        bible = painterResource(R.drawable.ic_bible_24dp),
        commentary = painterResource(R.drawable.ic_commentary),
        workspace = painterResource(R.drawable.ic_workspace_solid_24dp),
        overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
        sync = painterResource(R.drawable.ic_syncdb_24dp),
    )

    private val noopCallbacks = ReadingToolbarCallbacks(
        onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
        onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
        onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
        onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
    )

    // Requests Bible + Search + Workspace (3 quick buttons) — fits comfortably at the `land`
    // width budget, same as ReadingToolbarGoldenTest.fullState, so the toolbar-on render shows a
    // representative, non-truncated toolbar row.
    private val toolbarState = ToolbarState(
        pageTitle = "Genesis 1:1-3",
        documentTitle = "King James Version (KJV)",
        syncRunning = false,
        showBible = true,
        showCommentary = false,
        showStrongs = false,
        strongsMode = 1,
        searchable = true,
        speakable = false,
        speakStopped = true,
    )

    private val layout = WindowLayoutState(
        windows = listOf(
            WindowSnapshot(
                id = "A", state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
                isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
            ),
        ),
        activeWindowId = "A", maximizedWindowId = null, reverseSplitMode = false,
        restoreButtonsVisible = true,
    )

    // Single distinctly-colored pane so the split area's extent (full height vs. the height left
    // below the toolbar row) is visible in the captured PNG, same technique as ReadingSplitGoldenTest.
    private val pane: @Composable (String) -> Unit = { id ->
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
            Alignment.Center,
        ) { Text(id) }
    }

    private fun screen(
        fullScreen: Boolean,
        tabBar: (@Composable (applyNavBarInset: Boolean) -> Unit)? = null,
        agentLog: (@Composable (
            applyNavBarInset: Boolean,
            maxHeightDp: Float,
            collapsedHeightDp: Float,
            onCollapsedHeightMeasured: (Float) -> Unit,
        ) -> Unit)? = null,
        speakBar: (@Composable (applyNavBarInset: Boolean) -> Unit)? = null,
    ): @Composable () -> Unit = {
        ReadingViewScreen(
            layout = layout,
            toolbar = toolbarState,
            toolbarIcons = icons(),
            toolbarCallbacks = noopCallbacks,
            fullScreen = fullScreen,
            onWindowActivated = {},
            onSeparatorCommitted = { _, _, _, _ -> },
            pane = pane,
            tabBar = tabBar,
            agentLog = agentLog,
            speakBar = speakBar,
            agentLogVisible = agentLog != null,
            speakBarVisible = speakBar != null,
        )
    }

    // Covers the agentLog slot (Batch 12e-B Task 5; a bottom-anchored OVERLAY since round 12b §4).
    // Uses the real AgentLogPanel (already golden-covered on its own in AgentLogPanelGoldenTest)
    // with a fixed running state, animateStatus = false for a deterministic capture.
    private val agentLogEntries = listOf(
        AgentLogEntryVd("1", LogEntryKind.INFO, LogEntryStatus.COMPLETED, "Iteration 1"),
        AgentLogEntryVd("2", LogEntryKind.ACTION, LogEntryStatus.PENDING, "Reading John 3", details = "book=John"),
    )
    private val agentLogRunningExpanded = AgentLogUiState(
        visible = true, expanded = true,
        snapshot = AgentLogSnapshot(
            running = true, entries = agentLogEntries, statusText = "Reading John 3",
            headerCost = "$0.03", defaultModelText = "gpt-4o",
        ),
    )
    private val agentLogRunningCollapsed = agentLogRunningExpanded.copy(expanded = false)

    /**
     * The `agentLog` slot wired the way `ComposeReadingViewHost` wires it, minus the live
     * controller: the height comes from the same pure `agentPanelHeight`, and the screen's own
     * measured collapsed height ([collapsedHeightDp]) is what feeds it.
     *
     * [seedCollapsedHeight] exists because of how the reservation is populated (fix round 1,
     * Important 3). A COLLAPSED panel lays out intrinsically and reports its height, so the screen's
     * reservation is measured for real and needs no seed. An EXPANDED panel never reports one (the
     * measurement is guarded on `!expanded`, deliberately — reporting while expanded would make the
     * reservation track the drag), so a fixture that starts expanded would render with a 0dp
     * reservation and could not be compared against the collapsed capture at all. Seeding it with the
     * header's own `heightIn(min = 48.dp)` reproduces the state production is in for the whole common
     * case: shown collapsed, measured, then expanded. `SideEffect` rather than `LaunchedEffect` so it
     * does not depend on a coroutine dispatch inside the capture.
     *
     * `applyNavBarInset` is deliberately passed as `false` everywhere here, as it was before this
     * round: system insets are zero in a Roborazzi capture, so threading the real flag through would
     * change no pixel while suggesting the inset-ownership flip is covered. It is not — that stays a
     * device-pass check.
     */
    private fun agentLogSlot(
        state: AgentLogUiState,
        seedCollapsedHeight: Float? = null,
    ): @Composable (Boolean, Float, Float, (Float) -> Unit) -> Unit =
        { _, maxHeightDp, collapsedHeightDp, onCollapsedHeightMeasured ->
            if (seedCollapsedHeight != null) {
                SideEffect { onCollapsedHeightMeasured(seedCollapsedHeight) }
            }
            AgentLogPanel(
                state, animateStatus = false,
                statusIcon = painterResource(R.drawable.icon_robot),
                applyNavBarInset = false,
                panelHeightDp = if (state.expanded) {
                    agentPanelHeight(state, collapsedHeightDp, maxHeightDp)
                } else null,
                onHeightDragStarted = {},
                onHeightDrag = {},
                onCollapsedHeightMeasured = onCollapsedHeightMeasured,
                onToggleExpanded = {}, onStop = {},
                onClose = {}, onModelSelectorClick = {}, onModelChosen = {}, onModelPickerDismiss = {},
                onRawLogClick = {},
            )
        }

    /**
     * The reading view with an EXPANDED agent panel. Read together with [withAgentLogCollapsed],
     * which is captured at the same canvas and qualifiers ON PURPOSE: the pane is one flat colour
     * with its window id centred in it, so the label's vertical position is a direct read-out of the
     * pane's height. If the label sits at the same height in both captures, the expanded panel did
     * not reflow the pane — which is the whole invariant of round 12b §4, and nothing else in the
     * suite covers it.
     *
     * What this capture on its own does NOT prove: an expanded overlay above a reserved band and an
     * in-flow panel of the same height are pixel-identical everywhere except the pane's own extent,
     * so a single capture cannot tell them apart (fix round 1, Important 2 — the previous comment
     * here claimed it could). Two further caveats worth stating rather than glossing: the pair is
     * only comparable while [agentLogSlot]'s 48dp seed matches what the collapsed capture actually
     * measures, and the seed reaches the screen through a `SideEffect` + recomposition, so a
     * recorded PNG whose pane label sits BELOW the panel's top edge means the seed never landed and
     * the comparison is void.
     */
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withAgentLog() = captureGolden(
        "ReadingViewScreen", "withAgentLog", EDGE_MODE, heightDp = AGENT_OVERLAY_CANVAS_DP,
        content = screen(
            fullScreen = false,
            agentLog = agentLogSlot(agentLogRunningExpanded, seedCollapsedHeight = 48f),
        ),
    )

    /**
     * The other half of the pair above — and the only capture anywhere of a COLLAPSED panel in the
     * reading view, i.e. of the in-flow `Spacer` reservation, of the `!expanded` measurement guard
     * that fills it, and of the invariant that a collapsed panel covers nothing (fix round 1,
     * Important 3). Same canvas and qualifiers as [withAgentLog] so the two are directly comparable.
     */
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withAgentLogCollapsed() = captureGolden(
        "ReadingViewScreen", "withAgentLogCollapsed", EDGE_MODE, heightDp = AGENT_OVERLAY_CANVAS_DP,
        content = screen(
            fullScreen = false,
            agentLog = agentLogSlot(agentLogRunningCollapsed),
        ),
    )

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun toolbarOn() = captureMatrix("ReadingViewScreen", "toolbarOn", content = screen(fullScreen = false))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun fullScreen() = captureGolden("ReadingViewScreen", "fullScreen", EDGE_MODE, content = screen(fullScreen = true))

    // A small representative multi-window model for the rail — mirrors WindowTabBarGoldenTest's
    // own fixture shape (a pinned "P" + active non-pinned "N1"), NOT the single-window `layout`
    // above (which is the pane's own layout and unrelated to what the rail displays).
    private val railWindows = listOf(
        WindowSnapshot(
            id = "P", state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
            isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
        ),
        WindowSnapshot(
            id = "N1", state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
            isPinMode = false, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
        ),
    )
    private val railModel = buildWindowTabBar(
        WindowLayoutState(
            windows = railWindows, activeWindowId = "N1", maximizedWindowId = null,
            reverseSplitMode = false, restoreButtonsVisible = true,
        ),
    )

    // Covers the tabBar slot (Plan-A Task 6; restyled A/B batch 1 Task 5/F1): floated over the
    // split's bottom-end corner via SplitContent's `railOverlay` rather than rendered in-flow
    // below it. Uses the REAL `WindowTabBar` (same technique as `withSpeakBar` below, which uses
    // the real `SpeakTransportBar` "just to prove the slot stacks correctly here") rather than a
    // placeholder — a hand-rolled placeholder would have to duplicate the bar's compact/
    // end-packed/background styling to be representative, which would keep passing after a
    // regression in the real bar. This golden proves the rail floats in the bottom-END corner
    // OVER the pane, with the pane's own content extending underneath it, instead of taking a
    // full-width band below the pane (the pre-fix behaviour).
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withRail() = captureGolden(
        "ReadingViewScreen", "withRail", EDGE_MODE,
        content = screen(
            fullScreen = false,
            tabBar = { _ ->
                WindowTabBar(
                    model = railModel,
                    onRestore = {},
                    onWindowLongPress = {},
                    onAddWindow = {},
                    onUnMaximise = {},
                    onToggleCollapse = {},
                    windowLabel = { it.id },
                )
            },
        ),
    )

    // Covers the speakBar slot (Batch 12f Task 5): rendered between agentLog and tabBar only when
    // non-null. Uses the real SpeakTransportBar (its own full mode/RTL matrix is Task 6's golden
    // test) with a representative playing state, just to prove the slot stacks correctly here.
    private val speakTransportPlaying = SpeakTransportVd(
        visible = true, playing = true, stopped = false, statusText = "Reading John 3",
        speedPercent = 150, bookmarkButtonVisible = true,
    )

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withSpeakBar() = captureMatrix(
        "ReadingViewScreen", "withSpeakBar", heightDp = 640,
        content = screen(
            fullScreen = false,
            speakBar = { _ ->
                SpeakTransportBar(
                    speakTransportPlaying,
                    onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {},
                    onPrev = {}, onNext = {}, onBookmark = {}, onConfig = {},
                )
            },
        ),
    )

    /**
     * Both bottom surfaces at once (fix round 1, Important 3): [withAgentLog]/[withAgentLogCollapsed]
     * have no speak bar and [withSpeakBar] has no panel, so the overlay's bottom anchor —
     * `padding(bottom = <measured speak-bar height>)`, the one thing that keeps the panel ON TOP of
     * the bar instead of over it — was unexercised. The panel is collapsed here because that is the
     * case where a wrong anchor is unmistakable: a collapsed panel is the same height as its
     * reservation, so any anchoring error shows up as the bar being covered or as a gap between the
     * two surfaces. The inset-ownership flip that also happens in this configuration is NOT covered —
     * see [agentLogSlot]'s kdoc.
     *
     * Round 14b §6, corrected in fix round 1: measurement shows this capture proves only half of
     * §6. The boundary loses its 15px rounded-corner notch at both edges (rows 568-583, x 0-14 and
     * x 455-469) and gains no colour step, since both surfaces are `tonalElevation = 3.dp` — and the
     * CENTRE of the boundary was already byte-identical to the panel-alone capture before this
     * change. It does NOT prove the shadow half: this renderer draws no Compose elevation shadow in
     * either version, so the capture would look the same whether `shadowElevation` were 8.dp or
     * 0.dp. The shadow half is device-pass-only; the round's device checklist is its only coverage.
     * Still light-only per spec §9 — the bar's own four-mode matrix is
     * `SpeakTransportBarGoldenTest.underAgentPanel_matrix`.
     */
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withAgentLogAndSpeakBar() = captureGolden(
        "ReadingViewScreen", "withAgentLogAndSpeakBar", EDGE_MODE, heightDp = 640,
        content = screen(
            fullScreen = false,
            agentLog = agentLogSlot(agentLogRunningCollapsed),
            speakBar = { _ ->
                SpeakTransportBar(
                    speakTransportPlaying,
                    onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {},
                    onPrev = {}, onNext = {}, onBookmark = {}, onConfig = {},
                    // Round 14b §6: the panel above owns the corners and the shadow, so the bar
                    // goes square and flat. Hardcoded rather than routed through
                    // `speakBarOwnsTopEdge` on purpose: this capture states the CONFIGURATION it is
                    // a picture of, and the rule that derives the flag from `agentLogVisible` is
                    // unit-tested in `BottomBarInsetsTest` and applied by `ComposeReadingViewHost`.
                    // Deriving it here would make the golden agree with the host by construction
                    // and stop being independent evidence.
                    ownsTopEdge = false,
                )
            },
        ),
    )
}

/**
 * The canvas height the agent-overlay pair is captured at. Tall enough that the pane's centred
 * window-id label — the read-out the pair compares — stays clear of a default-height expanded panel
 * (308dp) instead of being hidden behind it: 800 - 56dp toolbar - 48dp reservation leaves a 696dp
 * pane whose centre line sits ~90dp above the panel's top edge. `withSpeakBar`'s 640 is the
 * precedent for overriding the height at all.
 *
 * The `48dp` in that arithmetic is [agentLogSlot]'s seed, which is in turn the panel header's own
 * `heightIn(min = 48.dp)` — one of the five unconnected places the number 48 now appears (whole-branch
 * review; the status doc's round-12b entry lists all five). Change the seed and this comment's
 * arithmetic goes stale with it.
 */
private const val AGENT_OVERLAY_CANVAS_DP = 800
