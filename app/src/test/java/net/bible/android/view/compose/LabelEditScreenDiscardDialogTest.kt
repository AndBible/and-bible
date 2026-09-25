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
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.LabelEditScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 13, Step 3: `NavHost.confirmDiscardLabelEdits` (classic `requestUp`'s discard-changes
 * `android.app.AlertDialog`) moved off the host into `LabelEditController.discardPrompt` and is
 * now rendered by [LabelEditScreen] itself -- unlike the delete prompt, this needed no new
 * `Strings.kt` entry (`discardChangesConfirmation` already existed), so the screen renders it
 * directly rather than through a host slot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelEditScreenDiscardDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0

    private fun state() = LabelEditState(
        labelId = "L1",
        name = "Grace",
        color = 0,
        customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT,
        wholeVerseStyle = null,
        favourite = false,
        isAssigning = false,
        thisBookmarkSelected = false,
        thisBookmarkPrimary = false,
        hasWorkspaceContext = false,
        autoAssign = false,
        autoAssignPrimary = false,
        overrideMode = OverrideMode.NONE,
        isSpecialLabel = false,
        isSpeakLabel = false,
    )

    private fun show(discardPrompt: Boolean) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                LabelEditScreen(
                    state = state(),
                    onName = {},
                    onColor = {},
                    onCustomIcon = {},
                    onSelectionStyle = {},
                    onWholeVerseStyle = {},
                    onToggleFavourite = {},
                    onToggleSelected = {},
                    onTogglePrimary = {},
                    onToggleAutoAssign = {},
                    onToggleAutoAssignPrimary = {},
                    onOverrideMode = {},
                    onUp = {},
                    iconKeys = emptyList(),
                    iconSlot = { _, _ -> },
                    actions = {},
                    discardPrompt = discardPrompt,
                    onConfirmDiscard = { confirmCalls++ },
                    onDismissDiscard = { dismissCalls++ },
                )
            }
        }
    }

    @Test fun noDialogWhenNotAsked() {
        show(discardPrompt = false)
        compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
    }

    @Test fun discardPromptShowsTheQuestionAndAnswersConfirm() {
        show(discardPrompt = true)
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun noAnswersDismiss() {
        show(discardPrompt = true)
        compose.onNodeWithText("No").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }
}
