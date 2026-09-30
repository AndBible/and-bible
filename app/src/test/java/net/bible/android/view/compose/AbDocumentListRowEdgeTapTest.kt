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
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbDocumentListRow
import net.bible.sharedui.components.HorizontalGestureEdges
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F73 (fix batch 3 §2.2.2): a left-edge back gesture that the system does not claim ends as a click
 * on the edge-to-edge row, and in download mode a row click asks to download. A press that STARTS
 * inside a horizontal system-gesture edge must not click the row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbDocumentListRowEdgeTapTest {
    @get:Rule val compose = createComposeRule()

    private var clicks = 0

    private fun show() = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AbDocumentListRow(
                    title = "King James Version",
                    subtitle = AnnotatedString("KJV"),
                    onClick = { clicks++ },
                    onLongClick = {},
                    modifier = Modifier.testTag("row"),
                    leading = { Box {} },
                    trailing = { Text("↓") },
                    gestureEdges = HorizontalGestureEdges(leftPx = 60f, rightPx = 60f),
                )
            }
        }
    }

    @Test fun aTapStartingInTheLeftEdgeDoesNotClick() {
        show()
        compose.onNodeWithTag("row").performTouchInput { down(Offset(5f, centerY)); up() }
        compose.waitForIdle()
        assertEquals(0, clicks)
    }

    @Test fun aTapStartingInTheRightEdgeDoesNotClick() {
        show()
        compose.onNodeWithTag("row").performTouchInput { down(Offset(width - 5f, centerY)); up() }
        compose.waitForIdle()
        assertEquals(0, clicks)
    }

    @Test fun aTapInTheMiddleClicks() {
        show()
        compose.onNodeWithTag("row").performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test fun aSemanticsActivationAfterAnEdgeStartedTapStillClicks() {
        show()
        compose.onNodeWithTag("row").performTouchInput { down(Offset(5f, centerY)); up() }
        compose.waitForIdle()
        assertEquals(0, clicks)
        // TalkBack double-tap / keyboard Enter: no pointer down, must not be swallowed by the stale edge flag.
        compose.onNodeWithTag("row").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(1, clicks)
    }
}
