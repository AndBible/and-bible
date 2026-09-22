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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.sharedui.reading.rememberSplitIsHorizontal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F65. `NavHostComposeActivity` declares `orientation` in `configChanges`, so a rotation does not
 * recreate it and `SplitContent`'s composition survives. The split latch (held while the IME is up,
 * A/B F6-B1) was a plain `remember { }` and therefore carried the pre-rotation answer across the
 * rotation: the split stayed in the old orientation for as long as the keyboard stayed up.
 *
 * Drives [rememberSplitIsHorizontal] directly with explicit geometry, IME and reset-key inputs --
 * `SplitContent` passes the window's own orientation as the reset key -- so the test does not depend
 * on Robolectric delivering IME insets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SplitLatchResetOnRotationTest {
    @get:Rule val compose = createComposeRule()

    private data class Inputs(val w: Float, val h: Float, val ime: Boolean, val windowLandscape: Boolean)

    @Test
    fun theLatchHoldsWhileTypingButARotationDropsIt() {
        var inputs by mutableStateOf(Inputs(w = 400f, h = 800f, ime = false, windowLandscape = false))
        var answer: Boolean? = null
        compose.setContent {
            answer = rememberSplitIsHorizontal(
                widthPx = inputs.w, heightPx = inputs.h, reverseSplitMode = false,
                imeVisible = inputs.ime, resetKey = inputs.windowLandscape,
            )
        }
        compose.waitForIdle()
        assertEquals("portrait window, no keyboard: stacked", false, answer)

        // Keyboard up; the pane box shrinks to wider-than-tall. The latch must hold (F6-B1).
        inputs = Inputs(w = 400f, h = 300f, ime = true, windowLandscape = false)
        compose.waitForIdle()
        assertEquals("the keyboard's shrink must not flip the split", false, answer)

        // Rotate with the keyboard still up: the window becomes landscape and the box is 1200x370.
        inputs = Inputs(w = 1200f, h = 370f, ime = true, windowLandscape = true)
        compose.waitForIdle()
        assertEquals(
            "after a rotation the latch must be dropped and the new geometry decide -- a latch that " +
                "survives the rotation holds the split in the pre-rotation orientation (F65)",
            true, answer,
        )
    }
}
