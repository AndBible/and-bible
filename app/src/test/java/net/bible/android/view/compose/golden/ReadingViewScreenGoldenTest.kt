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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedcore.ai.reading.AgentLogEntryVd
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentLogUiState
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
        tabBar: (@Composable () -> Unit)? = null,
        agentLog: (@Composable () -> Unit)? = null,
        speakBar: (@Composable () -> Unit)? = null,
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
        )
    }

    // Covers the agentLog slot (Batch 12e-B Task 5): rendered between SplitContent and tabBar
    // only when non-null. Uses the real AgentLogPanel (already golden-covered on its own in
    // AgentLogPanelGoldenTest) with a fixed running+expanded state, animateStatus = false for a
    // deterministic capture.
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

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withAgentLog() = captureGolden(
        "ReadingViewScreen", "withAgentLog", EDGE_MODE,
        content = screen(
            fullScreen = false,
            agentLog = {
                AgentLogPanel(
                    agentLogRunningExpanded, animateStatus = false, onToggleExpanded = {}, onStop = {},
                    onClose = {}, onModelSelectorClick = {}, onModelChosen = {}, onModelPickerDismiss = {},
                    onRawLogClick = {},
                )
            },
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
            tabBar = {
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
            speakBar = {
                SpeakTransportBar(
                    speakTransportPlaying,
                    onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {},
                    onPrev = {}, onNext = {}, onBookmark = {}, onConfig = {},
                )
            },
        ),
    )
}
