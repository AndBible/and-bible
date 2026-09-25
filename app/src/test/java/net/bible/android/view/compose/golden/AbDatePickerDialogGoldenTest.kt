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

package net.bible.android.view.compose.golden

import android.os.Looper
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeCaptureOption
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbDatePickerDialog
import net.bible.sharedui.components.ymdToUtcMidnightMillis
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/**
 * Below 360dp, [AbDatePickerDialog] starts in `DisplayMode.Input` (I4), whose M3 `DateInput` text
 * field requests focus from its own `LaunchedEffect(Unit) { delay(MotionTokens.DurationMedium2);
 * focusRequester.requestFocus() }`. Roborazzi's `captureRoboImage` takes the screenshot right
 * after `setContent`, with no implicit settle, so that delayed effect lands before the shot on
 * some runs and not others -- the *same* composition caught at two different coroutine-clock
 * offsets, one with a thick focused text-field border and one with a thin unfocused one. That is
 * what made `AbDatePickerDialog_startDate_bw_compare.png` flip between repeat runs.
 *
 * A first attempt wrapped the content passed to `captureMatrix`/`captureRtl` in a composable that
 * grabbed `LocalFocusManager.current` and called `clearFocus(force = true)` after a delay. That
 * did NOT work: `AbDatePickerDialog` opens a `BasicAlertDialog`, which -- on Android -- mounts its
 * content in a genuinely separate `Dialog` window (`androidx.compose.ui.window.DialogWrapper`,
 * a `ComponentDialog`) with its OWN composition root and its OWN `LocalFocusManager`. The wrapper
 * sat OUTSIDE that dialog (in the caller's composition, above where `AbDatePickerDialog` opens the
 * dialog), so its `LocalFocusManager.current` resolved to the *parent* window's focus manager --
 * clearing focus there is a no-op on the actual focused text field inside the dialog's window, so
 * the capture just went back to racing M3's own `requestFocus()` effect as before.
 *
 * [SettleAsyncFocusBeforeCapture] instead operates a layer below Compose, on the real Android View
 * tree, where window boundaries don't matter: [RoborazziComposeCaptureOption.beforeCapture] runs
 * right before the screenshot (after `setContent`, per `RoborazziCompose.kt`'s
 * `onActivity { activity -> activity.setContent(...); ...; doBeforeCapture();
 * view.captureRoboImage() }`). There it idles the Robolectric shadow main looper long enough for
 * M3's delayed `requestFocus()` to land (so that one-shot `LaunchedEffect(Unit)` cannot fire again
 * afterwards), then reaches the actual dialog window Compose opened via Robolectric's
 * `ShadowDialog.getLatestDialog()` (every `Dialog.show()` -- and `DialogWrapper` is a genuine
 * `Dialog` subclass -- registers there) and clears focus via `Dialog.getCurrentFocus()`, a plain
 * `View` API that isn't scoped to any particular Compose composition. An unfocused field has no
 * blinking caret and no active animation, so once focus is cleared there is nothing left running --
 * any further idling is a no-op, unlike the focused state's own endless caret-blink loop (which is
 * itself an unbounded two-state race on top of the original focus race, so settling INTO focus
 * instead of out of it was rejected even before this scope bug was found).
 */
@OptIn(ExperimentalRoborazziApi::class)
private object SettleAsyncFocusBeforeCapture : RoborazziComposeCaptureOption {
    override fun beforeCapture() {
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        looper.idleFor(Duration.ofSeconds(1))
        looper.idle()
        ShadowDialog.getLatestDialog()?.currentFocus?.clearFocus()
        looper.idle()
        looper.idleFor(Duration.ofMillis(500))
        looper.idle()
    }

    override fun afterCapture() {}
}

/** Golden for [AbDatePickerDialog] (Task 30b), the reading-plan start-date picker's Compose
 *  replacement for classic's platform `android.app.DatePickerDialog`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbDatePickerDialogGoldenTest {
    private val initialUtcMillis = ymdToUtcMidnightMillis(2026, 9, 25)
    private val maxUtcMillis = ymdToUtcMidnightMillis(2026, 9, 30)

    // The full M3 DatePickerDialog (header + month grid + nav row + OK/Cancel) is taller than the
    // default viewport clips to — same reason AbColorPicker's presets page (also heightDp = 620)
    // needs an override.
    @Test fun startDate_matrix() =
        captureMatrix("AbDatePickerDialog", "startDate", heightDp = 620, captureOptions = listOf(SettleAsyncFocusBeforeCapture)) {
            AbDatePickerDialog(
                initialUtcMillis = initialUtcMillis, maxUtcMillis = maxUtcMillis,
                confirmText = "OK", dismissText = "Cancel", onConfirm = { _, _, _ -> }, onDismiss = {},
            )
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun startDate_rtl() =
        captureRtl("AbDatePickerDialog", "startDate", heightDp = 620, captureOptions = listOf(SettleAsyncFocusBeforeCapture)) {
            AbDatePickerDialog(
                initialUtcMillis = initialUtcMillis, maxUtcMillis = maxUtcMillis,
                confirmText = "OK", dismissText = "Cancel", onConfirm = { _, _, _ -> }, onDismiss = {},
            )
        }
}
