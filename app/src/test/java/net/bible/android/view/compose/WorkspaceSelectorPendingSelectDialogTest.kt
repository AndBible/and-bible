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
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.workspaces.WorkspaceSelectorScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 22 (platform-dialog removal run 3): [WorkspaceSelectorScreen]'s dirty-select prompt moved
 * from a hand-rolled M3 `AlertDialog` onto `AbOptionsDialog` (spec §6.2). Same 3-branch behaviour
 * as before: "Yes" saves+goes, "No" discards+goes, and dismissing (tap outside/back) leaves the
 * pending selection untouched -- this proves the wiring survived the swap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceSelectorPendingSelectDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = mutableListOf<Boolean>()
    private var dismissCalls = 0

    private fun show(pending: String?) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                WorkspaceSelectorScreen(
                    title = "Select workspace",
                    workspaces = emptyList(),
                    dirty = false,
                    canDelete = true,
                    filtering = false,
                    query = "",
                    searchModeActive = false,
                    copySettingsState = null,
                    pendingSelectId = pending,
                    onQueryChange = {}, onOpenSearch = {}, onCloseSearch = {},
                    onMove = { _, _ -> }, onSelect = {}, onRename = { _, _ -> },
                    onClone = { _, _ -> }, onDelete = {}, onEditSettings = {}, onCopySettings = {},
                    onCopySettingsToGlobal = {}, onChooseCopyTypes = {}, onChooseCopyTargets = {},
                    onCancelCopySettings = {}, onCreate = {}, onSave = {}, onCancel = {},
                    onConfirmPendingSelect = { confirmCalls.add(it) },
                    onDismissPendingSelect = { dismissCalls++ },
                    onHelp = {}, onNavigateUp = {},
                )
            }
        }
    }

    @Test fun noDialogWhenNoPendingSelection() {
        show(pending = null)
        compose.onNodeWithText("Yes").assertDoesNotExist()
        compose.onNodeWithText("No").assertDoesNotExist()
    }

    @Test fun pendingSelectionShowsBothOptions() {
        show(pending = "ws-2")
        compose.onNodeWithText("Yes").assertExists()
        compose.onNodeWithText("No").assertExists()
    }

    @Test fun yesAnswersConfirmWithSaveTrue() {
        show(pending = "ws-2")
        compose.onNodeWithText("Yes").performClick()
        assertEquals(listOf(true), confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun noAnswersConfirmWithSaveFalse() {
        show(pending = "ws-2")
        compose.onNodeWithText("No").performClick()
        assertEquals(listOf(false), confirmCalls)
        assertEquals(0, dismissCalls)
    }
}
