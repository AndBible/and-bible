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
package net.bible.sharedui.desktop.reading

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.PassThroughBackHandler
import net.bible.sharedui.PlatformBackHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopBackDispatcherTest {
    @Test
    fun escClosesTopmostFirst() = runComposeUiTest {
        val log = mutableListOf<String>()
        var sheetOpen by mutableStateOf(true)
        setContent {
            PlatformBackHandler(enabled = true) { log += "screen" }
            if (sheetOpen) PlatformBackHandler(enabled = true) { log += "sheet"; sheetOpen = false }
        }
        waitForIdle()
        assertTrue(DesktopBackDispatcher.back())
        waitForIdle()
        assertEquals(listOf("sheet"), log)
        assertTrue(DesktopBackDispatcher.back())
        assertEquals(listOf("sheet", "screen"), log)
    }

    @Test
    fun disabledHandlerIsNotConsumed() = runComposeUiTest {
        var hits = 0
        setContent { PlatformBackHandler(enabled = false) { hits++ } }
        waitForIdle()
        assertFalse(DesktopBackDispatcher.back())
        assertEquals(0, hits)
    }

    @Test
    fun passThroughForwardsWhenUnhandled() = runComposeUiTest {
        val log = mutableListOf<String>()
        setContent {
            PlatformBackHandler(enabled = true) { log += "below" }
            PassThroughBackHandler(enabled = true) { passThrough -> log += "top"; passThrough() }
        }
        waitForIdle()
        assertTrue(DesktopBackDispatcher.back())
        assertEquals(listOf("top", "below"), log)
        // The pass-through handler is enabled again afterwards.
        assertTrue(DesktopBackDispatcher.back())
        assertEquals(listOf("top", "below", "top", "below"), log)
    }

    @Test
    fun passThroughWithNothingBelowReportsUnhandled() = runComposeUiTest {
        var hits = 0
        setContent { PassThroughBackHandler(enabled = true) { passThrough -> hits++; passThrough() } }
        waitForIdle()
        assertFalse(DesktopBackDispatcher.back())
        assertEquals(1, hits)
        assertFalse(DesktopBackDispatcher.back())
        assertEquals(2, hits)
    }

    @Test
    fun mouseBackButtonNavigatesBack() = runComposeUiTest {
        var hits = 0
        setContent { PlatformBackHandler(enabled = true) { hits++ } }
        waitForIdle()
        assertEquals(DesktopBackDispatcher.Navigation.Back, DesktopBackDispatcher.navigationForDomMouseButton(3))
        assertEquals(DesktopBackDispatcher.Navigation.Forward, DesktopBackDispatcher.navigationForDomMouseButton(4))
        assertEquals(DesktopBackDispatcher.Navigation.Back, DesktopBackDispatcher.navigationForAwtMouseButton(4))
        assertEquals(DesktopBackDispatcher.Navigation.Forward, DesktopBackDispatcher.navigationForAwtMouseButton(5))
        assertNull(DesktopBackDispatcher.navigationForDomMouseButton(0))
        assertNull(DesktopBackDispatcher.navigationForAwtMouseButton(1))
        assertTrue(DesktopBackDispatcher.perform(DesktopBackDispatcher.navigationForDomMouseButton(3)!!))
        assertEquals(1, hits)
        // Forward has no history on desktop: consumed, nothing dispatched.
        assertTrue(DesktopBackDispatcher.perform(DesktopBackDispatcher.Navigation.Forward))
        assertEquals(1, hits)
    }

    @Test
    fun rtlSwapsBackForwardKeys() {
        val b = DesktopBackDispatcher.Navigation.Back
        val f = DesktopBackDispatcher.Navigation.Forward
        assertEquals(b, DesktopBackDispatcher.navigationForKey(Key.DirectionLeft, alt = true, isRtl = false))
        assertEquals(f, DesktopBackDispatcher.navigationForKey(Key.DirectionRight, alt = true, isRtl = false))
        assertEquals(f, DesktopBackDispatcher.navigationForKey(Key.DirectionLeft, alt = true, isRtl = true))
        assertEquals(b, DesktopBackDispatcher.navigationForKey(Key.DirectionRight, alt = true, isRtl = true))
        assertNull(DesktopBackDispatcher.navigationForKey(Key.DirectionLeft, alt = false, isRtl = false))
        assertNull(DesktopBackDispatcher.navigationForKey(Key.Escape, alt = false, isRtl = false))
        assertEquals(b, DesktopBackDispatcher.navigationForKey(Key.Escape, alt = false, isRtl = false, fromPane = true))
        assertEquals(b, DesktopBackDispatcher.navigationForDomKey("ArrowLeft", alt = true, isRtl = false))
        assertEquals(b, DesktopBackDispatcher.navigationForDomKey("ArrowRight", alt = true, isRtl = true))
        assertEquals(b, DesktopBackDispatcher.navigationForDomKey("Escape", alt = false, isRtl = true))
        assertNull(DesktopBackDispatcher.navigationForDomKey("ArrowLeft", alt = false, isRtl = false))
    }

    @Test
    fun layoutDirectionIsLtrWithoutKoin() {
        assertFalse(DesktopLayoutDirection.isRtl())
        assertTrue(DesktopLayoutDirection.isRtl("ar"))
        assertFalse(DesktopLayoutDirection.isRtl("en"))
    }
}
