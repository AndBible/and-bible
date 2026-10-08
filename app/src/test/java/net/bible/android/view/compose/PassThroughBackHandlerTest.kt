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

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.bible.android.TEST_SDK
import net.bible.sharedui.PassThroughBackHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Spec §3.1: a destination handler that can hand a press on to what is below it, synchronously. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class PassThroughBackHandlerTest {
    private fun activity() = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun aConsumedPressDoesNotReachTheCallbackBelow() {
        val a = activity()
        var below = 0
        a.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { below++ } })
        var mine = 0
        a.setContent { PassThroughBackHandler(enabled = true) { mine++ } }
        idle()
        a.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, mine)
        assertEquals(0, below)
    }

    @Test fun passThroughReachesTheCallbackBelowAndTheHandlerStaysEnabled() {
        val a = activity()
        var below = 0
        a.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { below++ } })
        var mine = 0
        a.setContent { PassThroughBackHandler(enabled = true) { passThrough -> mine++; passThrough() } }
        idle()
        a.onBackPressedDispatcher.onBackPressed()
        assertEquals("handled once, not re-entered", 1, mine)
        assertEquals(1, below)
        a.onBackPressedDispatcher.onBackPressed()
        assertEquals("still first in line after a pass-through", 2, mine)
    }

    @Test fun passThroughWithNothingBelowRunsTheActivityFallback() {
        val a = activity()
        a.setContent { PassThroughBackHandler(enabled = true) { passThrough -> passThrough() } }
        idle()
        a.onBackPressedDispatcher.onBackPressed()
        assertTrue("Activity.onBackPressed's default finishes a test activity", a.isFinishing)
    }

    @Test fun aDisabledHandlerIsSkipped() {
        val a = activity()
        var below = 0
        a.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { below++ } })
        var enabled by mutableStateOf(true)
        var mine = 0
        a.setContent { PassThroughBackHandler(enabled = enabled) { mine++ } }
        idle()
        enabled = false
        idle()
        a.onBackPressedDispatcher.onBackPressed()
        assertEquals(0, mine)
        assertEquals(1, below)
        assertFalse(a.isFinishing)
    }
}
