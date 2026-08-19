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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.components.AbColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ManageLabelsGoldenTest {

    /** Stand-in for the host's real bar actions (which the host renders itself via
     *  painterResource'd Android drawables -- outside golden coverage). Proves the screen's
     *  actions slot renders at all, and -- paired with manageLabels_search below -- that Task 3's
     *  `actions = { if (!searchModeActive) actions() }` gate actually suppresses them in search
     *  mode. material-icons-extended is a real dependency of the :app TEST source set, so Material
     *  vectors are fine here even though production :app code can't use them. */
    private val standInActions: @Composable RowScope.() -> Unit = {
        Icon(Icons.Filled.Search, contentDescription = null)
        Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
        Icon(Icons.Filled.MoreVert, contentDescription = null)
    }

    private fun label(
        id: String,
        name: String,
        color: Int = AbColor.palette[0],
        favourite: Boolean = false,
        isUnlabeled: Boolean = false,
        hasOverride: Boolean = false,
    ) = LabelItem(
        id = id,
        name = name,
        color = color,
        favourite = favourite,
        isUnlabeled = isUnlabeled,
        isSpecial = false,
        customIcon = null,
        hasOverride = hasOverride,
    )

    /** A representative row list spanning ACTIVE/RECENT/OTHER, with a checked+primary row, a
     *  favourite, an override dot, an auto-assign row and a highlighted (StudyPad current) row.
     *  Headers are omitted for [ManageLabelsMode.STUDYPAD] (mode.hideCategories); the Unlabeled
     *  pseudo-label is appended only for modes that show it (mode.showUnassigned), exercising the
     *  Task-3 parity fix (plain icon, no auto-assign toggle). */
    private fun rows(mode: ManageLabelsMode): List<ManageLabelsRow> {
        val out = mutableListOf<ManageLabelsRow>()
        if (!mode.hideCategories) out += ManageLabelsRow.Header(LabelCategory.ACTIVE)
        out += ManageLabelsRow.Item(
            label = label("L1", "Study", favourite = true),
            checked = true, isAutoAssign = false, isPrimary = true, highlighted = false,
        )
        out += ManageLabelsRow.Item(
            label = label("L2", "Sermon notes", color = AbColor.palette[1], hasOverride = true),
            checked = false, isAutoAssign = true, isPrimary = false, highlighted = false,
        )
        if (!mode.hideCategories) out += ManageLabelsRow.Header(LabelCategory.RECENT)
        out += ManageLabelsRow.Item(
            label = label("L3", "Devotional", color = AbColor.palette[2]),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = true,
        )
        if (!mode.hideCategories) out += ManageLabelsRow.Header(LabelCategory.OTHER)
        out += ManageLabelsRow.Item(
            label = label("L4", "Prayer requests", color = AbColor.palette[3]),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        )
        if (mode.showUnassigned) {
            out += ManageLabelsRow.Item(
                label = label("unlabeled", "Unlabeled", isUnlabeled = true),
                checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
            )
        }
        return out
    }

    private fun screen(
        mode: ManageLabelsMode,
        searchMode: SearchMode = SearchMode.NAME_START,
        searchModeActive: Boolean = false,
        searchText: String = "",
        actions: @Composable RowScope.() -> Unit = {},
    ) = @androidx.compose.runtime.Composable {
        ManageLabelsScreen(
            title = "Manage labels",
            rows = rows(mode),
            mode = mode,
            searchText = searchText,
            searchMode = searchMode,
            onSearch = {},
            onSetSearchMode = {},
            onRowClick = {},
            onRowLongClick = {},
            onToggleChecked = {},
            onToggleFavourite = {},
            onSetPrimary = {},
            onToggleAutoAssign = {},
            onUp = {},
            iconSlot = { _, _ -> },
            actions = actions,
            searchModeActive = searchModeActive,
            onCloseSearch = {},
        )
    }

    /** A representative content-search-results list (SearchMode.CONTENT, StudyPad-only): 3 labels
     *  with match counts, each with a snippet whose [matchStart, matchEnd) span is highlighted --
     *  exercising [net.bible.sharedui.bookmark.searchResultSnippetStyledText]'s highlight-run split
     *  (a mid-snippet match, a match touching the start, and a multi-match count). */
    private fun searchResultRows(): List<ManageLabelsRow.SearchResult> = listOf(
        ManageLabelsRow.SearchResult(
            labelId = "L1",
            name = "Study",
            color = AbColor.palette[0],
            matchCount = 3,
            snippet = "In the beginning God created the heavens and the earth.",
            matchStart = 12,
            matchEnd = 15,
            firstMatchEntryId = "entry-1",
        ),
        ManageLabelsRow.SearchResult(
            labelId = "L2",
            name = "Sermon notes",
            color = AbColor.palette[1],
            matchCount = 1,
            snippet = "Grace and peace to you from God our Father.",
            matchStart = 0,
            matchEnd = 5,
            firstMatchEntryId = "entry-2",
        ),
        ManageLabelsRow.SearchResult(
            labelId = "L3",
            name = "Devotional",
            color = AbColor.palette[2],
            matchCount = 2,
            snippet = "Trust in the Lord with all your heart and lean not on your own understanding.",
            matchStart = 9,
            matchEnd = 13,
            firstMatchEntryId = "entry-3",
        ),
    )

    private fun contentSearchScreen() = @androidx.compose.runtime.Composable {
        ManageLabelsScreen(
            title = "Manage labels",
            rows = searchResultRows(),
            mode = ManageLabelsMode.STUDYPAD,
            searchText = "god",
            searchMode = SearchMode.CONTENT,
            onSearch = {},
            onSetSearchMode = {},
            onRowClick = {},
            onRowLongClick = {},
            onToggleChecked = {},
            onToggleFavourite = {},
            onSetPrimary = {},
            onToggleAutoAssign = {},
            onUp = {},
            iconSlot = { _, _ -> },
            actions = {},
            searchModeActive = false,
            onCloseSearch = {},
        )
    }

    // heightDp=800: up to 3 category headers + 5 item rows -- the default viewport clips the
    // tail of the list.
    @Test fun manageLabels_assign() =
        captureMatrix("ManageLabels", "assign", heightDp = 800, content = screen(ManageLabelsMode.ASSIGN))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun manageLabels_assign_rtl() =
        captureRtl("ManageLabels", "assign", heightDp = 800, content = screen(ManageLabelsMode.ASSIGN))

    /** WORKSPACE: auto-assign circle icons, favourite hearts, override dot, and the Unlabeled row
     *  (mode.showUnassigned) rendered with a plain (non-clickable) icon -- the Task-3 parity fix. */
    @Test fun manageLabels_workspace() =
        captureGolden("ManageLabels", "workspace", EDGE_MODE, heightDp = 800, content = screen(ManageLabelsMode.WORKSPACE))

    /** STUDYPAD: no category headers (mode.hideCategories), plain list -- no checkboxes, favourite,
     *  primary or auto-assign controls. */
    @Test fun manageLabels_studypad() =
        captureGolden("ManageLabels", "studypad", EDGE_MODE, heightDp = 800, content = screen(ManageLabelsMode.STUDYPAD))

    /** StudyPad content-search RESULTS state (SearchMode.CONTENT), search bar NOT active
     *  (searchModeActive = false, so per AbScaffold's contract the bar -- and the mode menu that
     *  only renders inside it -- are not drawn at all): just the SearchResult rows (colour dot,
     *  name, match-count text, and a highlighted snippet span) under the plain title bar --
     *  captured across all modes since highlight legibility is the point of this state. */
    @Test fun manageLabels_studypad_content() =
        captureMatrix("ManageLabels", "studypad_content", heightDp = 800, content = contentSearchScreen())

    /** Search mode: the bar becomes the search field, and the mode picker lives in its actions
     *  row. STUDYPAD so all three SearchModes are offered. The menu itself is NOT expanded — an
     *  expanded DropdownMenu hangs Roborazzi (see the two-popups finding). */
    @Test fun manageLabels_search() =
        captureGolden(
            "ManageLabels", "search", EDGE_MODE, heightDp = 700,
            content = screen(
                mode = ManageLabelsMode.STUDYPAD, searchMode = SearchMode.CONTENT, searchModeActive = true,
                searchText = "gen", actions = standInActions,
            ),
        )

    /** Normal (non-search) bar WITH host actions supplied: proves the screen renders the host's
     *  action slot at all. The host's real icons are Android drawables it resolves itself, so this
     *  stands in for them -- what is under test is the slot, not the glyphs. */
    @Test fun manageLabels_actions() =
        captureGolden(
            "ManageLabels", "actions", EDGE_MODE, heightDp = 700,
            content = screen(ManageLabelsMode.ASSIGN, actions = standInActions),
        )
}
