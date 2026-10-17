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

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SU-15d: the Bookmarks screen must not show the "no items" text while the first load is still
 * running (the controller starts with `loading = true` and empty rows).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarksFirstFrameTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val emptyText: String get() = context.getString(R.string.empty_list)

    private val row = BookmarkRow(
        id = "b1",
        title = "Genesis 1:1",
        dateText = "Mon, 2026-07-13 09:15",
        content = StyledText.plain("In the beginning"),
        notes = null,
        labelColors = emptyList(),
        isSpeak = false,
    )

    private fun setScreen(rows: () -> List<BookmarkRow>, loading: () -> Boolean) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    BookmarksScreen(
                        title = "Bookmarks", rows = rows(),
                        filterLabels = listOf(BookmarkFilterLabel(0, "All")), selectedFilterIndex = 0,
                        sortMode = BookmarkSortMode.BIBLE_ORDER, searchText = "", showNotes = true,
                        selection = emptySet(), expandedIds = emptySet(), loading = loading(),
                        onSelectFilter = {}, onCycleSort = {}, onSearch = {}, searchModeActive = false,
                        onOpenSearch = {}, onCloseSearch = {}, onToggleShowNotes = {}, onRowClick = { _, _ -> },
                        onRowLongClick = {}, onToggleSelected = {}, onToggleExpand = {}, onAssignSelected = {},
                        onDeleteSelected = {}, onClearSelection = {}, onManageLabels = {}, onExportCsv = {},
                        onImportCsv = {}, onUp = {},
                    )
                }
            }
        }
    }

    @Test fun empty_text_is_absent_while_loading_then_appears_when_loaded_empty() {
        val loading = mutableStateOf(true)
        setScreen({ emptyList() }, { loading.value })
        compose.onNodeWithText(emptyText).assertDoesNotExist()
        loading.value = false
        compose.waitForIdle()
        compose.onNodeWithText(emptyText).assertExists()
    }

    @Test fun rows_replace_the_loading_state_without_an_empty_text() {
        val loading = mutableStateOf(true)
        val rows = mutableStateOf(emptyList<BookmarkRow>())
        setScreen({ rows.value }, { loading.value })
        compose.onNodeWithText(emptyText).assertDoesNotExist()
        rows.value = listOf(row)
        loading.value = false
        compose.waitForIdle()
        compose.onNodeWithText("Genesis 1:1").assertExists()
        compose.onNodeWithText(emptyText).assertDoesNotExist()
    }
}
