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

package net.bible.sharedui.bookmark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbIcons
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.search.styledTextToAnnotatedString
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.LocalDisplayColorMode

/**
 * Stateless port of the classic `ManageLabels` activity / `manage_labels.xml` +
 * `ManageLabelItemAdapter`. Every value comes from [rows]/[mode] (mirroring the classic adapter's
 * derived per-item control visibility); every mutation is forwarded to the host via the action
 * lambdas, which the host wires 1:1 to a `ManageLabelsController` (a later task). [iconSlot] renders
 * the label's leading glyph (custom icon, or the built-in default when `customIcon == null`) —
 * Android drawable resources live host-side, so this screen never touches them; [actions] is the
 * top-bar overflow (new/help/reorder/reset/export-StudyPads/import-StudyPads — the single overflow
 * menu, all host-built; matches classic's one `manage_labels_options_menu.xml`).
 *
 * Design note on the leading icon: earlier this slot doubled as an *auto-assign* control -- a
 * plain colour-filled circle replaced the label's own icon whenever the label was auto-assigned in
 * a workspace-editing [mode], mirroring classic's separate label/auto-assign-circle `ImageView`.
 * That consolidation is gone: the leading slot is now identity only. [iconSlot] renders the
 * label's own icon (custom, or the host's built-in default when
 * [net.bible.sharedcore.bookmark.LabelItem.customIcon] is `null`), tinted with the label's own
 * colour via [net.bible.sharedcore.theme.accentArgbFor] -- the same call the editor's avatar
 * already makes -- never a solid dot standing in for the icon. Auto-assign membership is no longer
 * expressed by replacing this glyph; it has its own explicit ⚡ toggle in the trailing grid, in the
 * same visual grammar as the favourite heart beside it (filled + tinted on, HOLLOW + muted off --
 * a real hollow bolt, not Material's "outlined" one, which is the same solid silhouette). The
 * trailing controls are now a fixed grid of same-width columns rather than a run of independently
 * gated icons, so ⚡/♥/🔖 line up down the whole list whether or not a given row's control is on;
 * the ⚙ override indicator no longer squats in that run at all -- it moved to the row's tag line,
 * where it marks which of up to three style tags is imposed by this workspace's override.
 *
 * [searchActions] is a second host action slot, rendered in the *search* bar (alongside this
 * screen's own [SearchModeMenu]) rather than the normal one [actions] occupies. The host puts its
 * New (⊕) icon there: without it, a search that finds nothing had no way to become a label seeded
 * with the query, because the only path to that was the ⊕ in [actions], which the screen never
 * draws while search is active.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageLabelsScreen(
    title: String,
    rows: List<ManageLabelsRow>,
    mode: ManageLabelsMode,
    searchText: String,
    searchMode: SearchMode,
    onSearch: (String) -> Unit,
    onSetSearchMode: (SearchMode) -> Unit,
    searchModeActive: Boolean,
    onCloseSearch: () -> Unit,
    onRowClick: (labelId: String) -> Unit,
    onRowLongClick: (labelId: String) -> Unit,
    onToggleChecked: (labelId: String) -> Unit,
    onToggleFavourite: (labelId: String) -> Unit,
    onSetPrimary: (labelId: String) -> Unit,
    onToggleAutoAssign: (labelId: String) -> Unit,
    onUp: () -> Unit,
    iconSlot: @Composable (customIcon: String?, tint: Color) -> Unit,
    actions: @Composable RowScope.() -> Unit,
    searchActions: @Composable RowScope.() -> Unit,
) {
    val strings = LocalStrings.current

    AbScaffold(
        title = title,
        // In search mode the bar is entirely the search field (AbTopAppBar's contract), so the
        // up-arrow and the normal actions are not drawn at all; passing them anyway would be
        // redundant with that contract rather than disagreeing with it, but nulling onNavigateUp
        // keeps the intent readable at the call site.
        onNavigateUp = if (searchModeActive) null else onUp,
        actions = { if (!searchModeActive) actions() },
        search = if (searchModeActive) {
            AbTopBarSearchState(
                query = searchText,
                imeRequest = AbSearchImeRequest.Focus,
                placeholder = strings.labelsSearchHint,
            )
        } else null,
        searchCallbacks = if (searchModeActive) {
            AbTopBarSearchCallbacks(
                onQueryChange = onSearch,
                onClose = onCloseSearch,
                onImeRequestHandled = {},
            )
        } else null,
        searchActions = {
            if (searchModeActive) {
                // Host's search-bar actions first (its ⊕ lands here — see the KDoc above), then this
                // screen's own mode menu, so the bar reads [back | query | host actions | mode | ✕]
                // and the built-in Clear button stays the edge-most control.
                searchActions()
                SearchModeMenu(mode = mode, searchMode = searchMode, onSetSearchMode = onSetSearchMode, strings = strings)
            }
        },
    ) { padding: PaddingValues ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows, key = ::rowKey) { row ->
                    when (row) {
                        is ManageLabelsRow.Header -> CategoryHeaderRow(row.category, strings)
                        is ManageLabelsRow.Item -> LabelItemRow(
                            row = row,
                            mode = mode,
                            onRowClick = onRowClick,
                            onRowLongClick = onRowLongClick,
                            onToggleChecked = onToggleChecked,
                            onToggleFavourite = onToggleFavourite,
                            onSetPrimary = onSetPrimary,
                            onToggleAutoAssign = onToggleAutoAssign,
                            iconSlot = iconSlot,
                            strings = strings,
                        )
                        is ManageLabelsRow.SearchResult -> SearchResultRow(
                            row = row,
                            onRowClick = onRowClick,
                            strings = strings,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The search bar's mode picker: one icon + menu replacing the two widgets this screen used to draw
 * side by side (a 3-way dropdown in STUDYPAD, a 2-state text button everywhere else). The item set
 * is derived from [mode], so behaviour per mode is unchanged — only one widget now expresses it.
 * The content option is StudyPad-only because only StudyPads have searchable content.
 *
 * The icon's contentDescription is [Strings.searchOptions] ("Search settings"), not
 * [Strings.search] ("Find") — the bar it sits in is already a search field, so a TalkBack user
 * hearing "Find, button" for the control that opens the *match-mode* menu would be misled.
 */
