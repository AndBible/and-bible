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
package net.bible.sharedui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.DocGroup
import net.bible.sharedcore.navigation.DocGroupKey

/**
 * The chrome every document list shares. Extracted in round 17e-2 from `DocumentSelectionScreen`,
 * whose behaviour this reproduces exactly.
 *
 * Two details that are easy to lose in an extraction and are load-bearing:
 *
 * - `onRefresh == null` must render the list WITHOUT an `AbPullToRefresh` wrapper at all, not with
 *   a disabled one: the document picker has no refresh and wrapping it would add a gesture that
 *   does nothing.
 * - A `DocGroupKey.None` group gets NO sticky header. It is the ungrouped case, and a single
 *   header over the whole list would be chrome that says nothing.
 *
 * The sticky header is painted opaque on purpose — it scrolls OVER the rows beneath it, so a
 * transparent header renders the list text through its own label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> AbDocumentListScaffold(
    title: String,
    selectionMode: Boolean,
    selectedCount: Int,
    onNavigateUp: () -> Unit,
    onExitSelection: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    selectionActions: @Composable RowScope.() -> Unit,
    search: AbTopBarSearchState?,
    searchCallbacks: AbTopBarSearchCallbacks?,
    filterBar: @Composable () -> Unit,
    loading: Boolean,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    groups: List<DocGroup<T>>,
    groupHeaderLabel: (DocGroupKey) -> String,
    itemKey: (T) -> Any,
    emptyText: String?,
    row: @Composable (T) -> Unit,
) {
    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selectedCount,
        onNavigateUp = onNavigateUp,
        onExitSelection = onExitSelection,
        actions = actions,
        search = search,
        searchCallbacks = searchCallbacks,
        selectionActions = selectionActions,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            filterBar()
            if (loading) {
                AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            val list: @Composable () -> Unit = {
                if (emptyText != null && groups.all { it.rows.isEmpty() }) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emptyText, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        groups.forEach { group ->
                            // DocGroupKey.None is the ungrouped case and gets NO header — a single
                            // header reading "No grouping" over the whole list would be chrome that
                            // says nothing.
                            if (group.key != DocGroupKey.None) {
                                stickyHeader(key = "header-${group.key}") {
                                    Text(
                                        text = groupHeaderLabel(group.key),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // Opaque: a sticky header scrolls OVER the rows beneath
                                            // it, so a transparent one renders the list text through
                                            // the label.
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            items(group.rows, key = itemKey) { item ->
                                row(item)
                            }
                        }
                    }
                }
            }
            if (onRefresh != null) {
                AbPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) { list() }
            } else {
                list()
            }
        }
    }
}
