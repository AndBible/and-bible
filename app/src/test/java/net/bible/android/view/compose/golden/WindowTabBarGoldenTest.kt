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

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.reading.WindowTabBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers Batch 12b-followon-A Task 5's [WindowTabBar] — the shared restore rail rendering
 * [WindowTabBarModel] (Task 4's pure derivation from `WindowLayoutState`), reusing
 * [net.bible.sharedui.reading.WindowButton] (Task 3) for every tab. Mirrors classic
 * `SplitBibleArea`'s restore-button strip
 * (`app/src/main/java/net/bible/android/view/activity/page/screen/SplitBibleArea.kt`
 * `rebuildRestoreButtons()`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WindowTabBarGoldenTest {

    private fun win(
        id: String,
        pin: Boolean = false,
        links: Boolean = false,
        state: WindowStateValue = WindowStateValue.VISIBLE,
    ) = WindowSnapshot(
        id = id,
        state = state,
        weight = 1.0f,
        isVisible = state == WindowStateValue.VISIBLE,
        isPinMode = pin,
        isSynchronised = false,
        syncGroup = 0,
        isLinksWindow = links,
    )

    private fun layout(
        windows: List<WindowSnapshot>,
        active: String = windows.firstOrNull()?.id ?: "",
        maximized: String? = null,
        restoreVisible: Boolean = true,
    ) = WindowLayoutState(
        windows = windows,
        activeWindowId = active,
        maximizedWindowId = maximized,
        reverseSplitMode = false,
        restoreButtonsVisible = restoreVisible,
    )

    // Exactly one (non-closed) window -> RailLeading.AddWindow, no tabs shown.
    private val singleModel = buildWindowTabBar(layout(windows = listOf(win("A"))))

    // Pinned "P" | non-pinned "N1" (active), "N2" (minimised) -> one GroupSeparator between the
    // pinned and non-pinned groups, 3 tabs total, N2 rendered dimmed/dashed (minimised look).
    private val multiWindows = listOf(win("P", pin = true), win("N1"), win("N2", state = WindowStateValue.MINIMISED))
    private val multiExpandedModel = buildWindowTabBar(layout(windows = multiWindows, active = "N1", restoreVisible = true))

    // Same windows, but the rail is collapsed -> CollapseToggle(expanded = false), no tabs shown.
    private val multiCollapsedModel = buildWindowTabBar(layout(windows = multiWindows, active = "N1", restoreVisible = false))

    // A window is maximised -> RailLeading.Unmaximise, no tabs, regardless of window count.
    private val maximisedModel = buildWindowTabBar(layout(windows = listOf(win("A"), win("B")), maximized = "A"))

    private fun screen(model: WindowTabBarModel): @Composable () -> Unit = {
        WindowTabBar(
            model = model,
            onRestore = {},
            onWindowLongPress = {},
            onAddWindow = {},
            onUnMaximise = {},
            onToggleCollapse = {},
            windowLabel = { it.id },
            windowIcon = { null },
        )
    }

    // Widest state (leading + 3 tabs + a separator) — captured in `land` (same technique as
    // WindowButtonGoldenTest/ReadingToolbarGoldenTest/ReadingViewScreenGoldenTest) so every entry
    // is fully visible, uncropped, in one row.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun multiExpanded() = captureMatrix("WindowTabBar", "multiExpanded", content = screen(multiExpandedModel))

    // RTL layout direction (Arabic locale, land for the same width headroom) — the rail's
    // Arrangement.End + LazyRow ordering should mirror: entries read right-to-left.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar-land")
    fun multiExpanded_rtl() = captureRtl("WindowTabBar", "multiExpanded", content = screen(multiExpandedModel))

    @Test
    fun single() = captureGolden("WindowTabBar", "single", EDGE_MODE, content = screen(singleModel))

    @Test
    fun multiCollapsed() = captureGolden("WindowTabBar", "multiCollapsed", EDGE_MODE, content = screen(multiCollapsedModel))

    @Test
    fun maximised() = captureGolden("WindowTabBar", "maximised", EDGE_MODE, content = screen(maximisedModel))
}
