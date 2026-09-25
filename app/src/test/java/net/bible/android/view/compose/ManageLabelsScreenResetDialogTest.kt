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
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.ManageLabelsDialog
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsResetKind
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 13, Step 2: `resetManageLabels`'s `askConfirmation` (both the WORKSPACE and HIDELABELS
 * questions) moved off the host into `ManageLabelsController.dialog` and is now rendered by
 * [ManageLabelsScreen] itself from a `ManageLabelsDialog` state -- this proves the screen picks
 * the right message per `ManageLabelsResetKind` and answers the right callback for Yes/Cancel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ManageLabelsScreenResetDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0

    private fun show(dialog: ManageLabelsDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                ManageLabelsScreen(
                    title = "Manage labels",
                    rows = emptyList<ManageLabelsRow>(),
                    mode = ManageLabelsMode.WORKSPACE,
                    styleTagsVisible = false,
                    searchText = "",
                    searchMode = SearchMode.NAME_START,
                    onSearch = {},
                    onSetSearchMode = {},
                    filters = emptySet<LabelFilter>(),
                    onToggleFilter = {},
                    searchModeActive = false,
                    onCloseSearch = {},
                    onRowClick = {},
                    onRowLongClick = {},
                    onToggleChecked = {},
                    onToggleFavourite = {},
                    onSetPrimary = {},
                    onToggleAutoAssign = {},
                    onUp = {},
                    iconSlot = { _, _ -> },
                    actions = {},
                    searchActions = {},
                    dialog = dialog,
                    onConfirmDialog = { confirmCalls++ },
                    onDismissDialog = { dismissCalls++ },
                )
            }
        }
    }

    @Test fun noDialogWhenNone() {
        show(ManageLabelsDialog.None)
        compose.onNodeWithText("Do you want to", substring = true).assertDoesNotExist()
    }

    @Test fun workspaceKindShowsTheAutoAssignQuestion() {
        show(ManageLabelsDialog.ConfirmReset(ManageLabelsResetKind.WORKSPACE))
        compose.onNodeWithText("Do you want to remove all auto-assign labels from this workspace?").assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun hideLabelsKindShowsTheHideLabelsQuestion() {
        show(ManageLabelsDialog.ConfirmReset(ManageLabelsResetKind.HIDE_LABELS))
        // values-en/strings.xml overrides reset_hide_labels with different wording than the base
        // values/strings.xml -- Robolectric resolves the "en" locale, so this is the real string a
        // device shows, not the base resource's text (which the WORKSPACE case above happens to
        // share with its values-en, so that assertion didn't need this note).
        compose.onNodeWithText("Do you want to reset the hidden label settings?").assertExists()
    }

    @Test fun cancelAnswersDismiss() {
        show(ManageLabelsDialog.ConfirmReset(ManageLabelsResetKind.WORKSPACE))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }
}