@Composable
private fun SearchModeMenu(
    mode: ManageLabelsMode,
    searchMode: SearchMode,
    onSetSearchMode: (SearchMode) -> Unit,
    strings: Strings,
) {
    var expanded by remember { mutableStateOf(false) }
    AbActionIcon(Icons.Filled.Tune, strings.searchOptions) { expanded = true }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ManageLabelsSearchModeMenuRows(
            mode = mode,
            searchMode = searchMode,
            onSetSearchMode = { expanded = false; onSetSearchMode(it) },
            strings = strings,
        )
    }
}

/**
 * The mode menu's item set, factored out of [SearchModeMenu] for the same reason
 * `WorkspaceRowMenuRows` is (see `WorkspaceSelectorScreen.kt`): an expanded `DropdownMenu` cannot be
 * photographed — it hangs Roborazzi, and two open popups on one page hang the whole `:app` suite —
 * so the golden renders this composable directly, inside a plain `Column`, instead of opening the
 * real popup.
 *
 * The two name-match rows use [Strings.searchModeNameStart] / [Strings.searchModeNameContains]
 * ("Name (from start)" / "Name (contains)"), translated in 50 locales — NOT the strings behind
 * `R.string.match_start_of_text` / `R.string.match_any_text` ("Ab*" / "*ab*", removed from
 * [Strings] as unused once this menu stopped referencing them), which were the label of
 * classic's 40dp toggle *button* and have zero locale translations, so using them here made this
 * menu read as "Ab*" / "*ab*" / "Content" in every language.
 */
@Composable
fun ManageLabelsSearchModeMenuRows(
    mode: ManageLabelsMode,
    searchMode: SearchMode,
    onSetSearchMode: (SearchMode) -> Unit,
    strings: Strings,
) {
    AbMenuItem(
        text = strings.searchModeNameStart,
        onClick = { onSetSearchMode(SearchMode.NAME_START) },
        icon = { Icon(Icons.Filled.TextFields, contentDescription = null) },
        checkable = true,
        checked = searchMode == SearchMode.NAME_START,
    )
    AbMenuItem(
        text = strings.searchModeNameContains,
        onClick = { onSetSearchMode(SearchMode.NAME_CONTAINS) },
        icon = { Icon(Icons.Filled.Abc, contentDescription = null) },
        checkable = true,
        checked = searchMode == SearchMode.NAME_CONTAINS,
    )
    if (mode == ManageLabelsMode.STUDYPAD) {
        AbMenuItem(
            text = strings.searchModeContent,
            onClick = { onSetSearchMode(SearchMode.CONTENT) },
            icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
            checkable = true,
            checked = searchMode == SearchMode.CONTENT,
        )
    }
}

