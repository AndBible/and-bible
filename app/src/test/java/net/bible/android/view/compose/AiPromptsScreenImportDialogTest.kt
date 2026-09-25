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
import net.bible.sharedcore.ai.AiPromptsDialog
import net.bible.sharedcore.ai.ImportMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiPromptsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 14, Step 2/3: the AI prompt manager's CSV import-mode choice (classic's
 * `.setItems(editable, add-on)` chooser) and post-import error summary moved off the host
 * (`NavHostComposeActivity.importPrompts`/`importCsvAsEditable`) into `AiPromptsController.dialog`
 * and are now rendered by [AiPromptsScreen] itself from an `AiPromptsDialog` state -- this proves
 * the screen renders the right options/text and answers the right callback.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AiPromptsScreenImportDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmedMode: ImportMode? = null
    private var dismissChoiceCalls = 0
    private var dismissDialogCalls = 0

    private fun show(dialog: AiPromptsDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                AiPromptsScreen(
                    configured = true,
                    groups = emptyList(),
                    showHidden = false,
                    hasHiddenPrompts = false,
                    onUp = {},
                    onOpenPrompt = {},
                    onNewPrompt = {},
                    onToggleFavorite = {},
                    onSetPromptHidden = { _, _ -> },
                    onSetCategoryHidden = { _, _ -> },
                    onDeletePrompt = {},
                    onDeleteCategory = { _, _ -> },
                    onMovePrompt = { _, _ -> },
                    onMoveCategory = { _, _ -> },
                    onCreateCategory = {},
                    onRenameCategory = { _, _ -> },
                    onSetShowHidden = {},
                    onOpenConnectionSettings = {},
                    onImportCsv = {},
                    onExportCsv = {},
                    onCopyPrompt = {},
                    onMovePromptToCategory = { _, _ -> },
                    categoriesProvider = { emptyList() },
                    helpBody = "",
                    helpReadMoreUrl = "",
                    dialog = dialog,
                    onConfirmImportMode = { confirmedMode = it },
                    onDismissImportModeChoice = { dismissChoiceCalls++ },
                    onDismissDialog = { dismissDialogCalls++ },
                )
            }
        }
    }

    @Test fun noDialogWhenNone() {
        show(AiPromptsDialog.None)
        compose.onNodeWithText("Import prompts from CSV").assertDoesNotExist()
    }

    @Test fun chooseImportMode_showsBothOptions() {
        show(AiPromptsDialog.ChooseImportMode)
        compose.onNodeWithText("Import prompts from CSV").assertExists()
        compose.onNodeWithText("Import as editable prompts").assertExists()
        compose.onNodeWithText("Install as add-on (read-only)").assertExists()
    }

    @Test fun chooseImportMode_pickingEditable_answersEditable() {
        show(AiPromptsDialog.ChooseImportMode)
        compose.onNodeWithText("Import as editable prompts").performClick()
        assertEquals(ImportMode.EDITABLE, confirmedMode)
    }

    @Test fun chooseImportMode_pickingAddon_answersAddon() {
        show(AiPromptsDialog.ChooseImportMode)
        compose.onNodeWithText("Install as add-on (read-only)").performClick()
        assertEquals(ImportMode.ADDON, confirmedMode)
    }

    @Test fun importErrors_showsTheHostFormattedText_andDismissAnswersDismissDialog() {
        show(AiPromptsDialog.ImportErrors("2 created, 1 error"))
        compose.onNodeWithText("2 created, 1 error").assertExists()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, dismissDialogCalls)
        assertEquals(0, dismissChoiceCalls)
    }

    // M1: classic's post-import summary had a title (the same "Import prompts from CSV" the choice
    // sheet uses) -- an AbErrorDialog has no title slot at all, which silently dropped it.
    @Test fun importErrors_showsTheTitleClassicHad() {
        show(AiPromptsDialog.ImportErrors("2 created, 1 error"))
        compose.onNodeWithText("Import prompts from CSV").assertExists()
    }
}
