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

package net.bible.sharedui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedui.components.AbModalBottomSheet
import net.bible.sharedui.components.AbNavHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * osTicket 3499 on iOS (and desktop): those hosts cannot put the app's zero motion-duration scale into the recomposer
 * context the way Android's `AppWindowRecomposer` does (CMP 1.11's `ComposeUIViewController` creates its scene
 * privately, with no coroutine-context hook). The shared fallback is in the theme and the navigation host, driven by
 * `disableAnimations`, and this desktop test sees exactly what iOS sees: a clock with no [androidx.compose.ui.MotionDurationScale].
 *
 * The clock does not auto-advance; [FEW_FRAMES] frames are far shorter than the library's sheet motion and the
 * navigation crossfade, so "settled within a few frames" proves the animation was skipped.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class DisableAnimationsSharedTest {

    private class SheetProbe {
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var dismissCalls = 0
    }

    /** The sheet's state read while it is still composed (disposal cancels a running animation). */
    private data class SheetSnapshot(val currentValue: SheetValue, val isAnimationRunning: Boolean, val dismissCalls: Int)

    private enum class Close { NONE, CALLER_HIDE, SCRIM }

    private fun sheetAfterFewFrames(disableAnimations: Boolean, close: Close): SheetSnapshot {
        val probe = SheetProbe()
        lateinit var snapshot: SheetSnapshot
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                    probe.state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    probe.scope = rememberCoroutineScope()
                    AbModalBottomSheet(onDismissRequest = { probe.dismissCalls++ }, sheetState = probe.state) {
                        Text("sheet", Modifier.size(200.dp))
                    }
                }
            }
            if (close != Close.NONE) {
                // Let the show finish first, whatever its speed.
                mainClock.advanceTimeBy(5_000)
                assertEquals(SheetValue.Expanded, probe.state.currentValue)
                when (close) {
                    Close.CALLER_HIDE -> runOnIdle { probe.scope.launch { probe.state.hide() } }
                    // The library's own dismiss path (scrim tap; back uses the same callback).
                    else -> onNodeWithContentDescription(CLOSE_SHEET, useUnmergedTree = true).performClick()
                }
            }
            repeat(FEW_FRAMES) { mainClock.advanceTimeByFrame() }
            snapshot = SheetSnapshot(probe.state.currentValue, probe.state.isAnimationRunning, probe.dismissCalls)
            if (close == Close.SCRIM) {
                // Settled either way: the dismiss is reported exactly once, never twice.
                mainClock.advanceTimeBy(5_000)
                assertEquals(1, probe.dismissCalls, "dismiss reports after settling")
            }
        }
        return snapshot
    }

    @Test fun sheetOpensInstantlyWhenAnimationsAreDisabled() {
        val state = sheetAfterFewFrames(disableAnimations = true, close = Close.NONE)
        assertEquals(SheetValue.Expanded, state.currentValue)
        assertFalse(state.isAnimationRunning)
    }

    @Test fun sheetStillSlidesInWhenAnimationsAreEnabled() {
        val state = sheetAfterFewFrames(disableAnimations = false, close = Close.NONE)
        assertTrue(state.isAnimationRunning, "the show animation must still be running, value ${state.currentValue}")
    }

    @Test fun sheetHidesInstantlyWhenAnimationsAreDisabled() {
        val state = sheetAfterFewFrames(disableAnimations = true, close = Close.CALLER_HIDE)
        assertEquals(SheetValue.Hidden, state.currentValue)
        assertFalse(state.isAnimationRunning)
    }

    @Test fun sheetStillSlidesOutWhenAnimationsAreEnabled() {
        val state = sheetAfterFewFrames(disableAnimations = false, close = Close.CALLER_HIDE)
        assertTrue(state.isAnimationRunning, "the hide animation must still be running, value ${state.currentValue}")
    }

    @Test fun scrimTapDismissesInstantlyAndOnceWhenAnimationsAreDisabled() {
        val state = sheetAfterFewFrames(disableAnimations = true, close = Close.SCRIM)
        assertEquals(SheetValue.Hidden, state.currentValue)
        assertEquals(1, state.dismissCalls)
    }

    @Test fun scrimTapStillSlidesOutWhenAnimationsAreEnabled() {
        val state = sheetAfterFewFrames(disableAnimations = false, close = Close.SCRIM)
        assertTrue(state.isAnimationRunning, "the dismiss animation must still be running, value ${state.currentValue}")
        assertEquals(0, state.dismissCalls)
    }

    // --- Navigation between screens ------------------------------------------------------------------------------

    private fun previousScreenGoneAfterFewFrames(disableAnimations: Boolean): Boolean {
        var aPresent = false
        var gone = false
        runComposeUiTest {
            lateinit var nav: NavHostController
            setContent {
                AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                    nav = rememberNavController()
                    AbNavHost(nav, startDestination = "a") {
                        composable("a") {
                            DisposableEffect(Unit) {
                                aPresent = true
                                onDispose { aPresent = false }
                            }
                            Text("Screen A")
                        }
                        composable("b") { Text("Screen B") }
                    }
                }
            }
            waitForIdle()
            assertTrue(aPresent)
            mainClock.autoAdvance = false
            runOnIdle { nav.navigate("b") }
            repeat(FEW_FRAMES) { mainClock.advanceTimeByFrame() }
            onNodeWithText("Screen B").assertExists()
            gone = !aPresent
        }
        return gone
    }

    @Test fun navigationIsInstantWhenAnimationsAreDisabled() {
        assertTrue(previousScreenGoneAfterFewFrames(disableAnimations = true), "the old screen must be gone")
    }

    @Test fun navigationStillCrossfadesWhenAnimationsAreEnabled() {
        assertFalse(previousScreenGoneAfterFewFrames(disableAnimations = false), "the old screen must still fade out")
    }

    private companion object {
        const val FEW_FRAMES = 4
        /** Material's scrim description (`Strings.CloseSheet`, English on the JVM). */
        const val CLOSE_SHEET = "Close sheet"
    }
}
