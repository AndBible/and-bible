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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * osTicket 3499: the "Disable animations" setting must also make Compose animations instant, through the window
 * recomposer every Android Compose root gets ([AppWindowRecomposer], installed in `BibleApplication`), not per
 * component.
 *
 * Deliberately no Compose test rule: the rule installs its own recomposer (and with it its own clock and motion scale)
 * through `WindowRecomposerPolicy.withFactory`, which would hide the production wiring. The activity runs on
 * Robolectric's paused main looper and [step] advances it frame by frame.
 *
 * Robolectric's frame times jump (a 2 s tween was 96 % done after three frames), so the timing probes use
 * [LONG_MILLIS] (60 s) specs: far longer than any such jump, yet ended on the next frame by a zero duration scale.
 * The library's own sheet animation cannot be lengthened, so the sheet is covered by the scale its window's effects
 * see and by the hide-then-dismiss flow, not by timing.
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

    private fun launch(content: @Composable () -> Unit): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).also { controllers += it }
        controller.create().start().resume().visible()
        controller.get().setContent { content() }
        step()
        return controller.get()
    }

    /**
     * Publishes state writes made outside composition and lets [millis] of frames run.
     *
     * Not `ShadowLooper.idleFor`: with an open `ModalBottomSheet` (a Popup) the paused looper is never idle, so
     * `idle()` spins forever (memory robolectric-open-sheet-idle-hang). Time advances one frame at a time instead,
     * and only the tasks due by then run, with a cap per frame.
     */
    private fun step(millis: Long = FEW_FRAMES) {
        Snapshot.sendApplyNotifications()
        val looper = shadowOf(Looper.getMainLooper())
        repeat((millis / FRAME_MILLIS).toInt().coerceAtLeast(1)) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(FRAME_MILLIS))
            var tasks = 0
            while (!looper.isIdle && tasks++ < MAX_TASKS_PER_FRAME) looper.runOneTask()
            Snapshot.sendApplyNotifications()
        }
    }

    // --- The duration scale the effects see ---------------------------------------------------------------------

    private class ScaleProbe {
        var scope: CoroutineScope? = null
        val scale: Float? get() = scope?.coroutineContext?.get(MotionDurationScale)?.scaleFactor
    }

    @Test fun rootEffectsSeeZeroScaleWhileTheSettingIsOnAndFollowItLive() {
        CommonUtils.settings.setBoolean("disable_animations", true)
        val probe = ScaleProbe()
        launch { probe.scope = rememberCoroutineScope() }
        assertEquals(0f, probe.scale)
        CommonUtils.settings.setBoolean("disable_animations", false)
        assertEquals("the same window follows the setting without a rebuild", 1f, probe.scale)
        CommonUtils.settings.setBoolean("disable_animations", true)
        assertEquals(0f, probe.scale)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun bottomSheetWindowInheritsTheZeroScale() {
        CommonUtils.settings.setBoolean("disable_animations", true)
        val probe = ScaleProbe()
        launch {
            ModalBottomSheet(onDismissRequest = {}) {
                probe.scope = rememberCoroutineScope()
                Text("sheet", Modifier.size(200.dp))
            }
        }
        assertEquals("the sheet composes in its own window, through the parent composition context", 0f, probe.scale)
    }

    // --- AnimatedVisibility (Transition-based) ---------------------------------------------------------------

    private fun animatedVisibilityExitFinished(animationsOff: Boolean): Boolean {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        var visible by mutableStateOf(true)
        var contentPresent = false
        launch {
            AnimatedVisibility(visible, exit = fadeOut(tween(LONG_MILLIS))) {
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

    // --- InfiniteTransition frozen at scale 0 must resume when animations come back --------------------------

    @Test fun infiniteTransitionStartedWhileDisabledResumesWhenAnimationsAreEnabledAgain() {
        CommonUtils.settings.setBoolean("disable_animations", true)
        var current = -1f
        launch {
            val value by rememberInfiniteTransition(label = "probe").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
                label = "probe",
            )
            current = value
        }
        step()
        val frozen = current
        step()
        assertEquals("frozen while disabled", frozen, current, 0f)
        CommonUtils.settings.setBoolean("disable_animations", false)
        step()
        val seen = mutableSetOf<Float>()
        repeat(6) { step(FRAME_MILLIS); seen += current }
        assertTrue("the transition must run again after re-enabling, saw $seen", seen.size > 1)
    }

    // --- animate*AsState (LaunchedEffect-launched Animatable) --------------------------------------------------

    private fun animateFloatValueAfterFewFrames(animationsOff: Boolean): Float {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        var target by mutableStateOf(0f)
        var current = -1f
        launch {
            val value by animateFloatAsState(target, tween(durationMillis = LONG_MILLIS), label = "probe")
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

    // --- ModalBottomSheet hide-then-dismiss still completes with a zero scale ----------------------------------

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun sheetHideThenDismissStillCompletesWhenAnimationsAreDisabled() {
        CommonUtils.settings.setBoolean("disable_animations", true)
        var state: SheetState? = null
        var hideRequested by mutableStateOf(false)
        var open by mutableStateOf(true)
        var dismissed = false
        launch {
            if (open) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                state = sheetState
                val scope = rememberCoroutineScope()
                LaunchedEffect(hideRequested) {
                    // The production hide-then-dismiss pattern (memory compose-modalbottomsheet-hide-before-dismiss).
                    if (hideRequested) scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            dismissed = true
                            open = false
                        }
                    }
                }
                ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
                    Text("sheet", Modifier.size(200.dp))
                }
            }
        }
        assertEquals("shown within a few frames", SheetValue.Expanded, state!!.currentValue)
        hideRequested = true
        step()
        assertTrue("hide finished and the dismiss callback ran", dismissed)
        assertEquals(SheetValue.Hidden, state!!.currentValue)
    }

    private companion object {
        const val FEW_FRAMES = 50L
        const val FRAME_MILLIS = 16L
        const val MAX_TASKS_PER_FRAME = 1_000
        const val LONG_MILLIS = 60_000
    }
}
