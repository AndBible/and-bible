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
        synced: Boolean = false,
        syncGroup: Int = 0,
    ) = WindowSnapshot(
        id = id,
        state = state,
        weight = 1.0f,
        isVisible = state == WindowStateValue.VISIBLE,
        isPinMode = pin,
        isSynchronised = synced,
        syncGroup = syncGroup,
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

    // Same 3-window layout as multiExpandedModel, but "N1" is synchronised in (raw 0-based) sync
    // group 0 -> WindowTabBar must map this to WindowButton's 1-based syncGroup=1, so the tab
    // shows a sync badge ("classic parity" final-review fix: a synchronised window in group 0 is
    // the common case and must NOT be silently badge-less).
    private val multiWindowsSynced = listOf(
        win("P", pin = true),
        win("N1", synced = true, syncGroup = 0),
        win("N2", state = WindowStateValue.MINIMISED),
    )
    private val multiExpandedSyncedModel =
        buildWindowTabBar(layout(windows = multiWindowsSynced, active = "N1", restoreVisible = true))

    private fun screen(model: WindowTabBarModel, windowTopLabel: (WindowSnapshot) -> String? = { null }): @Composable () -> Unit = {
        WindowTabBar(
            model = model,
            onRestore = {},
            onWindowLongPress = {},
            onAddWindow = {},
            onUnMaximise = {},
            onToggleCollapse = {},
            windowLabel = { it.id },
            windowIcon = { null },
            windowTopLabel = windowTopLabel,
        )
    }

    // Task 4 (F2b): "N1" (the active tab) supplies a top label, "P"/"N2" do not — so this single
    // capture shows BOTH WindowButton's Rail two-row look (topLabel != null) and its plain one-row
    // look (topLabel == null) side by side, the only golden context exercising WindowTabBar's own
    // `windowTopLabel` wiring (Task 3's WindowButton goldens cover the button in isolation, not a
    // real rail).
    private val multiExpandedTopLabel: (WindowSnapshot) -> String? = { window -> if (window.id == "N1") "Gen 1" else null }

    // Widest state (leading + 3 tabs + a separator) — captured in `land` (same technique as
    // WindowButtonGoldenTest/ReadingToolbarGoldenTest/ReadingViewScreenGoldenTest) so every entry
    // is fully visible, uncropped, in one row.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun multiExpanded() = captureMatrix("WindowTabBar", "multiExpanded", content = screen(multiExpandedModel, multiExpandedTopLabel))

    // RTL layout direction (Arabic locale, land for the same width headroom) — the rail's
    // Arrangement.End + LazyRow ordering should mirror: entries read right-to-left.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar-land")
    fun multiExpanded_rtl() = captureRtl("WindowTabBar", "multiExpanded", content = screen(multiExpandedModel))

    // Final-review fix coverage: a synchronised window (raw syncGroup=0) must render a sync badge
    // ("1", 1-based) on its tab — same land/matrix technique as multiExpanded above.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun multiExpandedSynced() = captureMatrix("WindowTabBar", "multiExpandedSynced", content = screen(multiExpandedSyncedModel))

    @Test
    fun single() = captureGolden("WindowTabBar", "single", EDGE_MODE, content = screen(singleModel))

    @Test
    fun multiCollapsed() = captureGolden("WindowTabBar", "multiCollapsed", EDGE_MODE, content = screen(multiCollapsedModel))

    @Test
    fun maximised() = captureGolden("WindowTabBar", "maximised", EDGE_MODE, content = screen(maximisedModel))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun multiExpanded_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowTabBar", "multiExpanded", mode, content = screen(multiExpandedModel, multiExpandedTopLabel)) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun multiExpandedSynced_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowTabBar", "multiExpandedSynced", mode, content = screen(multiExpandedSyncedModel)) }
    }

    @Test
    fun single_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowTabBar", "single", mode, content = screen(singleModel)) }
    }

    @Test
    fun multiCollapsed_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowTabBar", "multiCollapsed", mode, content = screen(multiCollapsedModel)) }
    }

    @Test
    fun maximised_mono() {
        MONO_MODES.forEach { mode -> captureGolden("WindowTabBar", "maximised", mode, content = screen(maximisedModel)) }
    }
}
