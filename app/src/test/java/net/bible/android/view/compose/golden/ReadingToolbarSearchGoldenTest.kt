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

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.reading.ReadingToolbar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reading toolbar's F6 search mode: a non-null `searchBar` replaces the whole normal row.
 *
 * There is deliberately NO golden for `recentMenuOpen = true` — an expanded `DropdownMenu` hangs
 * Roborazzi. That is why the recent-terms menu state is state-in rather than remembered inside the
 * composable: `ReadingSearchBarStateTest` and the controller's tests cover what cannot be
 * photographed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingToolbarSearchGoldenTest {

    private fun bar(
        query: String = "light",
        recentTerms: List<String> = listOf("light", "water"),
        recentMenuOpen: Boolean = false,
    ) = ReadingSearchBarState(query, recentTerms, recentMenuOpen)

    private fun toolbar(
        searchBar: ReadingSearchBarState = bar(),
        state: ToolbarState = ToolbarState.EMPTY.copy(pageTitle = "Genesis 1", searchable = true),
    ): @Composable () -> Unit = {
        ReadingToolbar(
            state = state,
            icons = goldenToolbarIcons(),
            callbacks = goldenToolbarCallbacks(),
            searchBar = searchBar,
            searchBarCallbacks = ReadingSearchBarCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }

    @Test fun search() = captureMatrix("ReadingToolbar", "search", heightDp = 56, content = toolbar())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun search_rtl() = captureRtl("ReadingToolbar", "search", heightDp = 56, content = toolbar())

    // An empty query with no history: no clear button, and the leading search icon is inert (not an
    // IconButton) because there is nothing to drop down.
    @Test fun searchEmptyQuery() =
        captureGolden(
            "ReadingToolbar", "searchEmpty", EDGE_MODE, heightDp = 56,
            content = toolbar(searchBar = bar(query = "", recentTerms = emptyList())),
        )

    // The field, its icons and its placeholder must inherit the toolbar's derived content colour
    // rather than the scheme's — this is the case that proves it.
    @Test fun searchOnAWorkspaceColouredToolbar() =
        captureGolden(
            "ReadingToolbar", "searchWorkspaceColour", EDGE_MODE, heightDp = 56,
            content = toolbar(
                state = ToolbarState.EMPTY.copy(
                    pageTitle = "Genesis 1", searchable = true, workspaceColorArgb = 0xFF7B1FA2.toInt(),
                ),
            ),
        )
}