private fun rowKey(row: ManageLabelsRow): String = when (row) {
    is ManageLabelsRow.Header -> "header_${row.category}"
    is ManageLabelsRow.Item -> "item_${row.label.id}"
    is ManageLabelsRow.SearchResult -> "search_${row.labelId}"
}

/** Non-interactive section header ("Selected labels" / "Recent labels" / "Other labels"). */
@Composable
private fun CategoryHeaderRow(category: LabelCategory, strings: Strings) {
    Text(
        text = when (category) {
            LabelCategory.ACTIVE -> strings.activeLabelsHeader
            LabelCategory.RECENT -> strings.recentLabelsHeader
            LabelCategory.OTHER -> strings.otherLabelsHeader
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LabelItemRow(
    row: ManageLabelsRow.Item,
    mode: ManageLabelsMode,
    onRowClick: (String) -> Unit,
    onRowLongClick: (String) -> Unit,
    onToggleChecked: (String) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onToggleAutoAssign: (String) -> Unit,
    iconSlot: @Composable (customIcon: String?, tint: Color) -> Unit,
    strings: Strings,
) {
    val label = row.label
    // Identity glyph tint: accentArgbFor is what greys it in BW / e-ink, same call the editor
    // avatar makes (LabelEditScreen.kt:235). The style tag below uses the READER's monochrome
    // substitutions instead, via bookmarkStyleDecoration -- two different rules on purpose, one for
    // a workspace accent and one for what the page actually looks like.
    val glyphTint = Color(accentArgbFor(label.color, LocalDisplayColorMode.current))
    val markerGlyph: @Composable () -> Unit = { iconSlot(label.customIcon, glyphTint) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onRowClick(label.id) },
                onLongClick = { onRowLongClick(label.id) },
            )
            // 48dp, not classic's 40dp: the row carries up to four tappable controls and 48dp is
            // the Material minimum touch target. The old 40dp icon Box plus 8dp vertical padding
            // made this ~56dp with a mostly empty leading column.
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { markerGlyph() }

        Spacer(Modifier.width(12.dp))

        // ONLY this column is weighted. A weighted trailing element would make the LAST child the
        // overflow casualty, and a clipped IconButton stays tappable and can steal its neighbour's
        // tap -- so the name absorbs any shortage and the tag and controls keep intrinsic width.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.highlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StyleTagRow(label = label, markerGlyph = markerGlyph, strings = strings)
        }

        // A fixed grid, not a run of conditional icons. Every slot is exactly TrailingSlotSize wide
        // whether or not it draws anything, because the feedback was about COLUMNS: with each
        // control gated on its own condition, no two rows put the bolt, the heart and the bookmark
        // in the same place. `mode` is constant for the whole list, so a mode without a given
        // control has no column at all and nothing to align; the only per-row variance that needs
        // reserving is the Unlabeled pseudo-label, which has no workspace toggles.
        if (mode.showCheckboxes) {
            TrailingSlot { Checkbox(checked = row.checked, onCheckedChange = { onToggleChecked(label.id) }) }
        }

        if (mode.workspaceEdits) {
            if (label.isUnlabeled) {
                Spacer(Modifier.width(TrailingSlotSize * 2))
            } else {
                // Filled vs HOLLOW, not filled vs Material's "outlined" bolt -- Icons.Outlined.Bolt
                // is the same solid silhouette, so the off state was a tint change and read as no
                // state at all (AbIcons.BoltOutline exists for exactly this).
                IconButton(onClick = { onToggleAutoAssign(label.id) }, modifier = Modifier.size(TrailingSlotSize)) {
                    Icon(
                        if (row.isAutoAssign) Icons.Filled.Bolt else AbIcons.BoltOutline,
                        contentDescription = strings.autoAssignLabelSwitchLabel,
                        tint = if (row.isAutoAssign) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(TrailingIconSize),
                    )
                }
                IconButton(onClick = { onToggleFavourite(label.id) }, modifier = Modifier.size(TrailingSlotSize)) {
                    Icon(
                        if (label.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = strings.favouriteLabelSwitchLabel,
                        tint = if (label.favourite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(TrailingIconSize),
                    )
                }
            }
        }

        if (mode.primaryShown) {
            if (row.checked) {
                IconButton(onClick = { onSetPrimary(label.id) }, modifier = Modifier.size(TrailingSlotSize)) {
                    Icon(
                        if (row.isPrimary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = strings.primaryLabelSwitchLabel,
                        tint = if (row.isPrimary) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(TrailingIconSize),
                    )
                }
            } else {
                // Shown but inert: the column has to exist on every row, and the false state has to
                // be visible the way the heart's is -- but a tap here would both select the label
                // AND promote it, two things from one gesture, so it is an indicator only.
                //
                // The description is NOT silenced -- this is a state to report, not decoration: a
                // primary column that only announces when it happens to be active would tell a
                // screen-reader user this row has no primary concept at all. disabled() is what
                // makes that honest -- announced as present-but-disabled, matching the visible
                // muted glyph, rather than either a phantom control (a description with no onClick
                // behind it) or silence (a state that vanishes for this input mode alone).
                TrailingSlot(
                    // mergeDescendants = true: this slot must be its OWN merge boundary, not fold
                    // into the row's merged node. The row is a combinedClickable, and
                    // AbstractClickableNode.shouldMergeDescendantSemantics returns true
                    // unconditionally -- so without this, disabled() (which has no custom merge
                    // policy; the default is parentValue ?: childValue) bubbles straight up and the
                    // WHOLE ROW announces as disabled, while staying fully clickable. A merging
                    // descendant is not folded into an ancestor's merge scope -- the same reason the
                    // sibling IconButtons above (clickable themselves) escape this. Verified by
                    // ManageLabelsInertPrimaryA11yTest. Do not drop this parameter: it is the fix,
                    // not decoration.
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = strings.primaryLabelSwitchLabel
                        disabled()
                    },
                ) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        // null here: the description lives on the slot's own semantics node (set
                        // above), not on the icon -- one announcement per row, not two.
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = InertIndicatorAlpha),
                        modifier = Modifier.size(TrailingIconSize),
                    )
                }
            }
        }
    }
}

