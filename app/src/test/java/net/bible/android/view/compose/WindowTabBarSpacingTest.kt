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

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
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
import kotlin.test.assertTrue

/**
 * Gaps in the window-tab rail: tabs of one group sit 2dp apart (a hairline, tighter than the old 6dp)
 * while the semantic gap between groups stays wide (17dp, divider included). Measured as the
 * distance between the tabs' label start edges, which sit at the same offset inside every 40dp tab.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
class WindowTabBarSpacingTest {
    @get:Rule val compose = createComposeRule()

    private fun win(id: String, pin: Boolean = false) = WindowSnapshot(
        id = id,
        state = WindowStateValue.VISIBLE,
        weight = 1.0f,
        isVisible = true,
        isPinMode = pin,
        isSynchronised = false,
        syncGroup = 0,
        isLinksWindow = false,
    )

    private fun labelStart(label: String): Dp =
        compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot().left

    @Test
    fun tabsSitCloseAndGroupsKeepAWideGap() {
        val model = buildWindowTabBar(
            WindowLayoutState(
                windows = listOf(win("P", pin = true), win("N1"), win("N2")),
                activeWindowId = "N1",
                maximizedWindowId = null,
                reverseSplitMode = false,
                restoreButtonsVisible = true,
            ),
        )
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    WindowTabBar(
                        model = model,
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

        val tabWidth = 40.dp
        val inGroupGap = labelStart("N2") - labelStart("N1") - tabWidth
        val groupGap = labelStart("N1") - labelStart("P") - tabWidth

        assertTrue(inGroupGap in 1.5.dp..2.5.dp, "gap between tabs of one group: $inGroupGap")
        assertTrue(groupGap in 16.dp..18.dp, "gap between groups (separator included): $groupGap")
    }
}
