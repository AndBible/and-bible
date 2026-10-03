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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.components.AbDatePickerDialog
import net.bible.sharedui.components.ymdToUtcMidnightMillis
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 30b: [AbDatePickerDialog] confirming without touching the picker must report the INITIAL
 * date unchanged (not "today", not an off-by-one from a UTC-instant conversion) -- the whole point
 * of feeding it [ymdToUtcMidnightMillis]-encoded millis rather than a real instant. The pure
 * conversion round trip itself is covered by `:sharedUi`'s `AbDatePickerDialogTest` (commonTest, so
 * it also runs on the iOS compile); this is the Compose-rendering half.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbDatePickerDialogInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirm_withoutChangingSelection_reportsTheInitialDateUnchanged() {
        var confirmed: Triple<Int, Int, Int>? = null
        val initial = ymdToUtcMidnightMillis(2026, 9, 25)
        val max = ymdToUtcMidnightMillis(2026, 9, 25)
        compose.setContent {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AbDatePickerDialog(
                    initialUtcMillis = initial,
                    maxUtcMillis = max,
                    confirmText = "OK",
                    dismissText = "Cancel",
                    onConfirm = { y, m, d -> confirmed = Triple(y, m, d) },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("OK").performClick()
        assertEquals(Triple(2026, 9, 25), confirmed)
    }

    @Test fun dismiss_reportsNoConfirmation() {
        var confirmed: Triple<Int, Int, Int>? = null
        var dismissed = false
        val initial = ymdToUtcMidnightMillis(2026, 9, 25)
        val max = ymdToUtcMidnightMillis(2026, 9, 25)
        compose.setContent {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AbDatePickerDialog(
                    initialUtcMillis = initial,
                    maxUtcMillis = max,
                    confirmText = "OK",
                    dismissText = "Cancel",
                    onConfirm = { y, m, d -> confirmed = Triple(y, m, d) },
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertNull(confirmed)
        assertEquals(true, dismissed)
    }
}
