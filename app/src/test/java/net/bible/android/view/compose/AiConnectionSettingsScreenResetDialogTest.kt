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
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.ai.AiConnectionDialog
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiConnectionSettingsScreen
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.DialogButtonRoleKey
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 14, Step 1: the AI connection settings screen's "Reset usage data?" confirmation moved off
 * the host (`NavHostComposeActivity.showAiConnectionResetUsageConfirm`) into
 * `AiConnectionSettingsController.dialog` and is now rendered by [AiConnectionSettingsScreen] itself
 * from an `AiConnectionDialog` state -- this proves the screen renders the right title/message and
 * answers the right callback for OK/Cancel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiConnectionSettingsScreenResetDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0

    private fun show(dialog: AiConnectionDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AiConnectionSettingsScreen(
                    state = SettingsScreenState(title = "AI connection settings", items = emptyList()),
                    onUp = {},
                    onSwitch = { _, _ -> },
                    onListChoice = { _, _ -> },
                    onTextInputInt = { _, _ -> },
                    onCustomPromptSave = { _, _ -> },
                    customPromptTextFor = { "" },
                    languageChoices = emptyList(),
                    customLanguageValue = "\u0000custom",
                    onNavigate = {},
                    dialog = dialog,
                    onConfirmDialog = { confirmCalls++ },
                    onDismissDialog = { dismissCalls++ },
                )
            }
        }
    }

    @Test fun noDialogWhenNone() {
        show(AiConnectionDialog.None)
        compose.onNodeWithText("Reset usage data?").assertDoesNotExist()
    }

    @Test fun confirmResetUsage_showsLeadDetailAndDestructiveResetButton_andAnswersConfirm() {
        show(AiConnectionDialog.ConfirmResetUsage)
        compose.onNodeWithText("Reset usage data?").assertExists()
        compose.onNodeWithTag("dialog-detail").assertTextEquals("Token and cost totals go back to zero. This cannot be undone.")
        compose.onNodeWithTag("dialog-confirm").assertTextEquals("Reset").assert(
            SemanticsMatcher("role is Destructive") { it.config.getOrNull(DialogButtonRoleKey) == DialogButtonRole.Destructive },
        )
        compose.onNodeWithText("Reset").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun cancelAnswersDismiss() {
        show(AiConnectionDialog.ConfirmResetUsage)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }
}
