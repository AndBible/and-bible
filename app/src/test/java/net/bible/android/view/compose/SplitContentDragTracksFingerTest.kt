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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.SplitContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #3336: dragging the divider between two stacked windows must move it exactly as far as the finger,
 * whatever the stored weights add up to. The weights are relative, so `0.5/0.5` and `2/2` render the
 * same split, but the drag math used to assume they averaged 1: a sum of 1 moved the divider twice as
 * fast as the finger, a sum of 4 half as fast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SplitContentDragTracksFingerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weightsSummingToOne() = dividerTracksFinger(weight = 0.5f)
    @Test fun weightsSummingToTwo() = dividerTracksFinger(weight = 1f)
    @Test fun weightsSummingToFour() = dividerTracksFinger(weight = 2f)

    private fun dividerTracksFinger(weight: Float) {
        val windows = listOf("A", "B").map { id ->
            WindowSnapshot(id, WindowStateValue.VISIBLE, weight, true, true, false, 0, false)
        }
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.size(300.dp, 400.dp).testTag("split")) {
                SplitContent(
                    WindowLayoutState(windows, "A", null, false, true),
                    onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                    pane = { id -> Box(Modifier.fillMaxSize().testTag("pane-$id")) },
                )
            }
        }
        compose.waitForIdle()
        fun paneAHeight() = compose.onNodeWithTag("pane-A").fetchSemanticsNode().boundsInRoot.height
        val dividerY = compose.onNodeWithTag("pane-A").fetchSemanticsNode().boundsInRoot.bottom + 2 * density
        val split = compose.onNodeWithTag("split")
        // First move past the touch slop; measure the finger-to-divider ratio after it.
        split.performTouchInput { down(Offset(centerX, dividerY)); moveBy(Offset(0f, 30f * density)) }
        compose.waitForIdle()
        val before = paneAHeight()
        val fingerPx = 60f * density
        split.performTouchInput { moveBy(Offset(0f, fingerPx)) }
        compose.waitForIdle()
        val grown = paneAHeight() - before
        split.performTouchInput { up() }
        assertEquals("pane A must grow by exactly the finger travel (weights $weight/$weight)", fingerPx, grown, 1.5f)
    }
}
