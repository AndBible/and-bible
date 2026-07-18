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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.components.AbSelectionScaffold
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
 * **Top bar (non-selection).** A quick-access sort [AbActionIcon] reflects the current [sortMode]
 * (up/down arrow for ascending/descending; classic only toasted the new order on tap, this port
 * additionally encodes direction in the icon) plus an [AbOverflowMenu] with Manage Labels / Sort
 * (a text-labelled duplicate of the quick-access icon, for discoverability) / Show notes
 * (checkable) / Export CSV / Import CSV — matching `bookmark_actionbar_menu.xml`'s five items
 * (`manageLabels`, `sortByToggle`, `showNotes`, `exportCsv`, `importCsv`).
 *
 * **Filter + search.** The label filter is an always-visible [AbDropdownField] (classic's label
 * spinner). The notes-search field ([AbSearchField]) only shows when [showNotes] (classic hid
 * `textSearchLayout` unless "Show notes" was on, since search only ever matched note text).
 *
 * **Row.** A row of small tinted label-colour chips (classic `ic_label_24dp` circles, one per
 * non-speak label per [BookmarkRow.labelColors]'s contract), a speak icon when [BookmarkRow.isSpeak],
 * the title + date on one line, [BookmarkRow.content] rendered via [styledTextToAnnotatedString]
 * (bold selection highlight), and [BookmarkRow.notes] the same way when [showNotes] and non-null.
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
    loading: Boolean,
    onSelectFilter: (Int) -> Unit,
    onCycleSort: () -> Unit,
    onSearch: (String) -> Unit,
    onToggleShowNotes: () -> Unit,
    onRowClick: (id: String, index: Int) -> Unit,
    onRowLongClick: (id: String) -> Unit,
    onToggleSelected: (id: String) -> Unit,
    onAssignSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onManageLabels: () -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onUp: () -> Unit,
) {
    val strings = LocalStrings.current
    val selectionMode = selection.isNotEmpty()
    val selectedFilter = filterLabels.firstOrNull { it.index == selectedFilterIndex }
        ?: filterLabels.firstOrNull()
        ?: BookmarkFilterLabel(selectedFilterIndex, "")

    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selection.size,
        onNavigateUp = onUp,
        onExitSelection = onClearSelection,
        actions = {
            AbActionIcon(
                icon = sortIcon(sortMode),
                contentDescription = sortModeLabel(sortMode, strings),
                onClick = onCycleSort,
            )
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(
                    text = { Text(strings.manageLabelsLabel) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    onClick = { close(); onManageLabels() },
                )
                DropdownMenuItem(
                    text = { Text(sortModeLabel(sortMode, strings)) },
                    leadingIcon = { Icon(sortIcon(sortMode), contentDescription = null) },
                    onClick = { close(); onCycleSort() },
                )
                DropdownMenuItem(
                    text = { Text(strings.showNotesLabel) },
                    leadingIcon = {
                        Icon(
                            if (showNotes) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                        )
                    },
                    onClick = { close(); onToggleShowNotes() },
                )
                DropdownMenuItem(
                    text = { Text(strings.exportSomething("CSV")) },
                    onClick = { close(); onExportCsv() },
                )
                DropdownMenuItem(
                    text = { Text(strings.importItems("CSV")) },
                    onClick = { close(); onImportCsv() },
                )
            }
        },
        selectionActions = {
            AbActionIcon(Icons.AutoMirrored.Filled.Label, contentDescription = strings.assignLabelsLabel, onClick = onAssignSelected)
            AbActionIcon(Icons.Filled.Delete, contentDescription = strings.deleteLabel, onClick = onDeleteSelected)
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AbDropdownField(
                label = "",
                selected = selectedFilter,
                options = filterLabels,
                optionLabel = { it.displayName },
                onSelect = { onSelectFilter(it.index) },
            )
            if (showNotes) {
                AbSearchField(
                    value = searchText,
                    onValueChange = onSearch,
                    placeholder = strings.bookmarksSearchNotesHint,
                )
            }
            if (loading) {
                AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            if (rows.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.emptyList, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                        BookmarkListRow(
                            row = row,
                            showNotes = showNotes,
                            selectionMode = selectionMode,
                            selected = row.id in selection,
                            onClick = { if (selectionMode) onToggleSelected(row.id) else onRowClick(row.id, index) },
                            onLongClick = { onRowLongClick(row.id) },
                        )
                    }
                }
            }
        }
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

/**
 * One bookmark row: label-colour chips + speak icon + title/date line, then the highlighted
 * content, then notes (when shown). Mirrors `bookmark_list_item.xml`'s layout order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkListRow(
    row: BookmarkRow,
    showNotes: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val strings = LocalStrings.current
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
        )
        val notes = row.notes
        if (showNotes && notes != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = styledTextToAnnotatedString(notes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