/** One trailing-grid cell. 48dp is the Material minimum touch target and the width every slot
 *  reserves, drawn or not, so the controls form columns down the list. */
private val TrailingSlotSize = 48.dp
private val TrailingIconSize = 20.dp

/** The inert primary indicator: visible enough to show the column and its off state, muted enough
 *  not to invite a tap that would do nothing. */
private const val InertIndicatorAlpha = 0.38f

/** One cell of the row's trailing grid. Fixed width whether it draws a control, an indicator or
 *  nothing: an `IconButton`'s intrinsic size is not something to align columns on. */
@Composable
private fun TrailingSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.size(TrailingSlotSize), contentAlignment = Alignment.Center) { content() }
}

/**
 * The row's second line: what this label draws, in up to three tags.
 *
 * 1. the label's own selection style, decorated **partially** — a text-selection bookmark covers
 *    part of a verse, and that is what the half tag says;
 * 2. its whole-verse style, decorated fully, only when it is really set to something of its own;
 * 3. the style this workspace's override imposes, decorated fully and marked with a ⚙.
 *
 * The ⚙ is the one place that symbol survives this round. In the editor the same tag sits under the
 * "This workspace" heading, which supplies its meaning, so the indicator there is redundant; a list
 * row has no heading, so the tag needs one mark to say where it comes from — and ⚙ is already this
 * codebase's override symbol, with `workspace_override_indicator` available as its description. An
 * override takes BOTH axes (`Label.withStyleOverrides`), which is why it is decorated in full: it is
 * what the reader draws here, on either kind of bookmark. The label's own two tags stay, because
 * they are the label's identity and travel with it to every other workspace.
 *
 * A `FlowRow` rather than a `Row`: three tags do not fit one 320dp line, and the third wrapping is
 * better than the third being clipped away.
 */
