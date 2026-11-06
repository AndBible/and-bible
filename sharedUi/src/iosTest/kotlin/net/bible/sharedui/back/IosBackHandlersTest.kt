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
package net.bible.sharedui.back

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEventInput
import net.bible.sharedui.PassThroughBackHandler
import net.bible.sharedui.PlatformBackHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #4003 on Kotlin/Native: the iOS actuals of the back handlers must register with the scene's navigation-event
 * dispatcher, which is what Compose's UIKit input drives on an unconsumed hardware Esc (and on the edge swipe). Before
 * the fix the iOS `PassThroughBackHandler` was a no-op, so the reading view's back chain (Find, drawer, search sheet)
 * never saw back. The JVM tests cannot show that: the desktop actual always worked.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class IosBackHandlersTest {
    /** Stands in for UIKitNavigationEventInput: completes back events and records the edge-swipe enablement. */
    private class SceneInput : NavigationEventInput() {
        var hasEnabled = false
            private set

        override fun onHasEnabledHandlersChanged(enabled: Boolean) {
            hasEnabled = enabled
        }

        fun back() = dispatchOnBackCompleted()
    }

    @Test
    fun escapeReachesTheReadingChainThroughThePassThroughHandler() = runComposeUiTest {
        val input = SceneInput()
        var sheetOpen by mutableStateOf(true)
        var findOpen by mutableStateOf(true)
        var passedOn = 0
        val log = mutableListOf<String>()
        setContent {
            val dispatcher = LocalCompatNavigationEventDispatcherOwner.current?.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher?.addInput(input)
                onDispose { dispatcher?.removeInput(input) }
            }
            // The reading destination's handler (enabled while its chain has a layer), then a sheet over it.
            PassThroughBackHandler(enabled = findOpen) { passThrough ->
                if (findOpen) { findOpen = false; log += "find" } else { passedOn++; passThrough() }
            }
            if (sheetOpen) PlatformBackHandler(enabled = true) { sheetOpen = false; log += "sheet" }
        }
        waitForIdle()
        assertTrue(input.hasEnabled, "an open sheet and Find must enable the scene's back input")
        runOnIdle { input.back() }
        waitForIdle()
        assertEquals(listOf("sheet"), log, "the first Esc closes only the topmost sheet")
        runOnIdle { input.back() }
        waitForIdle()
        assertEquals(listOf("sheet", "find"), log, "the second Esc closes Find")
        assertEquals(0, passedOn)
        assertTrue(!input.hasEnabled, "plain reading: nothing enabled, so the edge-swipe back stays off")
    }

    @Test
    fun aDeclinedPressPassesToTheHandlerBelow() = runComposeUiTest {
        val input = SceneInput()
        val log = mutableListOf<String>()
        setContent {
            val dispatcher = LocalCompatNavigationEventDispatcherOwner.current?.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher?.addInput(input)
                onDispose { dispatcher?.removeInput(input) }
            }
            // Below: the NavHost's pop; above: a History-first handler with no history left.
            PlatformBackHandler(enabled = true) { log += "pop" }
            PassThroughBackHandler(enabled = true) { passThrough -> log += "history"; passThrough() }
        }
        waitForIdle()
        runOnIdle { input.back() }
        waitForIdle()
        assertEquals(listOf("history", "pop"), log)
    }
}
