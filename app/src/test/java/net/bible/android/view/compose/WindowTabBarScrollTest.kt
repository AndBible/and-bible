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
package net.bible.android.view.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #2498: a tab added to an overflowing rail (typically a new links window, which starts minimised and
 * inactive, at the end of the strip) is scrolled into view, and so is the active window's tab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WindowTabBarScrollTest {
    @get:Rule val compose = createComposeRule()

    private fun win(id: String, links: Boolean = false) = WindowSnapshot(
        id = id,
        state = if (links) WindowStateValue.MINIMISED else WindowStateValue.VISIBLE,
        weight = 1.0f,
        isVisible = !links,
        isPinMode = false,
        isSynchronised = false,
        syncGroup = 0,
        isLinksWindow = links,
    )

    private val baseWindows = (1..8).map { win("W$it") }

    private fun layout(windows: List<WindowSnapshot>, active: String) = WindowLayoutState(
        windows = windows,
        activeWindowId = active,
        maximizedWindowId = null,
        reverseSplitMode = false,
        restoreButtonsVisible = true,
    )

    private var layoutState by mutableStateOf(layout(baseWindows, active = "W1"))

    private fun setRail() {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    // Room for only a few 40dp tabs, so eight windows overflow the strip.
                    Box(Modifier.width(160.dp)) {
                        WindowTabBar(
                            model = buildWindowTabBar(layoutState),
                            onRestore = {},
                            onWindowLongPress = {},
                            onAddWindow = {},
                            onUnMaximise = {},
                            onToggleCollapse = {},
                            windowLabel = { it.id },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun newLinksWindowTabIsScrolledIntoView() {
        setRail()
        compose.onNodeWithText("W1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("W8", useUnmergedTree = true).assertIsNotDisplayed()

        layoutState = layout(baseWindows + win("LINKS", links = true), active = "W1")
        compose.waitForIdle()

        compose.onNodeWithText("LINKS", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun activeWindowTabIsScrolledIntoView() {
        setRail()
        compose.onNodeWithText("W8", useUnmergedTree = true).assertIsNotDisplayed()

        layoutState = layout(baseWindows, active = "W8")
        compose.waitForIdle()

        compose.onNodeWithText("W8", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun removingATabDoesNotUndoManualScroll() {
        setRail()
        repeat(3) { compose.onRoot().performTouchInput { swipeLeft(durationMillis = 100) } }
        compose.waitForIdle()
        compose.onNodeWithText("W1", useUnmergedTree = true).assertIsNotDisplayed()

        layoutState = layout(baseWindows.filter { it.id != "W3" }, active = "W1")
        compose.waitForIdle()

        compose.onNodeWithText("W1", useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test
    fun bulkReplaceTargetsTheActiveTab() {
        setRail()
        val replaced = (1..8).map { win("X$it") }

        layoutState = layout(replaced, active = "X1")
        compose.waitForIdle()

        compose.onNodeWithText("X1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("X8", useUnmergedTree = true).assertIsNotDisplayed()
    }
}
