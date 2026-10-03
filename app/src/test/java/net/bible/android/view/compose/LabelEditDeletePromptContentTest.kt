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
import net.bible.android.view.activity.nav.LabelEditDeletePromptContent
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.DeletePrompt
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 22 review, fix round 1, Important finding 2: [NavHostComposeActivity.LabelEditDeletePrompt]
 * (`app/src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt`) is a
 * `private` member composable that needs a live Activity instance to render (it calls its own
 * `getString`), so it had no behaviour test proving the `AbOptionsDialog` swap kept the same
 * callback wiring as the `ComposeAlertDialog` it replaced. The fix extracted the actual rendering
 * into the top-level, `internal` [LabelEditDeletePromptContent], which takes every string
 * pre-resolved and no longer touches Android resources or an Activity -- this test drives THAT
 * directly with plain string literals standing in for what `getString(R.string....)` would
 * otherwise have returned; [NavHostComposeActivity.LabelEditDeletePrompt] itself is now just a
 * one-call wrapper resolving those strings, unchanged in behaviour and still untested at the
 * member-composable level (same architectural gap the review found, now scoped to a thin,
 * low-risk wrapper instead of the actual rendering logic).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LabelEditDeletePromptContentTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = mutableListOf<Boolean>()
    private var dismissCalls = 0

    private fun show(prompt: DeletePrompt) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                LabelEditDeletePromptContent(
                    prompt = prompt,
                    title = "Remove bookmark label \"Study\"?",
                    orphanedMessage = if (prompt is DeletePrompt.Orphaned) "3 bookmarks would be orphaned" else null,
                    deleteBothLabel = "Delete label and bookmarks",
                    deleteOnlyLabel = "Delete label only",
                    cancelLabel = "Cancel",
                    confirmLabel = "Yes",
                    dismissLabel = "No",
                    onConfirm = { confirmCalls.add(it) },
                    onDismiss = { dismissCalls++ },
                )
            }
        }
    }

    // --- DeletePrompt.Orphaned: the AbOptionsDialog three-way ---------------------------------

    @Test fun orphaned_showsTitleMessageAndBothOptions() {
        show(DeletePrompt.Orphaned(3))
        compose.onNodeWithText("Remove bookmark label \"Study\"?").assertExists()
        compose.onNodeWithText("3 bookmarks would be orphaned").assertExists()
        compose.onNodeWithText("Delete label and bookmarks").assertExists()
        compose.onNodeWithText("Delete label only").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test fun orphaned_deleteBoth_answersConfirmTrue() {
        show(DeletePrompt.Orphaned(3))
        compose.onNodeWithText("Delete label and bookmarks").performClick()
        assertEquals(listOf(true), confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun orphaned_deleteLabelOnly_answersConfirmFalse() {
        show(DeletePrompt.Orphaned(3))
        compose.onNodeWithText("Delete label only").performClick()
        assertEquals(listOf(false), confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun orphaned_cancel_answersDismissOnly() {
        show(DeletePrompt.Orphaned(3))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<Boolean>(), confirmCalls)
        assertEquals(1, dismissCalls)
    }

    // --- DeletePrompt.Confirm: the plain 2-way, out of Task 22's scope but on the same seam ---

    @Test fun confirm_showsTitle() {
        show(DeletePrompt.Confirm)
        compose.onNodeWithText("Remove bookmark label \"Study\"?").assertExists()
    }

    @Test fun confirm_yes_answersConfirmFalse() {
        show(DeletePrompt.Confirm)
        compose.onNodeWithText("Yes").performClick()
        assertEquals(listOf(false), confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun confirm_no_answersDismissOnly() {
        show(DeletePrompt.Confirm)
        compose.onNodeWithText("No").performClick()
        assertEquals(emptyList<Boolean>(), confirmCalls)
        assertEquals(1, dismissCalls)
    }
}
