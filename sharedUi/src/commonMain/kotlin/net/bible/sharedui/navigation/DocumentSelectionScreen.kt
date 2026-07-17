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
package net.bible.sharedui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbSearchablePicker
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbPullToRefresh
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * Shared, stateless document-selection screen. Used by both ChooseDocument (Plan A,
 * `downloadMode = false`, `onRefresh = null`) and Download (Plan B, `downloadMode = true`,
 * pull-to-refresh enabled). The host owns all state and supplies the callbacks.
 */
@Composable
fun DocumentSelectionScreen(
    title: String,
    downloadMode: Boolean,
    loading: Boolean,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    displayed: List<DocRow>,
    languages: List<LangOption>,
    selectedLanguage: LangOption?,
    typeFilters: List<Pair<DocTypeFilter, String>>,
    selectedTypeFilter: DocTypeFilter,
    query: String,
    resultCount: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    error: ChooserError?,
    topBarActions: @Composable RowScope.() -> Unit,
    onQueryChange: (String) -> Unit,
    onLanguageChange: (LangOption?) -> Unit,
    onTypeFilterChange: (DocTypeFilter) -> Unit,
    onRowClick: (DocRow) -> Unit,
    onRowLongClick: (DocRow) -> Unit,
    onDownload: (DocRow) -> Unit,
    onCancel: (DocRow) -> Unit,
    onSelectionAbout: () -> Unit,
    onSelectionDelete: () -> Unit,
    onSelectionDeleteIndex: () -> Unit,
    onSelectionUnlock: () -> Unit,
    unlockVisible: Boolean,
    deleteVisible: Boolean,
    onDismissError: () -> Unit,
    onNavigateUp: () -> Unit,
    onExitSelection: () -> Unit,
    initiallyFiltersExpanded: Boolean = false,
) {
    val strings = LocalStrings.current
    // Language dropdown: null = all languages, then the concrete options.
    val languageOptions: List<LangOption?> = listOf<LangOption?>(null) + languages
    // Type dropdown: resolve the currently-selected (filter, label) pair.
    val selectedTypePair = typeFilters.firstOrNull { it.first == selectedTypeFilter }
        ?: (selectedTypeFilter to selectedTypeFilter.name)
    // Collapsed-by-default (F13): compact summary of the current language + type filter, composed
    // from existing data (no new strings) with " · ". Tap the row to reveal the two controls inline.
    var filtersExpanded by remember { mutableStateOf(initiallyFiltersExpanded) }
    val filtersSummary = "${selectedLanguage?.displayName ?: strings.all} · ${selectedTypePair.second}"

    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        onNavigateUp = onNavigateUp,
        // Tapping the selection-CAB Close (X) exits selection mode only; the host wires this to
        // controller::clearSelection. onNavigateUp still leaves the whole screen (finish()).
        onExitSelection = onExitSelection,
        actions = topBarActions,
        selectionActions = {
            IconButton(onClick = onSelectionAbout) {
                Icon(Icons.Filled.Info, contentDescription = null)
            }
            if (deleteVisible) {
                IconButton(onClick = onSelectionDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
            IconButton(onClick = onSelectionDeleteIndex) {
                Icon(Icons.Filled.SearchOff, contentDescription = null)
            }
            if (unlockVisible) {
                IconButton(onClick = onSelectionUnlock) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Collapsible filter bar (F13): the search field stays always visible (primary action);
            // the language + type controls collapse behind a compact tappable summary row so they
            // don't always take space. Language uses AbSearchablePicker — a type-to-filter,
            // virtualized bottom-sheet picker (fixes F3 typeability + F6 slow-open). Type keeps
            // AbDropdownField (short enum). resultCount stays on its own line below.
            //
            // Inline expand (not AbSettingsSummarySheet) is used deliberately: AbSearchablePicker
            // opens its OWN ModalBottomSheet, so hosting it inside the summary sheet's ModalBottomSheet
            // would nest bottom sheets. Expanding the controls in place keeps the picker's sheet the
            // only bottom sheet on screen.
            AbSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = strings.searchHint,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filtersExpanded = !filtersExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    filtersSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (filtersExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AbSearchablePicker(
                        label = strings.languageLabel,
                        selected = selectedLanguage,
                        options = languageOptions,
                        optionLabel = { it?.displayName ?: strings.all },
                        onSelect = onLanguageChange,
                        searchPlaceholder = strings.search,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AbDropdownField(
                        // No dedicated "document type" label string exists yet and the brief forbids new
                        // strings; the selected type's own label already conveys the field's purpose.
                        label = "",
                        selected = selectedTypePair,
                        options = typeFilters,
                        optionLabel = { it.second },
                        onSelect = { onTypeFilterChange(it.first) },
                        modifier = Modifier.fillMaxWidth(),
                        horizontalPadding = 0.dp,
                    )
                }
            }
            Text(
                text = resultCount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (loading) {
                AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            if (onRefresh != null) {
                AbPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                    DocumentList(displayed, downloadMode, selectionMode, selectedIds,
                        onRowClick, onRowLongClick, onDownload, onCancel)
                }
            } else {
                DocumentList(displayed, downloadMode, selectionMode, selectedIds,
                    onRowClick, onRowLongClick, onDownload, onCancel)
            }
        }
    }

    if (error != null) {
        AbErrorDialog(message = strings.errorOccurred, confirmText = strings.okay, onDismiss = onDismissError)
    }
}

@Composable
private fun DocumentList(
    displayed: List<DocRow>,
    downloadMode: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onRowClick: (DocRow) -> Unit,
    onRowLongClick: (DocRow) -> Unit,
    onDownload: (DocRow) -> Unit,
    onCancel: (DocRow) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(displayed, key = { it.docId }) { row ->
            DocumentRow(
                row = row,
                downloadMode = downloadMode,
                selectionMode = selectionMode,
                selected = row.docId in selectedIds,
                onClick = { onRowClick(row) },
                onLongClick = { onRowLongClick(row) },
                onDownload = { onDownload(row) },
                onCancel = { onCancel(row) },
            )
        }
    }
}
