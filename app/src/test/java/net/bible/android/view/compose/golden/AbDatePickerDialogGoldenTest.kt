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

import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbDatePickerDialog
import net.bible.sharedui.components.ymdToUtcMidnightMillis
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        captureMatrix("AbDatePickerDialog", "startDate", heightDp = 620) {
            AbDatePickerDialog(
                initialUtcMillis = initialUtcMillis, maxUtcMillis = maxUtcMillis,
                confirmText = "OK", dismissText = "Cancel", onConfirm = { _, _, _ -> }, onDismiss = {},
            )
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun startDate_rtl() =
        captureRtl("AbDatePickerDialog", "startDate", heightDp = 620) {
            AbDatePickerDialog(
                initialUtcMillis = initialUtcMillis, maxUtcMillis = maxUtcMillis,
                confirmText = "OK", dismissText = "Cancel", onConfirm = { _, _, _ -> }, onDismiss = {},
            )
        }
}
