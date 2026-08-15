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

package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarksGoldenTest {

    /** A representative row list: a Bible-verse bookmark with a bold-highlighted content run and
     *  two label colours, a generic-title multi-label (3 colours) row, a speak row, and a row
     *  carrying notes. Mirrors classic `bookmark_list_item.xml`'s layout order (chips, speak icon,
     *  title/date, content, notes). */
    private fun rows(): List<BookmarkRow> = listOf(
        BookmarkRow(
            id = "b1",
            title = "Genesis 1:1",
            dateText = "Mon, 2026-07-13 09:15",
            content = StyledText(
                listOf(
                    StyledRun("In the beginning God created "),
                    StyledRun("the heavens and the earth", bold = true),
                    StyledRun("."),
                ),
            ),
            notes = null,
            labelColors = listOf(AbColor.palette[0], AbColor.palette[3]),
            isSpeak = false,
        ),
        BookmarkRow(
            id = "b2",
            title = "Sermon notes -- grace",
            dateText = "Tue, 2026-07-14 18:42",
            content = StyledText.plain("Grace and peace to you from God our Father and the Lord Jesus Christ."),
            notes = StyledText.plain("Follow up with the small group on this passage next week."),
            labelColors = listOf(AbColor.palette[1], AbColor.palette[2], AbColor.palette[4]),
            isSpeak = false,
        ),
        BookmarkRow(
            id = "b3",
            title = "Psalm 23:1",
            dateText = "Wed, 2026-07-15 07:03",
            content = StyledText(
                listOf(
                    StyledRun("The "),
                    StyledRun("Lord", bold = true),
                    StyledRun(" is my shepherd; I shall not want."),
                ),
            ),
            notes = null,
            labelColors = listOf(AbColor.palette[0]),
            isSpeak = true,
        ),
    )

    private fun filterLabels(): List<BookmarkFilterLabel> = listOf(
        BookmarkFilterLabel(0, "All"),
        BookmarkFilterLabel(1, "Unlabeled"),
        BookmarkFilterLabel(2, "Study"),
        BookmarkFilterLabel(3, "Sermon notes"),
    )

    private fun screen(
        rows: List<BookmarkRow> = rows(),
        showNotes: Boolean = true,
        selection: Set<String> = emptySet(),
        searchText: String = "",
        searchModeActive: Boolean = false,
    ) = @androidx.compose.runtime.Composable {
        BookmarksScreen(
            title = "Bookmarks",
            rows = rows,
            filterLabels = filterLabels(),
            selectedFilterIndex = 0,
            sortMode = BookmarkSortMode.BIBLE_ORDER,
            searchText = searchText,
            showNotes = showNotes,
            selection = selection,
            loading = false,
            onSelectFilter = {},
            onCycleSort = {},
            onSearch = {},
            searchModeActive = searchModeActive,
            onOpenSearch = {},
            onCloseSearch = {},
            onToggleShowNotes = {},
            onRowClick = { _, _ -> },
            onRowLongClick = {},
            onToggleSelected = {},
            onAssignSelected = {},
            onDeleteSelected = {},
            onClearSelection = {},
            onManageLabels = {},
            onExportCsv = {},
            onImportCsv = {},
            onUp = {},
        )
    }

    // heightDp=800: filter dropdown + search bar (showNotes=true) + 3 rows (one with notes) --
    // the default viewport clips the tail of the list.
    @Test fun bookmarks_primary() =
        captureMatrix("Bookmarks", "primary", heightDp = 800, content = screen())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun bookmarks_primary_rtl() =
        captureRtl("Bookmarks", "primary", heightDp = 800, content = screen())

    /** Selection active: contextual bar (assign-labels + delete) + checkboxes, two of three rows
     *  selected. */
    @Test fun bookmarks_selection() =
        captureGolden(
            "Bookmarks", "selection", EDGE_MODE, heightDp = 800,
            content = screen(selection = setOf("b1", "b3")),
        )

    // Search-mode top app bar, captured with an EMPTY query on purpose (not the usual non-empty
    // convention used elsewhere): this screen's search field carries its own placeholder text
    // ("Filter by notes", strings.bookmarksSearchNotesHint) which a text field only draws while its
    // value is empty, so an empty query is the one capture that actually shows that placeholder.
    @Test fun bookmarks_searchMode() =
        captureMatrix("Bookmarks", "searchMode", heightDp = 800, content = screen(searchModeActive = true))

    @Test fun bookmarks_empty() =
        captureGolden("Bookmarks", "empty", EDGE_MODE, heightDp = 800, content = screen(rows = emptyList()))
}