@Composable
private fun StyleTagRow(label: LabelItem, markerGlyph: @Composable () -> Unit, strings: Strings) {
    // Bounded on every branch: an intrinsic-width tag can win the space contest against the
    // weighted name and make it vanish entirely (round-1 fix -- Finding 1). 110dp is a CEILING,
    // not what actually binds a tag's width in every mode -- in ASSIGN/WORKSPACE the name/tag
    // column itself is narrower than 110dp (the trailing grid's reserved slots leave ~60dp in
    // ASSIGN, ~108dp in WORKSPACE at the goldens' 320dp width), so the column is the real
    // constraint there; the cap only binds where no slots are reserved (STUDYPAD/HIDELABELS,
    // ~252dp/~204dp of column). Either way this stops an intrinsic-width tag from winning the
    // space contest against the weighted name.
    val tagMaxWidth = Modifier.widthIn(max = 110.dp)
    // itemVerticalAlignment explicit rather than FlowRow's default Top: every tag here is one
    // line tall today, but a centred baseline is the right call if a taller tag (e.g. a larger
    // marker glyph) ever wraps to a second FlowRow line, matching the CenterVertically the
    // per-tag Rows below already use for their own separator+icon+text groups.
    FlowRow(modifier = Modifier.fillMaxWidth(), itemVerticalAlignment = Alignment.CenterVertically) {
        LabelStyleTag(
            label.selectionStyle,
            label.color,
            modifier = tagMaxWidth,
            decoratePartially = true,
            iconSlot = markerGlyph,
        )
        // Only when the whole-verse axis is really DIFFERENT: null means it inherits, and an
        // explicitly-set-but-equal value reads the same way, so a second identical tag is noise.
        val wholeVerse = label.wholeVerseStyle
        if (wholeVerse != null && wholeVerse != label.selectionStyle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagSeparator()
                LabelStyleTag(wholeVerse, label.color, modifier = tagMaxWidth, iconSlot = markerGlyph)
            }
        }
        val override = label.overrideStyle
        if (override != null) {
            // Separator, mark and tag in ONE Row so a wrap can never leave the ⚙ stranded at the
            // end of a line with its tag on the next.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagSeparator()
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = strings.overrideIndicatorDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(2.dp))
                LabelStyleTag(override, label.color, modifier = tagMaxWidth, iconSlot = markerGlyph)
            }
        }
    }
}

@Composable
private fun TagSeparator() {
    Text(
        " · ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A StudyPad content-search hit: the label's colour dot + name, a match-count line
 * (`search_results_match`/`search_results_matches`, mirroring classic `ManageLabelItemAdapter`'s
 * `VIEW_TYPE_SEARCH_RESULT`), and the first match's snippet with its [ManageLabelsRow.SearchResult.matchStart]..
 * [ManageLabelsRow.SearchResult.matchEnd] span highlighted — reusing the same [StyledText] →
 * [styledTextToAnnotatedString] renderer the Batch 5 search-result screens use, rather than a
 * bespoke highlighter. Tapping the row hands the label id to [onRowClick]; the host resolves it to
 * a StudyPad navigation using [ManageLabelsRow.SearchResult.firstMatchEntryId].
 */
@Composable
private fun SearchResultRow(
    row: ManageLabelsRow.SearchResult,
    onRowClick: (String) -> Unit,
    strings: Strings,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick(row.labelId) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(AbColor.toComposeColor(row.color), CircleShape),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (row.matchCount == 1) {
                        strings.searchResultsMatch
                    } else {
                        strings.searchResultsMatches(row.matchCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = styledTextToAnnotatedString(searchResultSnippetStyledText(row)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Splits [ManageLabelsRow.SearchResult.snippet] into up to three [StyledRun]s around
 * `[matchStart, matchEnd)`, with the matched slice flagged [StyledRun.highlight] so
 * [styledTextToAnnotatedString] paints it as the same bold/pill highlight the Batch 5
 * search-result screens use. Falls back to a single plain run when the snippet is empty or the
 * bounds are out of range (e.g. a host-side "no matches" placeholder row).
 */
private fun searchResultSnippetStyledText(row: ManageLabelsRow.SearchResult): StyledText {
    val text = row.snippet
    val start = row.matchStart
    val end = row.matchEnd
    if (text.isEmpty() || start < 0 || end <= start || end > text.length) {
        return StyledText.plain(text)
    }
    val runs = buildList {
        if (start > 0) add(StyledRun(text.substring(0, start)))
        add(StyledRun(text.substring(start, end), highlight = true))
        if (end < text.length) add(StyledRun(text.substring(end)))
    }
    return StyledText(runs)
}
