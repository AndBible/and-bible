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

import net.bible.sharedcore.ui.dialog.toAnswers
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbOptionsDialog
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Close-out Task 5 (AX5 walk on the iOS simulator): the "proceed to download documents?" dialog is an
 * [AbOptionsDialog]; at the largest accessibility text size its long message pushed the answers ("Yes",
 * "Don't ask again") out of the dialog, unreachable (the screenshot showed only "Cancel"). The message and
 * the answers must scroll together so every answer stays reachable at any font scale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbOptionsDialogLargeFontTest {
    // The real activity font scale 3 (as in dialog/AbDialogShellTest): a LocalDensity override does not reach the Dialog window.
    @get:Rule(order = 0) val fontScaleRule: org.junit.rules.TestRule = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                org.robolectric.RuntimeEnvironment.setFontScale(3f)
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    private val longMessage = List(12) { "If you live in a persecuted country you might need to be careful." }.joinToString(" ")

    @Test fun everyAnswerIsReachableAtTheLargestFontScale() {
        compose.setContent {
            AbTheme {
                AbOptionsDialog(
                    title = "Do you want to proceed to download documents?",
                    message = longMessage,
                    options = listOf(
                        SettingsItem.Choice(value = "yes", label = "Yes"),
                        SettingsItem.Choice(value = "dont_ask_again", label = "Do not ask again"),
                    ).toAnswers(),
                    onSelect = {},
                    onDismissRequest = {},
                    dismissText = "Cancel",
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("dialog-answer-dont_ask_again").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dialog-answer-yes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("dialog-dismiss").assertIsDisplayed()
    }
}
