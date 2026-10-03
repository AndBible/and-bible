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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.SplitContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F69 (and F64 on API 23–29). The keyboard shrinks the split's box, not the window. In a portrait
 * window with the keyboard up the box is wider than tall, and a split that decides from the box goes
 * side by side (the latch only masked that until a rotation reset it). The orientation must follow
 * the WINDOW, as classic's `CommonUtils.isPortrait` and `BibleView.isSplitVertically` do.
 *
 * The Robolectric default display is portrait (w320dp h470dp); the split is forced into a
 * landscape-shaped 300x150dp box, which is exactly the keyboard-up geometry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SplitFollowsWindowShapeTest {
    @get:Rule val compose = createComposeRule()

    private fun win(id: String) = WindowSnapshot(
        id = id, state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
        isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
    )

    private fun twoWindows() = WindowLayoutState(
        windows = listOf(win("A"), win("B")), activeWindowId = "A", maximizedWindowId = null,
        reverseSplitMode = false, restoreButtonsVisible = true,
    )

    @Test
    fun aPortraitWindowStacksEvenWhenTheSplitBoxIsLandscapeShaped() {
        compose.setContent {
            Box(Modifier.size(300.dp, 150.dp)) {
                SplitContent(
                    layout = twoWindows(),
                    onWindowActivated = {},
                    onSeparatorCommitted = { _, _, _, _ -> },
                    pane = { id -> Box(Modifier.fillMaxSize().testTag("pane-$id")) },
                )
            }
        }
        val a = compose.onNodeWithTag("pane-A").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("pane-B").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "portrait window: the panes must be STACKED (B below A), not side by side -- " +
                "A=$a B=$b (F69: the split followed the keyboard-shrunk box)",
            b.top >= a.bottom - 1f && b.left <= a.left + 1f,
        )
    }
}
