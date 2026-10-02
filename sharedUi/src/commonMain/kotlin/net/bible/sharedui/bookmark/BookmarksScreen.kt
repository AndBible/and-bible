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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import net.bible.sharedui.components.volumeScrollTarget
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.bookmark.BookmarksDialog
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.search.styledTextToAnnotatedString
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * Stateless port of the classic `Bookmarks` activity / `BookmarkItemAdapter` +
 * `bookmark_actionbar_menu.xml` / `bookmark_context_menu.xml`. Every value comes from [rows]/
 * [filterLabels]/[sortMode]/[selection]; every mutation is forwarded to the host via the action
 * lambdas, which the host wires 1:1 to a `BookmarksController` (a later task).
 *
 * **Selection.** [selection] (a set of [BookmarkRow.id]) drives [AbSelectionScaffold]: non-empty
 * means selection mode is active. A row tap toggles selection while active, else opens the
 * bookmark ([onRowClick]); a long-press always reports [onRowLongClick] (the host decides whether
 * that enters selection). The contextual bar shows assign-labels + delete (classic
 * `bookmark_context_menu.xml`'s `assign_labels`/`delete`); the count is rendered by
 * [AbSelectionScaffold] itself. Exiting selection ([onClearSelection]) clears it.
 *
 * **Top bar (non-selection).** A conditional search icon (only when [showNotes], since search only
 * ever matches note text — see below), then two always-visible [AbActionIcon]s — Manage Labels,
 * then a quick-access sort icon reflecting the current [sortMode] (up/down arrow for ascending/
 * descending; classic only toasted the new order on tap, this port additionally encodes direction
 * in the icon) — plus an [AbOverflowMenu] with Show notes (checkable) / Export CSV / Import CSV.
 * This matches `bookmark_actionbar_menu.xml`: `manageLabels`/`sortByToggle` are
 * `showAsAction="always"`, `showNotes`/`exportCsv`/`importCsv` are overflow-only.
 *
 * **Filter + search.** The label filter is a [BookmarkFilterBar] chip (classic's label spinner)
 * that opens a searchable bottom sheet. Search lives in the top bar (via [AbSelectionScaffold]'s
 * `search`/`searchCallbacks`,
 * [searchModeActive] gating whether it renders) and only ever appears when [showNotes] is on
 * (classic hid `textSearchLayout` unless "Show notes" was on, since search only ever matched note
 * text) — [onToggleShowNotes] switching notes off also leaves search mode host-side, since its
 * trigger icon disappears from the bar at the same time.
 *
 * **Row.** A row of small tinted label-colour chips (classic `ic_label_24dp` circles, one per
 * non-speak label per [BookmarkRow.labelColors]'s contract), a speak icon when [BookmarkRow.isSpeak],
 * the title + date on one line, [BookmarkRow.content] rendered via [styledTextToAnnotatedString]
 * (bold selection highlight), and [BookmarkRow.notes] the same way when [showNotes] and non-null.
 * Content and notes are COLLAPSED by default (3 lines / 1 line) — classic was unbounded here and let
 * one multi-verse bookmark fill the screen. When either field is actually clipped, a chevron
 * appears; tapping it (via [onToggleExpand], driven by [expandedIds]) expands or re-collapses just
 * that row, without disturbing the row's own tap-to-open behaviour ([onRowClick]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen(
    title: String,
    rows: List<BookmarkRow>,
    filterLabels: List<BookmarkFilterLabel>,
    selectedFilterIndex: Int,
    sortMode: BookmarkSortMode,
    searchText: String,
    showNotes: Boolean,
    selection: Set<String>,
    expandedIds: Set<String>,
    loading: Boolean,
    onSelectFilter: (Int) -> Unit,
    onCycleSort: () -> Unit,
    onSearch: (String) -> Unit,
    searchModeActive: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onToggleShowNotes: () -> Unit,
    onRowClick: (id: String, index: Int) -> Unit,
    onRowLongClick: (id: String) -> Unit,
    onToggleSelected: (id: String) -> Unit,
    onToggleExpand: (id: String) -> Unit,
    onAssignSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onManageLabels: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onUp: () -> Unit,
    dialog: BookmarksDialog = BookmarksDialog.None,
    onConfirmDialog: () -> Unit = {},
    onDismissDialog: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val selectionMode = selection.isNotEmpty()

    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selection.size,
        onNavigateUp = onUp,
        onExitSelection = onClearSelection,
        actions = {
            if (showNotes) {
                AbActionIcon(
                    icon = Icons.Filled.Search,
                    contentDescription = strings.search,
                    onClick = onOpenSearch,
                )
            }
            AbActionIcon(
                icon = Icons.AutoMirrored.Filled.Label,
                contentDescription = strings.manageLabelsLabel,
                onClick = onManageLabels,
            )
            AbActionIcon(
                icon = sortIcon(sortMode),
                contentDescription = sortModeLabel(sortMode, strings),
                onClick = onCycleSort,
            )
            AbOverflowMenu(contentDescription = null) { close ->
                AbMenuItem(
                    text = strings.showNotesLabel,
                    onClick = { close(); onToggleShowNotes() },
                    icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    checkable = true,
                    checked = showNotes,
                )
                AbMenuItem(
                    text = strings.exportSomething("CSV"),
                    onClick = { close(); onExportCsv() },
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                )
                AbMenuItem(
                    text = strings.importItems("CSV"),
                    onClick = { close(); onImportCsv() },
                    icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                )
            }
        },
        selectionActions = {
            AbActionIcon(Icons.AutoMirrored.Filled.Label, contentDescription = strings.assignLabelsLabel, onClick = onAssignSelected)
            AbActionIcon(Icons.Filled.Delete, contentDescription = strings.deleteLabel, onClick = onDeleteSelected)
        },
        search = if (searchModeActive) {
            AbTopBarSearchState(query = searchText, imeRequest = AbSearchImeRequest.Focus, placeholder = strings.bookmarksSearchNotesHint)
        } else null,
        searchCallbacks = if (searchModeActive) {
            AbTopBarSearchCallbacks(
                onQueryChange = onSearch,
                onClose = onCloseSearch,
                onImeRequestHandled = {},
            )
        } else null,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BookmarkFilterBar(
                filterLabels = filterLabels,
                selectedFilterIndex = selectedFilterIndex,
                onSelectFilter = onSelectFilter,
            )
            if (loading) {
                AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            if (rows.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.emptyList, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().volumeScrollTarget(listState)) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        BookmarkListRow(
                            row = row,
                            showNotes = showNotes,
                            selectionMode = selectionMode,
                            selected = row.id in selection,
                            expanded = row.id in expandedIds,
                            onClick = { if (selectionMode) onToggleSelected(row.id) else onRowClick(row.id, index) },
                            onLongClick = { onRowLongClick(row.id) },
                            onToggleExpand = { onToggleExpand(row.id) },
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        is BookmarksDialog.ConfirmDelete -> AbConfirmDialog(
            title = null,
            message = strings.confirmDeleteBookmarks(dialog.count),
            confirmText = strings.yes,
            dismissText = strings.cancel,
            onConfirm = onConfirmDialog,
            onDismiss = onDismissDialog,
        )
        BookmarksDialog.None -> {}
    }
}

/** Icon for the quick-access sort control: direction (up/down) mirrors ascending/descending. */
private fun sortIcon(mode: BookmarkSortMode): ImageVector = when (mode) {
    BookmarkSortMode.BIBLE_ORDER -> Icons.Filled.ArrowUpward
    BookmarkSortMode.BIBLE_ORDER_DESC -> Icons.Filled.ArrowDownward
    BookmarkSortMode.CREATED_AT_DESC -> Icons.Filled.ArrowDownward
    BookmarkSortMode.CREATED_AT -> Icons.Filled.ArrowUpward
}

/** The current sort mode's descriptive label (classic `BookmarkSortOrder.description`: bible-order
 *  variants share one string, date-order variants share the other — direction is icon-only). */
private fun sortModeLabel(mode: BookmarkSortMode, strings: Strings): String =
    if (mode.isBibleOrder) strings.sortByBibleBookLabel else strings.sortByDateLabel

/** Collapsed line budgets. Deliberately asymmetric: the bible text is bodyMedium and the note is
 *  bodySmall, so three lines of the former and one of the latter read as comparable weight. */
private const val COLLAPSED_CONTENT_LINES = 3
private const val COLLAPSED_NOTES_LINES = 1

/**
 * One bookmark row: label-colour chips + speak icon + title/date line, then the highlighted
 * content, then notes (when shown). Mirrors `bookmark_list_item.xml`'s layout order.
 *
 * Rows are COLLAPSED by default ([COLLAPSED_CONTENT_LINES] / [COLLAPSED_NOTES_LINES]). Classic did
 * not bound these rows either, so one multi-verse bookmark could fill the screen; the line budget is
 * therefore an improvement on classic rather than a parity fix. The expand chevron appears only when
 * something is actually clipped, and carries its own `clickable`: an inner clickable consumes the
 * tap, so expanding never reaches the row's `combinedClickable` and a row tap still opens the
 * bookmark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkListRow(
    row: BookmarkRow,
    showNotes: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val strings = LocalStrings.current
    // Whether the collapsed text is really clipped. This is MEASURED, not derived: it depends on
    // the width and the font, which the model cannot know. Keyed by row id so a reused row
    // re-measures instead of inheriting its predecessor's answer.
    var contentClipped by remember(row.id) { mutableStateOf(false) }
    var notesClipped by remember(row.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
            }
            if (row.labelColors.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.labelColors.forEach { colorArgb ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(AbColor.toComposeColor(colorArgb), CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            if (row.isSpeak) {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = strings.speak,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = styledTextToAnnotatedString(row.content),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_CONTENT_LINES,
            overflow = TextOverflow.Ellipsis,
            // Guarded on !expanded so an expanded row (which never overflows) cannot clear the
            // flag and make the collapse affordance vanish.
            onTextLayout = { if (!expanded) contentClipped = it.hasVisualOverflow },
        )
        val notes = row.notes
        if (showNotes && notes != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = styledTextToAnnotatedString(notes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_NOTES_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) notesClipped = it.hasVisualOverflow },
            )
        }
        // The notes half of the gate is conditioned on the notes actually being SHOWN: the measured
        // flags self-correct while their Text stays in composition, but switching "Show notes" off
        // removes the notes Text entirely, so nothing could ever clear notesClipped -- leaving a row
        // with short content and a long note holding a chevron that expanded nothing visible.
        if (expanded || contentClipped || (showNotes && notes != null && notesClipped)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) strings.collapseRow else strings.expandRow,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClick = onToggleExpand)
                        // 12dp of padding on every side around the 24dp icon: the clickable is
                        // outermost, so the touch target is 48x48dp -- Android's recommended minimum.
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .size(24.dp),
                )
            }
        }
    }
}
