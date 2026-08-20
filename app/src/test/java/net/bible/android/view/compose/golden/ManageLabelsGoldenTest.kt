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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.bookmark.ManageLabelsScreen
import net.bible.sharedui.bookmark.ManageLabelsSearchModeMenuRows
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.strings.LocalStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Stand-in for the host's `ManageLabelIcon`. Paints with the caller-supplied [Color], because
 *  after round 10a Task 1 the tint is the SCREEN's decision (the host derived it before, which is
 *  why a grey glyph for every icon-less label survived the whole golden suite -- the slot was
 *  stubbed empty and the colour was never in an image). The real host resolves an Android drawable
 *  per key; the glyph here only has to be visible and tinted. */
val manageLabelIcon: @Composable (String?, Color) -> Unit = { _, tint ->
    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tint)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ManageLabelsGoldenTest {

    /** Stand-in for the host's real bar actions (which the host renders itself via
     *  painterResource'd Android drawables -- outside golden coverage). Paired with
     *  [standInSearchActions] below, this establishes three things, not two: manageLabels_actions
     *  shows the normal bar renders the host's `actions` slot at all; manageLabels_search shows that
     *  SAME normal-bar slot renders NONE of these icons while search is active -- `AbTopAppBar`'s own
     *  "search replaces the bar" contract (it discards `actions` whenever `search != null`), not
     *  something Task 3's screen-level gate adds on top; and manageLabels_search ALSO shows the
     *  search bar DOES render the host's separate `searchActions` slot ([standInSearchActions]) --
     *  the slot the real ⊕ reaches through post-I1, and the reason "no match -> create it with that
     *  name" is reachable again. material-icons-extended is a real dependency of the :app TEST
     *  source set, so Material vectors are fine here even though production :app code can't use
     *  them. */
    private val standInActions: @Composable RowScope.() -> Unit = {
        Icon(Icons.Filled.Search, contentDescription = null)
        Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
        Icon(Icons.Filled.MoreVert, contentDescription = null)
    }

    /** Stands in for the host's ⊕ in the SEARCH bar (I1). Deliberately a single distinct glyph, so
     *  the search golden shows at a glance which icons came from `searchActions` (present) and which
     *  from `actions` (absent -- the bar discards those under `search != null`). */
    private val standInSearchActions: @Composable RowScope.() -> Unit = {
        Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
    }

    private fun label(
        id: String,
        name: String,
        color: Int = AbColor.palette[0],
        favourite: Boolean = false,
        isUnlabeled: Boolean = false,
        hasOverride: Boolean = false,
        selectionStyle: BookmarkDisplayStyle = BookmarkDisplayStyle.HIGHLIGHT,
        wholeVerseStyle: BookmarkDisplayStyle? = null,
    ) = LabelItem(
        id = id,
        name = name,
        color = color,
        favourite = favourite,
        isUnlabeled = isUnlabeled,
        isSpecial = false,
        customIcon = null,
        selectionStyle = selectionStyle,
        wholeVerseStyle = wholeVerseStyle,
        hasOverride = hasOverride,
    )

    /** One row per style, plus a row whose whole-verse axis really differs (so the second tag
     *  appears) and one that inherits (so it does not). This is the image that shows whether the
     *  list answers "what does this label look like" at all. */
    private fun styleRows(): List<ManageLabelsRow> = listOf(
        ManageLabelsRow.Item(
            label = label("S1", "Study", selectionStyle = BookmarkDisplayStyle.HIGHLIGHT),
            checked = true, isAutoAssign = true, isPrimary = true, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label(
                "S2", "Sermon notes", color = AbColor.palette[1],
                selectionStyle = BookmarkDisplayStyle.UNDERLINE,
                wholeVerseStyle = BookmarkDisplayStyle.HIGHLIGHT,
            ),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label("S3", "Prayer requests", color = AbColor.palette[2], selectionStyle = BookmarkDisplayStyle.MARKER),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label("S4", "Old notes", color = AbColor.palette[3], selectionStyle = BookmarkDisplayStyle.HIDDEN),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
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
        searchActions: @Composable RowScope.() -> Unit = {},
        compact: Boolean = false,
        rows: List<ManageLabelsRow>? = null,
    ) = @androidx.compose.runtime.Composable {
        ManageLabelsScreen(
            title = "Manage labels",
            // rows(mode) below: explicit `this.` is load-bearing -- the `rows` PARAMETER above
            // shadows the `rows(mode)` MEMBER FUNCTION by simple name inside this scope.
            rows = rows ?: this.rows(mode),
            mode = mode,
            compact = compact,
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
            iconSlot = manageLabelIcon,
            actions = actions,
            searchActions = searchActions,
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
            compact = false,
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
            iconSlot = manageLabelIcon,
            actions = {},
            searchActions = {},
            // Production can never have a non-empty query with the bar closed: nothing but the
            // bar's onQueryChange writes searchText, and closeSearch clears it. searchModeActive =
            // true depicts the state production actually reaches, and incidentally gives the search
            // bar its dark / BW / COLOR_EINK coverage via captureMatrix below (previously only in
            // light, since manageLabels_search below is a single captureGolden call).
            searchModeActive = true,
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

    /** Every style on one page, two-line (default) mode: the tag column is the thing under test.
     *  Row S2's whole-verse axis differs so its second tag appears; the other three inherit, so
     *  they show one tag each. */
    @Test fun manageLabels_styles() =
        captureMatrix("ManageLabels", "styles", heightDp = 500, content = screen(ManageLabelsMode.ASSIGN, rows = styleRows()))

    /** The same rows, compact: one line, selection tag only, ~48dp per row. Read against
     *  ManageLabels_styles_light to see what the ⋮ toggle actually buys. */
    @Test fun manageLabels_styles_compact() =
        captureGolden(
            "ManageLabels", "styles_compact", EDGE_MODE, heightDp = 500,
            content = screen(ManageLabelsMode.ASSIGN, rows = styleRows(), compact = true),
        )

    /** WORKSPACE compact: the four trailing controls (checkbox, ⚡, heart, primary) at their
     *  tightest, against the longest name in the fixture. This is the layout-overflow image. */
    @Test fun manageLabels_workspace_compact() =
        captureGolden(
            "ManageLabels", "workspace_compact", EDGE_MODE, heightDp = 800,
            content = screen(ManageLabelsMode.WORKSPACE, compact = true),
        )

    /** WORKSPACE: auto-assign circle icons, favourite hearts, override dot, and the Unlabeled row
     *  (mode.showUnassigned) rendered with a plain (non-clickable) icon -- the Task-3 parity fix. */
    @Test fun manageLabels_workspace() =
        captureGolden("ManageLabels", "workspace", EDGE_MODE, heightDp = 800, content = screen(ManageLabelsMode.WORKSPACE))

    /** STUDYPAD: no category headers (mode.hideCategories), plain list -- no checkboxes, favourite,
     *  primary or auto-assign controls. */
    @Test fun manageLabels_studypad() =
        captureGolden("ManageLabels", "studypad", EDGE_MODE, heightDp = 800, content = screen(ManageLabelsMode.STUDYPAD))

    /** StudyPad content-search RESULTS state (SearchMode.CONTENT), search bar ACTIVE
     *  (searchModeActive = true -- the only state production can actually reach, see
     *  contentSearchScreen's comment): the SearchResult rows (colour dot, name, match-count text,
     *  and a highlighted snippet span) under the search bar -- captured across all modes since
     *  highlight legibility is the point of this state. */
    @Test fun manageLabels_studypad_content() =
        captureMatrix("ManageLabels", "studypad_content", heightDp = 800, content = contentSearchScreen())

    /** Search mode: the bar becomes the search field, the host's ⊕ ([standInSearchActions]) sits in
     *  the search bar's own action slot, and the mode picker follows it. STUDYPAD so all three
     *  SearchModes are offered. The menu itself is NOT expanded — an expanded DropdownMenu hangs
     *  Roborazzi (see the two-popups finding).
     *
     *  This is the other half of the standInActions/standInSearchActions pair (see that KDoc above),
     *  and now proves BOTH halves of the I1 fix in one image: `actions = standInActions` is passed
     *  to show those three normal-bar icons are NOT drawn while search is active (`AbTopAppBar`'s
     *  "search replaces the bar" contract, not something this screen adds); `searchActions =
     *  standInSearchActions` is passed to show the search bar DOES render a distinct host icon of
     *  its own. The bar should read [back arrow | "gen" | ⊕ | mode icon | ✕] -- exactly what
     *  production now renders, and exactly the reachable "no match -> create it with that name" path
     *  I1 restored. */
    @Test fun manageLabels_search() =
        captureGolden(
            "ManageLabels", "search", EDGE_MODE, heightDp = 700,
            content = screen(
                mode = ManageLabelsMode.STUDYPAD, searchMode = SearchMode.CONTENT, searchModeActive = true,
                searchText = "gen", actions = standInActions, searchActions = standInSearchActions,
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

    /**
     * The mode menu's ITEM SET, golden directly rather than via the real popup: an expanded
     * `DropdownMenu` hangs Roborazzi (see the two-popups finding), so this follows
     * `WorkspaceSelectorGoldenTest.workspaceRowMenu_root`'s pattern of rendering
     * [net.bible.sharedui.bookmark.ManageLabelsSearchModeMenuRows] inside a plain `Column` instead
     * of opening the real `DropdownMenu`. This is the coverage that would have caught the labels
     * reading "Ab*" / "*ab*" instead of "Name (from start)" / "Name (contains)" (I2) -- a wording
     * bug the earlier text-button widget never exposed and no prior golden rendered.
     */
    @Composable
    private fun modeMenuRows(mode: ManageLabelsMode, searchMode: SearchMode = SearchMode.NAME_START) = Column {
        ManageLabelsSearchModeMenuRows(
            mode = mode,
            searchMode = searchMode,
            onSetSearchMode = {},
            strings = LocalStrings.current,
        )
    }

    /** STUDYPAD: three rows (name-start, name-contains, content), the content row checked. */
    @Test fun manageLabelsSearchModeMenu_studypad() {
        captureGolden("ManageLabelsSearchModeMenu", "studypad", EDGE_MODE, heightDp = 400) {
            modeMenuRows(ManageLabelsMode.STUDYPAD, searchMode = SearchMode.CONTENT)
        }
    }

    /** A non-StudyPad mode: only the two name-match rows -- no content option, since only
     *  StudyPads have searchable content. */
    @Test fun manageLabelsSearchModeMenu_assign() {
        captureGolden("ManageLabelsSearchModeMenu", "assign", EDGE_MODE, heightDp = 400) {
            modeMenuRows(ManageLabelsMode.ASSIGN)
        }
    }
}
