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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Abc
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbColor
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
 * expressed by replacing this glyph; it gets its own explicit control in the trailing run (a later
 * task's ⚡ toggle).
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
    // Wired to the ⚡ toggle in the trailing run in the next task; the leading glyph deliberately
    // stopped being its click target here.
    @Suppress("UNUSED_PARAMETER") onToggleAutoAssign: (String) -> Unit,
    iconSlot: @Composable (customIcon: String?, tint: Color) -> Unit,
    strings: Strings,
) {
    val label = row.label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onRowClick(label.id) },
                onLongClick = { onRowLongClick(label.id) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The glyph is identity only: the label's icon (or the host's built-in default when
        // customIcon == null) in the label's own colour. accentArgbFor is what greys it in BW /
        // e-ink, the same call LabelEditScreen.kt:235 makes for the editor avatar. It is NOT a
        // control any more -- classic's hidden "tap the icon to toggle auto-assign" gesture is
        // replaced by an explicit ⚡ toggle in the trailing run (Task 6).
        val glyphTint = Color(accentArgbFor(label.color, LocalDisplayColorMode.current))
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            iconSlot(label.customIcon, glyphTint)
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = label.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (row.highlighted) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (label.hasOverride) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = strings.overrideIndicatorDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(start = 4.dp),
            )
        }

        if (mode.showCheckboxes) {
            Checkbox(checked = row.checked, onCheckedChange = { onToggleChecked(label.id) })
        }

        if (mode.workspaceEdits && !label.isUnlabeled) {
            IconButton(onClick = { onToggleFavourite(label.id) }) {
                Icon(
                    if (label.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = strings.favouriteLabelSwitchLabel,
                    tint = if (label.favourite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (mode.primaryShown && row.checked) {
            IconButton(onClick = { onSetPrimary(label.id) }) {
                Icon(
                    if (row.isPrimary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = strings.primaryLabelSwitchLabel,
                    tint = if (row.isPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
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
