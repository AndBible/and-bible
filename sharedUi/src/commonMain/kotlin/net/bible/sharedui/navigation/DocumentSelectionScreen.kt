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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbPullToRefresh
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
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
    searchModeActive: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
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
) {
    val strings = LocalStrings.current

    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        onNavigateUp = onNavigateUp,
        // Tapping the selection-CAB Close (X) exits selection mode only; the host wires this to
        // controller::clearSelection. onNavigateUp still leaves the whole screen (finish()).
        onExitSelection = onExitSelection,
        actions = {
            AbActionIcon(Icons.Filled.Search, strings.search, onOpenSearch)
            topBarActions()
        },
        search = if (searchModeActive) {
            AbTopBarSearchState(query = query, imeRequest = AbSearchImeRequest.Focus)
        } else null,
        searchCallbacks = if (searchModeActive) {
            AbTopBarSearchCallbacks(
                onQueryChange = onQueryChange,
                onClose = onCloseSearch,
                onImeRequestHandled = {},
            )
        } else null,
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
            // Search lives in the top bar (round 7). DocumentFilterBar carries the language and type
            // filters as chips that show their current value and open one bottom sheet at a time —
            // which is why the controls are not hosted in a summary sheet: nesting bottom sheets is
            // not an option, and AbSearchableOptionSheet is itself a ModalBottomSheet.
            DocumentFilterBar(
                languages = languages,
                selectedLanguage = selectedLanguage,
                onLanguageChange = onLanguageChange,
                typeFilters = typeFilters,
                selectedTypeFilter = selectedTypeFilter,
                onTypeFilterChange = onTypeFilterChange,
                resultCount = resultCount,
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
