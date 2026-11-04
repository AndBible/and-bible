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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
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
 * privately, with no coroutine-context hook; Material 3 1.4's MotionScheme is internal). The shared fallback is in
 * [AbModalBottomSheet] (every app sheet) and [AbNavHost], driven by `LocalDisableAnimations`, and this desktop test
 * sees exactly what iOS sees: a composition with no [androidx.compose.ui.MotionDurationScale].
 *
 * The clock does not auto-advance; [FEW_FRAMES] frames are far shorter than the library's sheet motion and the
 * navigation crossfade, so "settled within a few frames" proves the animation was skipped.
 *
 * Runs on the v2 [runComposeUiTest] (a queued `StandardTestDispatcher` for the composition and its effects). The
 * deprecated v1 one uses an unconfined dispatcher, and the JVM-wide `GlobalSnapshotManager` (started by the first
 * Compose scene in the JVM) applies global writes from the AWT thread: under v1 that thread resumed the sheet's
 * `snapshotFlow`s, so [InstantSheetMotion][net.bible.sharedui.components.AbModalBottomSheet] and Material's hide ran
 * on two threads at once and the scrim dismiss sometimes ended Expanded with no report (about 1 in 10 runs).
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

    /**
     * A takeover cancelled by another sheet-state user (a finger down, the library's own show) must not end the
     * takeover for good: the sheet's later animations are still instant.
     */
    @Test fun cancelledTakeoverDoesNotEndInstantMotion() {
        val probe = SheetProbe()
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    probe.state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    probe.scope = rememberCoroutineScope()
                    AbModalBottomSheet(onDismissRequest = { probe.dismissCalls++ }, sheetState = probe.state) {
                        Text("sheet", Modifier.size(200.dp))
                    }
                }
            }
            mainClock.advanceTimeBy(5_000)
            assertEquals(SheetValue.Expanded, probe.state.currentValue)
            // A slow hide starts; once the takeover is running it, another user cancels the takeover's hide.
            runOnIdle { probe.scope.launch { probe.state.hide() } }
            var frames = 0
            while (!probe.state.isAnimationRunning && frames++ < FEW_FRAMES) mainClock.advanceTimeByFrame()
            runOnIdle { probe.scope.launch { probe.state.expand() } }
            repeat(FEW_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(SheetValue.Expanded, probe.state.currentValue)
            assertFalse(probe.state.isAnimationRunning, "the interrupting expand must be instant too")
            // The later close is still instant.
            runOnIdle { probe.scope.launch { probe.state.hide() } }
            repeat(FEW_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(SheetValue.Hidden, probe.state.currentValue)
            assertFalse(probe.state.isAnimationRunning)
        }
    }

    /** The drag handle's dismiss action (screen readers) is instant and reports the dismiss exactly once. */
    @Test fun dragHandleDismissIsInstantAndOnceWhenAnimationsAreDisabled() {
        val probe = SheetProbe()
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AbTheme(darkTheme = false, disableAnimations = true) {
                    probe.state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    AbModalBottomSheet(
                        onDismissRequest = { probe.dismissCalls++ },
                        sheetState = probe.state,
                        dragHandleDescription = "Handle",
                    ) { Text("sheet", Modifier.size(200.dp)) }
                }
            }
            mainClock.advanceTimeBy(5_000)
            assertEquals(SheetValue.Expanded, probe.state.currentValue)
            onNodeWithContentDescription("Handle", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.Dismiss)
            repeat(FEW_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(SheetValue.Hidden, probe.state.currentValue)
            assertEquals(1, probe.dismissCalls)
            mainClock.advanceTimeBy(5_000)
            assertEquals(1, probe.dismissCalls, "still once after settling")
        }
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

    /** Toggling the setting at runtime must keep the nav graph (and the settings screen the user toggles it in). */
    @Test fun togglingTheSettingKeepsTheDestinationState() {
        var created = 0
        runComposeUiTest {
            var disable by mutableStateOf(false)
            setContent {
                AbTheme(darkTheme = false, disableAnimations = disable) {
                    val nav = rememberNavController()
                    AbNavHost(nav, startDestination = "a") {
                        composable("a") {
                            remember { created++ }
                            Text("Screen A")
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(1, created)
            runOnIdle { disable = true }
            waitForIdle()
            runOnIdle { disable = false }
            waitForIdle()
            assertEquals(1, created, "the destination was recreated")
        }
    }

    private companion object {
        const val FEW_FRAMES = 4
        /** Material's scrim description (`Strings.CloseSheet`, English on the JVM). */
        const val CLOSE_SHEET = "Close sheet"
    }
}
