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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarksRowExpandTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val expandText: String get() = context.getString(R.string.bookmark_expand_row)

    // Long enough to overflow three lines of bodyMedium at the default Robolectric width.
    private val longText = List(12) {
        "In the beginning God created the heavens and the earth and the earth was without form."
    }.joinToString(" ")

    private fun row(id: String, content: String, notes: String? = null) = BookmarkRow(
        id = id,
        title = "Genesis 1:1",
        dateText = "Mon, 2026-07-13 09:15",
        content = StyledText.plain(content),
        notes = notes?.let { StyledText.plain(it) },
        labelColors = emptyList(),
        isSpeak = false,
    )

    /**
     * [showNotes] is a lambda, not a plain Boolean, so a test can flip it mid-composition: it is
     * read inside `setContent`, which makes the read a tracked snapshot read.
     */
    private fun setScreen(
        rows: List<BookmarkRow>,
        expandedIds: Set<String> = emptySet(),
        showNotes: () -> Boolean = { true },
        onToggleExpand: (String) -> Unit = {},
        onRowClick: (String, Int) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    BookmarksScreen(
                        title = "Bookmarks",
                        rows = rows,
                        filterLabels = listOf(BookmarkFilterLabel(0, "All")),
                        selectedFilterIndex = 0,
                        sortMode = BookmarkSortMode.BIBLE_ORDER,
                        searchText = "",
                        showNotes = showNotes(),
                        selection = emptySet(),
                        expandedIds = expandedIds,
                        loading = false,
                        onSelectFilter = {},
                        onCycleSort = {},
                        onSearch = {},
                        searchModeActive = false,
                        onOpenSearch = {},
                        onCloseSearch = {},
                        onToggleShowNotes = {},
                        onRowClick = onRowClick,
                        onRowLongClick = {},
                        onToggleSelected = {},
                        onToggleExpand = onToggleExpand,
                        onAssignSelected = {},
                        onDeleteSelected = {},
                        onClearSelection = {},
                        onManageLabels = {},
                        onExportCsv = {},
                        onImportCsv = {},
                        onUp = {},
                    )
                }
            }
        }
    }

    @Test fun a_short_row_gets_no_expand_chevron() {
        setScreen(listOf(row("b1", "For God so loved the world.")))
        compose.onNodeWithContentDescription(expandText).assertDoesNotExist()
    }

    @Test fun a_clipped_row_gets_an_expand_chevron() {
        setScreen(listOf(row("b1", longText)))
        compose.onNodeWithContentDescription(expandText).assertIsDisplayed()
    }

    @Test fun a_row_whose_only_long_field_is_the_note_gets_a_chevron_too() {
        setScreen(listOf(row("b1", "Short verse.", notes = longText)))
        compose.onNodeWithContentDescription(expandText).assertIsDisplayed()
    }

/**
     * The measured clipping flags self-correct while the measured `Text` stays in composition — but
     * the notes `Text` LEAVES composition when "Show notes" is switched off, so nothing could clear
     * `notesClipped` and the row kept a chevron that expanded nothing visible. Flipping the flag on
     * a live composition is the only way to see it: a screen composed with showNotes=false from the
     * start never sets the flag at all.
     */
    @Test fun hiding_the_notes_retires_the_chevron_that_only_a_long_note_earned() {
        val showNotes = mutableStateOf(true)
        setScreen(
            rows = listOf(row("b1", "Short verse.", notes = longText)),
            showNotes = { showNotes.value },
        )
        compose.onNodeWithContentDescription(expandText).assertIsDisplayed()

        showNotes.value = false
        compose.waitForIdle()

        compose.onNodeWithContentDescription(expandText).assertDoesNotExist()
    }

    @Test fun tapping_the_chevron_toggles_expansion_and_does_not_open_the_bookmark() {
        var expanded: String? = null
        var opened: String? = null
        setScreen(
            rows = listOf(row("b1", longText)),
            onToggleExpand = { expanded = it },
            onRowClick = { id, _ -> opened = id },
        )
        compose.onNodeWithContentDescription(expandText).performClick()
        assertEquals("b1", expanded)
        // The chevron's own clickable must consume the tap; if it bubbles to the row's
        // combinedClickable the user gets navigated away instead of a longer row.
        assertNull(opened)
    }

    @Test fun tapping_the_row_still_opens_the_bookmark() {
        var opened: String? = null
        setScreen(rows = listOf(row("b1", longText)), onRowClick = { id, _ -> opened = id })
        compose.onNodeWithText("Genesis 1:1").performClick()
        assertEquals("b1", opened)
    }

    @Test fun an_expanded_row_offers_the_collapse_affordance() {
        setScreen(rows = listOf(row("b1", longText)), expandedIds = setOf("b1"))
        // Fully expanded, longText's ~1100 characters push the chevron below Robolectric's tiny
        // default viewport (320x470px) -- scroll it into view first, same as a real long list.
        compose.onNodeWithContentDescription(context.getString(R.string.bookmark_collapse_row))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
