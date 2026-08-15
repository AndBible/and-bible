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
package net.bible.sharedui.cloud

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.cloud.CloudDocAction
import net.bible.sharedcore.cloud.CloudDocActionLabel
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocStatus
import net.bible.sharedcore.cloud.SyncNowDialogState
import net.bible.sharedcore.cloud.actionLabelKind
import net.bible.sharedcore.cloud.bulkMenuActions
import net.bible.sharedcore.cloud.cloudDocStatus
import net.bible.sharedcore.cloud.documentMenuActions
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbPullToRefresh
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.LocalDisplayColorMode

@Composable
fun CloudDocumentsScreen(
    title: String,
    loading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    displayed: List<CloudDocItem>,
    statusFilters: List<Pair<CloudDocFilter, String>>,
    selectedStatusFilter: CloudDocFilter,
    categoryFilters: List<Pair<DocCategory?, String>>,
    selectedCategoryFilter: DocCategory?,
    query: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    syncEnabled: Boolean,
    syncNowDialog: SyncNowDialogState?,
    topBarActions: @Composable RowScope.() -> Unit,
    onQueryChange: (String) -> Unit,
    searchModeActive: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onStatusFilterChange: (CloudDocFilter) -> Unit,
    onCategoryFilterChange: (DocCategory?) -> Unit,
    onRowClick: (CloudDocItem) -> Unit,
    onRowLongClick: (CloudDocItem) -> Unit,
    onRowAction: (CloudDocItem, CloudDocAction) -> Unit,
    onBulkAction: (CloudDocAction) -> Unit,
    onSyncNowConfirm: (List<Boolean>) -> Unit,
    onSyncNowDismiss: () -> Unit,
    onNavigateUp: () -> Unit,
    onExitSelection: () -> Unit,
) {
    val strings = LocalStrings.current
    val selectedStatusPair = statusFilters.firstOrNull { it.first == selectedStatusFilter }
        ?: (selectedStatusFilter to selectedStatusFilter.name)
    val selectedCategoryPair = categoryFilters.firstOrNull { it.first == selectedCategoryFilter }
        ?: (selectedCategoryFilter to "")
    val bulkActions = if (selectionMode) bulkMenuActions(displayed.filter { it.initials in selectedIds }, syncEnabled) else emptyList()

    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selectedIds.size,
        onNavigateUp = onNavigateUp,
        onExitSelection = onExitSelection,
        actions = {
            AbActionIcon(Icons.Filled.Search, strings.search, onOpenSearch)
            topBarActions()
        },
        selectionActions = {
            bulkActions.forEach { action ->
                IconButton(onClick = { onBulkAction(action) }) {
                    Icon(bulkIcon(action), contentDescription = null)
                }
            }
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
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AbDropdownField(
                label = "",
                selected = selectedStatusPair,
                options = statusFilters,
                optionLabel = { it.second },
                onSelect = { onStatusFilterChange(it.first) },
            )
            AbDropdownField(
                label = "",
                selected = selectedCategoryPair,
                options = categoryFilters,
                optionLabel = { it.second },
                onSelect = { onCategoryFilterChange(it.first) },
            )
            if (loading) {
                AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            AbPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                if (displayed.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.emptyList, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayed, key = { it.initials }) { item ->
                            CloudDocRow(
                                item = item, selectionMode = selectionMode,
                                selected = item.initials in selectedIds, syncEnabled = syncEnabled,
                                onClick = { onRowClick(item) }, onLongClick = { onRowLongClick(item) },
                                onAction = { onRowAction(item, it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (syncNowDialog != null) {
        val checked = remember(syncNowDialog) { mutableStateListOf(*syncNowDialog.checked.toTypedArray()) }
        AlertDialog(
            onDismissRequest = onSyncNowDismiss,
            confirmButton = { TextButton(onClick = { onSyncNowConfirm(checked.toList()) }) { Text(strings.okay) } },
            dismissButton = { TextButton(onClick = onSyncNowDismiss) { Text(strings.cancel) } },
            text = {
                Column {
                    syncNowDialog.labels.forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickableRow { checked[i] = !checked[i] }.padding(vertical = 4.dp)) {
                            Checkbox(checked = checked.getOrElse(i) { false }, onCheckedChange = { checked[i] = it })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

private fun bulkIcon(action: CloudDocAction): ImageVector = when (action) {
    CloudDocAction.DOWNLOAD -> Icons.Filled.Download
    CloudDocAction.PUSH, CloudDocAction.RESTORE -> Icons.Filled.Upload
    CloudDocAction.REMOVE_CLOUD -> Icons.Filled.Delete
    CloudDocAction.BLOCK -> Icons.Filled.Block
    CloudDocAction.UNBLOCK -> Icons.Filled.Sync
    CloudDocAction.PURGE -> Icons.Filled.CloudOff
}

private fun statusLabel(status: CloudDocStatus, strings: Strings): String = when (status) {
    CloudDocStatus.SYNCED -> strings.cloudStatusSynced
    CloudDocStatus.LOCAL_ONLY -> strings.cloudStatusLocalOnly
    CloudDocStatus.CLOUD_ONLY -> strings.cloudStatusCloudOnly
    CloudDocStatus.UPDATE -> strings.cloudStatusUpdate
    CloudDocStatus.BLOCKED -> strings.cloudStatusBlocked
    CloudDocStatus.WONT_SYNC -> strings.cloudStatusWontSync
    CloudDocStatus.REMOVED -> strings.cloudStatusRemoved
    CloudDocStatus.REMOVED_STILL_INSTALLED -> "${strings.cloudStatusRemoved} · ${strings.cloudStatusStillInstalled}"
}

private fun actionLabel(label: CloudDocActionLabel, strings: Strings): String = when (label) {
    CloudDocActionLabel.DOWNLOAD -> strings.cloudActionDownload
    CloudDocActionLabel.PUSH -> strings.cloudActionPush
    CloudDocActionLabel.REMOVE_CLOUD -> strings.cloudActionRemoveCloud
    CloudDocActionLabel.REMOVE_ALL_DEVICES -> strings.cloudActionRemoveAllDevices
    CloudDocActionLabel.BLOCK -> strings.cloudActionBlock
    CloudDocActionLabel.DONT_SYNC -> strings.cloudActionDontSync
    CloudDocActionLabel.UNBLOCK -> strings.cloudActionUnblock
    CloudDocActionLabel.ALLOW_SYNC -> strings.cloudActionAllowSync
    CloudDocActionLabel.RESTORE -> strings.cloudActionRestore
    CloudDocActionLabel.PURGE -> strings.cloudActionPurge
}

private fun statusIcon(status: CloudDocStatus): ImageVector = when (status) {
    CloudDocStatus.SYNCED -> Icons.Filled.Check
    CloudDocStatus.UPDATE -> Icons.Filled.ArrowUpward
    CloudDocStatus.CLOUD_ONLY -> Icons.Filled.Download
    CloudDocStatus.LOCAL_ONLY -> Icons.Filled.Upload
    CloudDocStatus.BLOCKED, CloudDocStatus.WONT_SYNC -> Icons.Filled.Block
    CloudDocStatus.REMOVED, CloudDocStatus.REMOVED_STILL_INSTALLED -> Icons.Filled.CloudOff
}

// Classic status hues (ARGB), grayed on e-ink via accentArgbFor + LocalDisplayColorMode so status
// never rides on color alone (the icon + subtitle text always convey it).
private fun statusBaseArgb(status: CloudDocStatus): Int = when (status) {
    CloudDocStatus.SYNCED -> 0xFF4CAF50.toInt()          // green
    CloudDocStatus.UPDATE -> 0xFF2196F3.toInt()          // blue
    CloudDocStatus.CLOUD_ONLY -> 0xFF2196F3.toInt()      // blue
    CloudDocStatus.LOCAL_ONLY -> 0xFFFF9800.toInt()      // amber
    CloudDocStatus.BLOCKED, CloudDocStatus.WONT_SYNC -> 0xFFF44336.toInt()  // red
    CloudDocStatus.REMOVED, CloudDocStatus.REMOVED_STILL_INSTALLED -> 0xFF9E9E9E.toInt()  // gray
}

@Composable
private fun statusColor(status: CloudDocStatus): Color =
    Color(accentArgbFor(statusBaseArgb(status), LocalDisplayColorMode.current))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudDocRow(
    item: CloudDocItem,
    selectionMode: Boolean,
    selected: Boolean,
    syncEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (CloudDocAction) -> Unit,
) {
    val strings = LocalStrings.current
    val status = cloudDocStatus(item)
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = null)
        else Icon(statusIcon(status), contentDescription = null, tint = statusColor(status)) // icon + text: e-ink safe
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle(item, status, strings), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!selectionMode) {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                documentMenuActions(item, syncEnabled).forEach { action ->
                    DropdownMenuItem(
                        text = { Text(actionLabel(actionLabelKind(action, item.localOnly, syncEnabled), strings)) },
                        onClick = { expanded = false; onAction(action) },
                    )
                }
            }
        }
    }
}

private fun subtitle(item: CloudDocItem, status: CloudDocStatus, strings: Strings): String {
    val version = item.localVersion ?: item.cloudVersion
    return buildList {
        if (version != null) add(strings.cloudVersionPrefix(version))
        item.sizeLabel?.let { add(it) }
        add(statusLabel(status, strings))
    }.joinToString(" · ")
}
