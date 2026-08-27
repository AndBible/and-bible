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
        overrideStyle: BookmarkDisplayStyle? = null,
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
        overrideStyle = overrideStyle,
    )

    /** One row per style, plus a row whose whole-verse axis really differs (so the second tag
     *  appears) and one that inherits (so it does not). S2's whole-verse axis is deliberately
     *  `MARKER`, not another plain-text style: that is the two-tags-where-the-second-carries-a-
     *  glyph case, the exact width pressure two earlier fix rounds were about, and before this it
     *  was never actually captured (no row anywhere in this fixture had `MARKER` on the whole-verse
     *  axis). This is the image that shows whether the list answers "what does this label look
     *  like" at all. */
    private fun styleRows(): List<ManageLabelsRow> = listOf(
        ManageLabelsRow.Item(
            label = label("S1", "Study", selectionStyle = BookmarkDisplayStyle.HIGHLIGHT),
            checked = true, isAutoAssign = true, isPrimary = true, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label(
                "S2", "Sermon notes", color = AbColor.palette[1],
                selectionStyle = BookmarkDisplayStyle.UNDERLINE,
                wholeVerseStyle = BookmarkDisplayStyle.MARKER,
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
        ManageLabelsRow.Item(
            label = label(
                "S5", "Overridden", color = AbColor.palette[4],
                selectionStyle = BookmarkDisplayStyle.HIGHLIGHT,
                wholeVerseStyle = BookmarkDisplayStyle.UNDERLINE,
                overrideStyle = BookmarkDisplayStyle.MARKER,
            ),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
    )

    /** A representative row list spanning ACTIVE/RECENT/OTHER, with a checked+primary row, a
     *  favourite, an overridden tag (⚙ on the tag line), an auto-assign row and a highlighted
     *  (StudyPad current) row.
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
            label = label("L2", "Sermon notes", color = AbColor.palette[1], overrideStyle = BookmarkDisplayStyle.UNDERLINE),
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
        rows: List<ManageLabelsRow>? = null,
        styleTagsVisible: Boolean = true,
    ) = @androidx.compose.runtime.Composable {
        ManageLabelsScreen(
            title = "Manage labels",
            // rows(mode) below: explicit `this.` is load-bearing -- the `rows` PARAMETER above
            // shadows the `rows(mode)` MEMBER FUNCTION by simple name inside this scope.
            rows = rows ?: this.rows(mode),
            mode = mode,
            styleTagsVisible = styleTagsVisible,
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
            styleTagsVisible = true,
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
     *  Row S2's whole-verse axis differs so its second tag appears; S4 and the rest inherit, so
     *  they show one tag each; S5 carries all three tags at once (selection, whole-verse AND a
     *  workspace override), the ⚙-marked tag the row's second line ends with now that the ⚙ has
     *  moved out of the trailing grid. heightDp raised 500 -> 700 for the extra row plus room for
     *  any tag-line wrap. */
    @Test fun manageLabels_styles() =
        captureMatrix("ManageLabels", "styles", heightDp = 700, content = screen(ManageLabelsMode.ASSIGN, rows = styleRows()))

    /**
     * Fix round 1: whether `LabelStyleTag`'s `tagMaxWidth` (`widthIn(max = 110.dp)`,
     * `ManageLabelsScreen.kt`) actually binds a tag's width at all -- checked with the widest real
     * translated tag string, in a mode where the surrounding column is wide enough that the CAP,
     * not the column, is what a reader is looking at.
     *
     * The column is NOT a constant 110dp+ everywhere: at the goldens' 320dp width the content box
     * is 288dp (320 - 2x16 padding), minus the 24dp glyph and 12dp spacer = 252dp, minus whatever
     * the trailing grid reserves for the mode. ASSIGN reserves 4x48=192dp (checkbox + bolt + heart
     * + primary) -> a 60dp name/tag column; WORKSPACE reserves 3x48=144dp -> 108dp. Both are
     * narrower than the 110dp cap, so in [manageLabels_styles] (ASSIGN) the COLUMN truncates the
     * tag long before the cap could -- that capture cannot show whether the cap itself works.
     * STUDYPAD reserves nothing (`showCheckboxes`/`workspaceEdits`/`primaryShown` all false) -> a
     * 252dp column, comfortably wider than the 110dp cap, which is why this capture uses it: here
     * the cap is the binding constraint, not the column.
     *
     * The pressing string is French's `display_mode_marker`, "Marqueur uniquement" (19 chars) --
     * the widest of any of the four display-mode strings in the whole `res` tree (checked with a
     * one-off scan of every locale's strings.xml; Vietnamese's 16-char `display_mode_highlight`,
     * used in an earlier draft of this test, presses less hard). It lands on row S2's WHOLE-VERSE
     * tag ([styleRows]'s `wholeVerseStyle = BookmarkDisplayStyle.MARKER`) and additionally appends
     * the superscript marker glyph ([net.bible.sharedui.bookmark.SuperscriptMarker]), so this is the single hardest-pressing
     * real tag this codebase can render. Loaded via `@Config(qualifiers = "fr")`, the same real
     * `@Config` locale mechanism `manageLabels_assign_rtl` already uses for Arabic -- there is no
     * separate fixture-only string override point (see [screen]/[label], which take no `Strings`
     * parameter).
     *
     * Read this image for: measure S2's second tag -- the one after the " · " separator, carrying
     * "Marqueur uniquement" plus its superscript dot -- from its own left edge (right after the
     * separator) to its own right edge (the last visible glyph or the ellipsis, whichever is
     * later). At this capture's density (1.0), 1px = 1dp. If that measured span is at or under
     * 110px, the cap is binding as intended (the string may also simply be short of 110dp and
     * render whole with no ellipsis at all -- either is a pass). If it measures MEANINGFULLY MORE
     * than 110px -- visibly reaching toward the rest of the 252dp column rather than stopping near
     * its own 110dp box -- the cap has failed to bind and `tagMaxWidth` needs a real fix.
     */
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "fr")
    fun manageLabels_styles_longName() =
        captureGolden(
            "ManageLabels", "styles_longname", EDGE_MODE, heightDp = 700,
            content = screen(ManageLabelsMode.STUDYPAD, rows = styleRows()),
        )

    /** WORKSPACE: the trailing grid's ⚡/♥/🔖 columns, an override tag (⚙) on the tag line, and
     *  the Unlabeled row (mode.showUnassigned) rendered with a plain (non-clickable) icon and its
     *  two workspace columns reserved empty -- the Task-3 parity fix. */
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

    /** Deliberately covers every combination the grid has to line up: on/off × on/off × primary,
     *  not-primary-but-selected, and not-selected (the inert 🔖) -- plus the Unlabeled row for the
     *  GEOMETRY of its two reserved-empty workspace columns, not as a reachable ASSIGN state: real
     *  ASSIGN (mode.showUnassigned == false) never shows an Unlabeled row at all, so this row here
     *  renders WITH a checkbox, which production never does. The reachable Unlabeled state (no
     *  checkbox, WORKSPACE/HIDELABELS) is [manageLabels_workspace]. */
    private fun iconGridRows(): List<ManageLabelsRow> = listOf(
        ManageLabelsRow.Item(
            label = label("G1", "All on", favourite = true),
            checked = true, isAutoAssign = true, isPrimary = true, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label("G2", "Selected, not primary", color = AbColor.palette[1], favourite = true),
            checked = true, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label("G3", "Nothing set", color = AbColor.palette[2]),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label("G4", "Auto-assign only", color = AbColor.palette[3]),
            checked = false, isAutoAssign = true, isPrimary = false, highlighted = false,
        ),
        ManageLabelsRow.Item(
            label = label("unlabeled", "Unlabeled", isUnlabeled = true),
            checked = false, isAutoAssign = false, isPrimary = false, highlighted = false,
        ),
    )

    /** The trailing grid, which is what round 12a is about: every row shows ⚡, ♥ and 🔖 in the same
     *  three columns, each in its true or false state -- a HOLLOW bolt when auto-assign is off (not
     *  Material's outlined bolt, which is the same solid shape), a hollow heart, and a muted inert
     *  🔖 on the rows that are not selected. The Unlabeled row reserves the two workspace slots
     *  empty rather than shifting its 🔖 left. Read the columns, not the icons. */
    @Test fun manageLabels_iconGrid() =
        captureMatrix(
            "ManageLabels", "iconGrid", heightDp = 800,
            content = screen(ManageLabelsMode.ASSIGN, rows = iconGridRows()),
        )
}
