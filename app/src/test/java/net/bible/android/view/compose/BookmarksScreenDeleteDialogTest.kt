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
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.bookmark.BookmarksDialog
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 13, Step 1: the Bookmarks list's delete confirmation moved off the host
 * (`NavHostComposeActivity.confirmDeleteBookmarks`) into `BookmarksController.dialog` and is now
 * rendered by [BookmarksScreen] itself from a `BookmarksDialog` state -- this proves the screen
 * renders the right text for the count and answers the right callback for OK/Cancel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarksScreenDeleteDialogTest {
    @get:Rule val compose = createComposeRule()

    private var confirmCalls = 0
    private var dismissCalls = 0

    private fun show(dialog: BookmarksDialog) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                BookmarksScreen(
                    title = "Bookmarks",
                    rows = emptyList<BookmarkRow>(),
                    filterLabels = emptyList<BookmarkFilterLabel>(),
                    selectedFilterIndex = 0,
                    sortMode = BookmarkSortMode.BIBLE_ORDER,
                    searchText = "",
                    showNotes = false,
                    selection = emptySet(),
                    expandedIds = emptySet(),
                    loading = false,
                    onSelectFilter = {},
                    onCycleSort = {},
                    onSearch = {},
                    searchModeActive = false,
                    onOpenSearch = {},
                    onCloseSearch = {},
                    onToggleShowNotes = {},
                    onRowClick = { _, _ -> },
                    onRowLongClick = {},
                    onToggleSelected = {},
                    onToggleExpand = {},
                    onAssignSelected = {},
                    onDeleteSelected = {},
                    onClearSelection = {},
                    onManageLabels = {},
                    onExportCsv = {},
                    onImportCsv = {},
                    onUp = {},
                    dialog = dialog,
                    onConfirmDialog = { confirmCalls++ },
                    onDismissDialog = { dismissCalls++ },
                )
            }
        }
    }

    @Test fun noDialogWhenNone() {
        show(BookmarksDialog.None)
        compose.onNodeWithText("Do you want to remove", substring = true).assertDoesNotExist()
    }

    @Test fun confirmDeleteShowsTheFormattedCountAndAnswersConfirm() {
        show(BookmarksDialog.ConfirmDelete(3))
        compose.onNodeWithText("Do you want to remove 3 bookmarks and their notes?").assertExists()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, confirmCalls)
        assertEquals(0, dismissCalls)
    }

    @Test fun cancelAnswersDismiss() {
        show(BookmarksDialog.ConfirmDelete(1))
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmCalls)
        assertEquals(1, dismissCalls)
    }
}
