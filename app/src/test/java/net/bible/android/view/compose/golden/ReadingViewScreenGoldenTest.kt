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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
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

    private fun screen(fullScreen: Boolean, tabBar: (@Composable () -> Unit)? = null): @Composable () -> Unit = {
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
        )
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun toolbarOn() = captureMatrix("ReadingViewScreen", "toolbarOn", content = screen(fullScreen = false))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun fullScreen() = captureGolden("ReadingViewScreen", "fullScreen", EDGE_MODE, content = screen(fullScreen = true))

    // Covers the tabBar slot (Plan-A Task 6): rendered below SplitContent only when non-null.
    // Uses a distinctly-colored placeholder Box (the real WindowTabBar is host-composed by
    // Plan A Task 7) so the rail's position/extent below the pane is visible in the PNG.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withRail() = captureGolden(
        "ReadingViewScreen", "withRail", EDGE_MODE,
        content = screen(
            fullScreen = false,
            tabBar = {
                Box(Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.secondaryContainer)) {}
            },
        ),
    )
}
