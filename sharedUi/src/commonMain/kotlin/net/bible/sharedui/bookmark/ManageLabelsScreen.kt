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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.search.styledTextToAnnotatedString
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * Stateless port of the classic `ManageLabels` activity / `manage_labels.xml` +
 * `ManageLabelItemAdapter`. Every value comes from [rows]/[mode] (mirroring the classic adapter's
 * derived per-item control visibility); every mutation is forwarded to the host via the action
 * lambdas, which the host wires 1:1 to a `ManageLabelsController` (a later task). [iconSlot] renders
 * the label's leading glyph (custom icon, or the built-in default when `customIcon == null`) —
 * Android drawable resources live host-side, so this screen never touches them; [actions] is the
 * top-bar overflow (reset/reorder/etc, also host-built).
 *
 * Design note on the leading icon: the classic adapter shows two *separate* `ImageView`s per row
 * (a label/auto-assign-circle glyph, and — further along the row — an optional custom-icon glyph).
 * This port's [iconSlot] contract only carries one glyph slot, so the two are consolidated here:
 * when the label is auto-assigned in a workspace-editing [mode], the leading slot is a plain
 * colour-filled circle (mirrors `ic_label_circle`, drawn natively — no host round-trip needed for a
 * solid dot); otherwise [iconSlot] renders the label's own icon (custom, or the host's built-in
 * default when [net.bible.sharedcore.bookmark.LabelItem.customIcon] is `null`).
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
    onRowClick: (labelId: String) -> Unit,
    onRowLongClick: (labelId: String) -> Unit,
    onToggleChecked: (labelId: String) -> Unit,
    onToggleFavourite: (labelId: String) -> Unit,
    onSetPrimary: (labelId: String) -> Unit,
    onToggleAutoAssign: (labelId: String) -> Unit,
    onUp: () -> Unit,
    onExportStudyPads: () -> Unit,
    onImportStudyPads: () -> Unit,
    iconSlot: @Composable (customIcon: String?, colorArgb: Int) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val strings = LocalStrings.current

    AbScaffold(title = title, onNavigateUp = onUp, actions = actions) { padding: PaddingValues ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbSearchField(
                    value = searchText,
                    onValueChange = onSearch,
                    placeholder = strings.labelsSearchHint,
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 8.dp,
                )
                if (mode == ManageLabelsMode.STUDYPAD) {
                    // StudyPad content-search: a 3-way selector over all SearchModes.
                    SearchModeSelector(
                        searchMode = searchMode,
                        onSetSearchMode = onSetSearchMode,
                        strings = strings,
                    )
                } else {
                    // Non-STUDYPAD modes only ever filter by name: a simple two-state toggle.
                    val insideText = searchMode == SearchMode.NAME_CONTAINS
                    TextButton(
                        onClick = {
                            onSetSearchMode(if (insideText) SearchMode.NAME_START else SearchMode.NAME_CONTAINS)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (insideText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(if (insideText) strings.matchAnyText else strings.matchStartOfText)
                    }
                }
                // Export/import StudyPads overflow: visible in ALL modes (classic
                // ManageLabels.kt:379-386 onCreateOptionsMenu parity — only resetButton/reOrder
                // are mode-conditional there, export_studypads/import_studypads are not).
                AbOverflowMenu(contentDescription = null) { close ->
                    DropdownMenuItem(
                        text = { Text(strings.exportSomething(strings.studyPadsLabel)) },
                        onClick = { close(); onExportStudyPads() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.importItems(strings.studyPadsLabel)) },
                        onClick = { close(); onImportStudyPads() },
                    )
                }
            }

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
 * StudyPad-only 3-way search-mode picker (name-from-start / name-contains / content), a small
 * button showing the active mode's label that opens a dropdown over all [SearchMode] values —
 * the portable analogue of classic `ManageLabels`'s `PopupMenu` (`R.menu.search_mode_menu`).
 */
@Composable
private fun SearchModeSelector(
    searchMode: SearchMode,
    onSetSearchMode: (SearchMode) -> Unit,
    strings: Strings,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (searchMode) {
        SearchMode.NAME_START -> strings.searchModeNameStart
        SearchMode.NAME_CONTAINS -> strings.searchModeNameContains
        SearchMode.CONTENT -> strings.searchModeContent
    }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.padding(end = 4.dp)) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings.searchModeNameStart) },
                onClick = { expanded = false; onSetSearchMode(SearchMode.NAME_START) },
            )
            DropdownMenuItem(
                text = { Text(strings.searchModeNameContains) },
                onClick = { expanded = false; onSetSearchMode(SearchMode.NAME_CONTAINS) },
            )
            DropdownMenuItem(
                text = { Text(strings.searchModeContent) },
                onClick = { expanded = false; onSetSearchMode(SearchMode.CONTENT) },
            )
        }
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
    iconSlot: @Composable (customIcon: String?, colorArgb: Int) -> Unit,
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
        // Leading glyph: auto-assign circle (workspace-editing modes only) or the icon rendered by
        // the host's iconSlot (custom icon, or its built-in default when null). Clicking it toggles
        // auto-assign membership, same as the classic labelIcon click.
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (mode.workspaceEdits && !label.isUnlabeled) {
                        Modifier.clickable { onToggleAutoAssign(label.id) }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (mode.workspaceEdits && !label.isUnlabeled && row.isAutoAssign) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(AbColor.toComposeColor(label.color), CircleShape),
                )
            } else {
                iconSlot(label.customIcon, label.color)
            }
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
