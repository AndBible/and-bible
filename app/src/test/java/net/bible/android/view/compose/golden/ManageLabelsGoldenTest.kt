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
     *  favourite, an overridden tag (carrying the override mark on the tag line), an auto-assign
     *  row and a highlighted (StudyPad current) row.
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
     *  workspace override), the tag carrying the override mark that the row's second line ends
     *  with now that the override mark has moved out of the trailing grid. heightDp raised
     *  500 -> 700 for the extra row plus room for any tag-line wrap. */
    @Test fun manageLabels_styles() =
        captureMatrix("ManageLabels", "styles", heightDp = 700, content = screen(ManageLabelsMode.ASSIGN, rows = styleRows()))

    /** [styleRows] with S2's NAME made long, for [manageLabels_styles_longName]. S2 is the row
     *  that carries TWO tags (its whole-verse axis really differs), so it is where a long name and
     *  a full tag line compete for the same 132dp column (WORKSPACE; see that test's own KDoc). */
    private fun longNameStyleRows(): List<ManageLabelsRow> = styleRows().map { row ->
        if (row is ManageLabelsRow.Item && row.label.id == "S2") {
            row.copy(label = row.label.copy(name = "Notes de sermon du dimanche matin"))
        } else {
            row
        }
    }

    /**
     * Width pressure on the row's name+tag column, in the widest mode that still SHOWS tags.
     *
     * Round 15a changed what this capture can prove, twice over, and the honest statement of what
     * is left is the point of this KDoc.
     *
     * It began as the evidence that `LabelStyleTag`'s `tagMaxWidth` (`widthIn(max = 110.dp)`,
     * `ManageLabelsScreen.kt`) binds a tag's width at all. That needed a mode whose column is
     * WIDER than the 110dp cap, so the cap rather than the column is the binding constraint, and
     * STUDYPAD was the only one: at the goldens' 320dp width the content box is 288dp
     * (320 - 2x16 padding), minus the 24dp glyph and 12dp spacer = 252dp, minus whatever the
     * trailing grid reserves. STUDYPAD reserved nothing -> 252dp. The pressing string was French's
     * `display_mode_marker`, "Marqueur uniquement" (19 chars), the widest display-mode string in
     * the whole `res` tree, landing on S2's whole-verse tag plus its superscript glyph.
     *
     * Both halves of that are gone:
     *  - §4.2 replaced the tag text with the AXIS word, so no `display_mode_*` string is rendered
     *    here any more and "Marqueur uniquement" cannot appear at all.
     *  - §4.3 gated the tags to ASSIGN and WORKSPACE, so STUDYPAD now draws NO tag line. Captured
     *    in STUDYPAD this test would show a plain one-line list and prove nothing whatsoever.
     *
     * So the capture moves to WORKSPACE, the widest mode that still shows tags. Its trailing grid
     * reserves 3 slots at `TrailingSlotSize` = 40dp (not 48 -- `ManageLabelsScreen.kt`), for a
     * 132dp name+tag column; ASSIGN reserves those same 3 slots plus a fourth for the leading
     * checkbox (and its 4dp spacer), for an 88dp column. **Both figures are measured directly off
     * the recorded goldens** (`ManageLabels_workspace_light.png` / `ManageLabels_assign_light.png`:
     * the three trailing glyphs sit centred at x=204/244/284, i.e. 40dp slots whose column starts
     * at x=184, and the name+tag column runs 52->184 in WORKSPACE and 96->184 in ASSIGN) rather
     * than re-derived here -- a reader who doubts them should measure the PNG, not recompute from
     * this comment. So **the 110dp tag cap IS still exercisable** -- in WORKSPACE, whose 132dp
     * column is wider than the cap, the cap rather than the column is what stops an intrinsic-width
     * tag from winning the space contest; ASSIGN's 88dp column is narrower than the cap and binds
     * first, same as before.
     *
     * What this capture nonetheless does NOT exercise the cap with: the tag text now comes from
     * `Strings` (the axis words), and the golden harness has no fixture-only string override point,
     * so no long tag string can be injected until the `bookmark_style_tag_*` keys are translated --
     * see the `TODO(post-Transifex)` below.
     *
     * What it proves NOW: that the name column ellipsises under a long name WITH the tag line
     * present, and that the two-tag line and the trailing grid still lay out around it. The long
     * name is the fixture's own ([longNameStyleRows]), not a translation, because the three new
     * `bookmark_style_tag_*` keys ship English-first -- under `qualifiers = "fr"` they fall back to
     * English, which is exactly why the French run no longer presses the tags.
     *
     * `qualifiers = "fr"` is kept deliberately: it is the only place in this file that renders the
     * screen under a non-default locale with real translated chrome around the fixture, and the
     * round-15a English fallback is itself worth seeing in an image.
     *
     * TODO(post-Transifex): re-point this at the longest translation of bookmark_style_tag_* -- until
     * they are translated, the French run falls back to English and this golden no longer proves the
     * 110dp tag cap the way "Marqueur uniquement" did.
     */
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "fr")
    fun manageLabels_styles_longName() =
        captureGolden(
            "ManageLabels", "styles_longname", EDGE_MODE, heightDp = 700,
            content = screen(ManageLabelsMode.WORKSPACE, rows = longNameStyleRows()),
        )

    /**
     * The ⋮ "Show style examples" toggle, OFF (§4.5), in WORKSPACE -- the mode where the examples
     * are relevant, so the toggle is the only thing suppressing them.
     *
     * Read this image for two things: every row falls to ONE line, and the trailing ⚡/♥/🔖 grid
     * has not moved a pixel. `heightIn(min = 48.dp)` is what guarantees the second, so a row that
     * lost its tag line must still be 48dp tall -- if the rows visibly tightened, the gate was put
     * somewhere that changes the row's measured height rather than only its content.
     */
    @Test fun manageLabels_workspace_noTags() =
        captureGolden(
            "ManageLabels", "workspace_noTags", EDGE_MODE, heightDp = 400,
            content = screen(ManageLabelsMode.WORKSPACE, styleTagsVisible = false, rows = styleRows()),
        )

    /** WORKSPACE: the trailing grid's ⚡/♥/🔖 columns, a tag carrying the override mark on the tag line, and
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
