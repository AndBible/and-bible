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
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/**
 * osTicket 3499: the "Disable animations" setting must also make Compose animations instant, through the window
 * recomposer every Android Compose root gets (installed in `BibleApplication`), not per component.
 *
 * Deliberately no Compose test rule: the rule installs its own recomposer (and with it its own clock and motion scale)
 * through `WindowRecomposerPolicy.withFactory`, which would hide the production wiring. The activity runs on
 * Robolectric's paused main looper, so [step] advances real (shadow) time frame by frame, like a device would.
 *
 * Each animation lasts 1-2 s; [FEW_FRAMES] (about 50 ms) is far too short for it to finish unless the duration scale
 * is zero, and far too long for a zero-scale animation not to have finished (it ends on the next frame).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DisableAnimationsMotionScaleTest {
    private val controllers = mutableListOf<ActivityController<ComponentActivity>>()

    @After fun tearDown() {
        CommonUtils.settings.setBoolean("disable_animations", false)
        controllers.forEach { it.pause().stop().destroy() }
    }

    private fun launch(content: @androidx.compose.runtime.Composable () -> Unit): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).also { controllers += it }
        controller.create().start().resume().visible()
        controller.get().setContent { content() }
        step()
        return controller.get()
    }

    /** Publishes state writes made outside composition and lets [millis] of frames run. */
    private fun step(millis: Long = FEW_FRAMES) {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    // --- AnimatedVisibility (Transition-based) ---------------------------------------------------------------

    private fun animatedVisibilityExitFinished(animationsOff: Boolean): Boolean {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        var visible by mutableStateOf(true)
        var contentPresent = false
        launch {
            AnimatedVisibility(visible) {
                DisposableEffect(Unit) {
                    contentPresent = true
                    onDispose { contentPresent = false }
                }
                Box(Modifier.size(40.dp))
            }
        }
        assertTrue("content is shown before the exit", contentPresent)
        visible = false
        step()
        return !contentPresent
    }

    @Test fun animatedVisibilityExitIsInstantWhenAnimationsAreDisabled() {
        assertTrue("the exit transition must end within a few frames", animatedVisibilityExitFinished(animationsOff = true))
    }

    @Test fun animatedVisibilityExitStillAnimatesWhenAnimationsAreEnabled() {
        assertFalse("the exit transition must still be running", animatedVisibilityExitFinished(animationsOff = false))
    }

    // --- animate*AsState (LaunchedEffect-launched Animatable) --------------------------------------------------

    private fun animateFloatValueAfterFewFrames(animationsOff: Boolean): Float {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        var target by mutableStateOf(0f)
        var current = -1f
        launch {
            val value by animateFloatAsState(target, tween(durationMillis = 2_000), label = "probe")
            current = value
        }
        target = 1f
        step()
        return current
    }

    @Test fun animateAsStateJumpsToTargetWhenAnimationsAreDisabled() {
        assertEquals(1f, animateFloatValueAfterFewFrames(animationsOff = true), 0f)
    }

    @Test fun animateAsStateStillAnimatesWhenAnimationsAreEnabled() {
        val value = animateFloatValueAfterFewFrames(animationsOff = false)
        assertTrue("expected an intermediate value, was $value", value < 1f)
    }

    // --- ModalBottomSheet show/hide (SheetState animations from rememberCoroutineScope, in the sheet's own window) --

    @OptIn(ExperimentalMaterial3Api::class)
    private class SheetProbe {
        var state: SheetState? = null
        var showRequested by mutableStateOf(false)
        var hideRequested by mutableStateOf(false)
        var dismissed = false
        var open by mutableStateOf(true)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun sheetProbe(animationsOff: Boolean): SheetProbe {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        val probe = SheetProbe()
        launch {
            if (probe.open) {
                val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                probe.state = state
                val scope = rememberCoroutineScope()
                LaunchedEffect(probe.hideRequested) {
                    // The production hide-then-dismiss pattern (memory compose-modalbottomsheet-hide-before-dismiss).
                    if (probe.hideRequested) scope.launch { state.hide() }.invokeOnCompletion {
                        if (!state.isVisible) {
                            probe.dismissed = true
                            probe.open = false
                        }
                    }
                }
                ModalBottomSheet(onDismissRequest = { probe.open = false }, sheetState = state) {
                    Text("sheet", Modifier.size(200.dp))
                }
            }
        }
        return probe
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun sheetShowsAndHidesInstantlyWhenAnimationsAreDisabled() {
        val probe = sheetProbe(animationsOff = true)
        assertEquals("shown within a few frames", SheetValue.Expanded, probe.state!!.currentValue)
        probe.hideRequested = true
        step()
        assertTrue("hide finished and the dismiss callback ran", probe.dismissed)
        assertEquals(SheetValue.Hidden, probe.state!!.currentValue)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun sheetStillAnimatesWhenAnimationsAreEnabled() {
        val probe = sheetProbe(animationsOff = false)
        assertEquals("still sliding in after a few frames", SheetValue.Hidden, probe.state!!.currentValue)
        step(5_000)
        assertEquals(SheetValue.Expanded, probe.state!!.currentValue)
        probe.hideRequested = true
        step()
        assertFalse("still sliding out after a few frames", probe.dismissed)
        step(5_000)
        assertTrue("dismissed once the slide-out ends", probe.dismissed)
    }

    @Test fun diagnostic() {
        var scale: Float? = -2f
        launch { scale = rememberCoroutineScope().coroutineContext[androidx.compose.ui.MotionDurationScale]?.scaleFactor }
        println("DIAG scale=$scale settings=" + android.provider.Settings.Global.getFloat(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, -1f) + " va=" + android.animation.ValueAnimator.getDurationScale())
    }

    private companion object {
        const val FEW_FRAMES = 50L
    }
}
