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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.cloud.CloudDocAction
import net.bible.sharedcore.cloud.CloudDocActionLabel
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocStatus
import net.bible.sharedcore.cloud.CloudDocumentsDialog
import net.bible.sharedcore.cloud.actionLabelKind
import net.bible.sharedcore.cloud.bulkMenuActions
import net.bible.sharedcore.cloud.cloudDocStatus
import net.bible.sharedcore.cloud.documentMenuActions
import net.bible.sharedcore.navigation.DocArrangement
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocGroup
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocGroupKey
import net.bible.sharedcore.navigation.DocSortKey
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbDocumentListRow
import net.bible.sharedui.components.AbDocumentListScaffold
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectSheet
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.LocalAbColors
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.isPureMonochrome

@Composable
fun CloudDocumentsScreen(
    title: String,
    loading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    grouped: List<DocGroup<CloudDocItem>>,
    statusFilters: List<Pair<CloudDocFilter, String>>,
    selectedStatusFilter: CloudDocFilter,
    categoryFilters: List<Pair<DocCategory?, String>>,
    selectedCategoryFilter: DocCategory?,
    query: String,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    syncEnabled: Boolean,
    dialog: CloudDocumentsDialog,
    topBarActions: @Composable RowScope.() -> Unit,
    onQueryChange: (String) -> Unit,
    searchModeActive: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onStatusFilterChange: (CloudDocFilter) -> Unit,
    onCategoryFilterChange: (DocCategory?) -> Unit,
    arrangement: DocArrangement,
    groupKeys: List<DocGroupBy>,
    rememberArrangement: Boolean,
    arrangementIsDefault: Boolean,
    onMoveSort: (from: Int, to: Int) -> Unit,
    onToggleSortDirection: (DocSortKey) -> Unit,
    onGroupByChange: (DocGroupBy) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onResetArrangement: () -> Unit,
    showRemoved: Boolean,
    onShowRemovedChange: (Boolean) -> Unit,
    onRowClick: (CloudDocItem) -> Unit,
    onRowLongClick: (CloudDocItem) -> Unit,
    onRowAction: (CloudDocItem, CloudDocAction) -> Unit,
    onBulkAction: (CloudDocAction) -> Unit,
    onSyncNowConfirm: (List<Boolean>) -> Unit,
    onSyncNowDismiss: () -> Unit,
    onConfirmDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onNavigateUp: () -> Unit,
    onExitSelection: () -> Unit,
) {
    val strings = LocalStrings.current
    val allRows = grouped.flatMap { it.rows }
    val bulkActions = if (selectionMode) {
        bulkMenuActions(allRows.filter { it.initials in selectedIds }, syncEnabled)
    } else emptyList()
    val resultCount = strings.docFilterResults(allRows.size)

    AbDocumentListScaffold(
        title = title,
        selectionMode = selectionMode, selectedCount = selectedIds.size,
        onNavigateUp = onNavigateUp, onExitSelection = onExitSelection,
        actions = { AbActionIcon(Icons.Filled.Search, strings.search, onOpenSearch); topBarActions() },
        selectionActions = {
            bulkActions.forEach { action ->
                IconButton(onClick = { onBulkAction(action) }) { Icon(bulkIcon(action), contentDescription = bulkActionLabel(action, strings)) }
            }
        },
        search = if (searchModeActive) AbTopBarSearchState(query = query, imeRequest = AbSearchImeRequest.Focus) else null,
        searchCallbacks = if (searchModeActive) AbTopBarSearchCallbacks(
            onQueryChange = onQueryChange, onClose = onCloseSearch, onImeRequestHandled = {},
        ) else null,
        filterBar = {
            CloudDocFilterBar(
                statusFilters = statusFilters,
                selectedStatusFilter = selectedStatusFilter,
                onStatusFilterChange = onStatusFilterChange,
                categoryFilters = categoryFilters,
                selectedCategoryFilter = selectedCategoryFilter,
                onCategoryFilterChange = onCategoryFilterChange,
                resultCount = resultCount,
                arrangement = arrangement,
                groupKeys = groupKeys,
                rememberArrangement = rememberArrangement,
                arrangementIsDefault = arrangementIsDefault,
                onMoveSort = onMoveSort,
                onToggleSortDirection = onToggleSortDirection,
                onGroupByChange = onGroupByChange,
                onRememberChange = onRememberChange,
                onResetArrangement = onResetArrangement,
                showRemoved = showRemoved,
                onShowRemovedChange = onShowRemovedChange,
            )
        },
        loading = loading, isRefreshing = isRefreshing, onRefresh = onRefresh,
        groups = grouped,
        groupHeaderLabel = { cloudGroupHeaderLabel(it, strings) },
        itemKey = { it.initials },
        emptyText = strings.emptyList,
    ) { item ->
        CloudDocRow(
            item = item, selectionMode = selectionMode,
            selected = item.initials in selectedIds, syncEnabled = syncEnabled,
            onClick = { onRowClick(item) }, onLongClick = { onRowLongClick(item) },
            onAction = { onRowAction(item, it) },
        )
    }

    when (dialog) {
        is CloudDocumentsDialog.SyncNow -> {
            val syncNowDialog = dialog.state
            // The host's contract is POSITIONAL — `onSyncNowConfirm(List<Boolean>)` feeds
            // `CloudDocumentsController.confirmSyncNow`, which reads index 0/1/2 as download/upload/
            // delete — while `AbMultiSelectSheet` speaks ids. The index IS the id here, so the two map
            // onto each other exactly and the controller (and its test) stay untouched.
            val rows = remember(syncNowDialog) {
                syncNowDialog.labels.mapIndexed { i, label -> i.toString() to label }
            }
            val preChecked = remember(syncNowDialog) {
                syncNowDialog.checked.mapIndexedNotNull { i, on -> if (on) i.toString() else null }
            }
            AbMultiSelectSheet(
                open = true,
                title = strings.cloudDocSyncNow,
                options = rows,
                selectedIds = preChecked,
                idOf = { it.first },
                labelOf = { it.second },
                confirmText = strings.okay,
                dismissText = strings.cancel,
                onConfirm = { ids -> onSyncNowConfirm(rows.indices.map { it.toString() in ids }) },
                onDismiss = onSyncNowDismiss,
            )
        }
        // The remove/purge question (NH rows 8574/8586); classic's own title/Okay/Cancel wording.
        // The title is one of two ALREADY-EXISTING strings (also used as the per-row action label),
        // picked here in the SCREEN from the bare `allDevices` flag the controller carries.
        is CloudDocumentsDialog.ConfirmRemove -> AbConfirmDialog(
            title = if (dialog.allDevices) strings.cloudActionRemoveAllDevices else strings.cloudActionRemoveCloud,
            message = dialog.message,
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = onConfirmDialog,
            onDismiss = onDismissDialog,
        )
        is CloudDocumentsDialog.ConfirmPurge -> AbConfirmDialog(
            title = strings.cloudActionPurge,
            message = dialog.message,
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = onConfirmDialog,
            onDismiss = onDismissDialog,
        )
        is CloudDocumentsDialog.ConfirmBlock -> AbConfirmDialog(
            title = strings.cloudActionBlock,
            message = dialog.message,
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = onConfirmDialog,
            onDismiss = onDismissDialog,
        )
        CloudDocumentsDialog.None -> {}
    }
}

/**
 * Header label for a cloud-list group, reusing the SAME words the screen's own filters/status show
 * — a group called "Update available" must not be a third spelling of a state the status chip and
 * the row subtitle already name. [DocCategory.OTHER]/null is NOT the same as "no value" — it's a
 * real, meaningful category (documents that aren't Bible/commentary/dictionary/general
 * book/maps/add-on), so it gets its own [Strings.docTypeOther] label rather than the `all`
 * placeholder, matching [net.bible.sharedui.navigation.documentGroupHeaderLabel]'s sibling case.
 */
private fun cloudGroupHeaderLabel(key: DocGroupKey, strings: Strings): String = when (key) {
    is DocGroupKey.Status -> statusLabel(CloudDocStatus.entries[key.rank], strings)
    is DocGroupKey.Category -> when (key.category) {
        DocCategory.BIBLE -> strings.docTypeBible
        DocCategory.COMMENTARY -> strings.docTypeCommentary
        DocCategory.DICTIONARY -> strings.docTypeDictionary
        DocCategory.GENERAL_BOOK -> strings.docTypeGeneralBook
        DocCategory.MAPS -> strings.docTypeMaps
        DocCategory.AND_BIBLE -> strings.docTypeAddon
        DocCategory.OTHER, null -> strings.docTypeOther
    }
    // The cloud screen offers neither, so these are unreachable — but the `when` must stay
    // exhaustive so a future group key fails the build rather than rendering an empty header.
    is DocGroupKey.Language, is DocGroupKey.Repository, DocGroupKey.None -> ""
}

private fun bulkActionLabel(action: CloudDocAction, strings: Strings): String = when (action) {
    CloudDocAction.DOWNLOAD -> strings.cloudActionDownload
    CloudDocAction.PUSH -> strings.cloudActionPush
    CloudDocAction.REMOVE_CLOUD -> strings.cloudActionRemoveCloud
    CloudDocAction.BLOCK -> strings.cloudActionBlock
    CloudDocAction.UNBLOCK -> strings.cloudActionUnblock
    CloudDocAction.RESTORE -> strings.cloudActionRestore
    CloudDocAction.PURGE -> strings.cloudActionPurge
}

private fun bulkIcon(action: CloudDocAction): ImageVector = when (action) {
    CloudDocAction.DOWNLOAD -> Icons.Filled.Download
    CloudDocAction.PUSH, CloudDocAction.RESTORE -> Icons.Filled.Upload
    CloudDocAction.REMOVE_CLOUD -> Icons.Filled.Delete
    CloudDocAction.BLOCK -> Icons.Filled.Block
    CloudDocAction.UNBLOCK -> Icons.Filled.Sync
    CloudDocAction.PURGE -> Icons.Filled.CloudOff
}

/**
 * The row-menu icon for one [CloudDocActionLabel] — the finer-grained, context-sensitive label
 * set (`actionLabelKind()`) shown per row, as opposed to [bulkIcon]'s per-[CloudDocAction] icon for
 * the selection-mode toolbar. Exhaustive with no `else`: a future eleventh label must pick an icon
 * here or the build fails, rather than a new row silently rendering iconless.
 */
private fun cloudActionIcon(kind: CloudDocActionLabel): ImageVector = when (kind) {
    CloudDocActionLabel.DOWNLOAD -> Icons.Filled.CloudDownload          // classic ic_cloud_download_24dp
    CloudDocActionLabel.PUSH -> Icons.Filled.CloudUpload                // classic ic_cloud_upload_24dp
    CloudDocActionLabel.REMOVE_CLOUD -> Icons.Filled.Delete             // classic ic_delete_24dp
    CloudDocActionLabel.REMOVE_ALL_DEVICES -> Icons.Filled.DeleteSweep  // chosen
    CloudDocActionLabel.BLOCK -> Icons.Filled.Block                     // chosen; matches bulkIcon
    CloudDocActionLabel.DONT_SYNC -> Icons.Filled.SyncDisabled          // chosen
    CloudDocActionLabel.UNBLOCK -> Icons.Filled.Sync                    // chosen; matches bulkIcon
    CloudDocActionLabel.ALLOW_SYNC -> Icons.Filled.Sync                 // chosen
    CloudDocActionLabel.RESTORE -> Icons.Filled.Restore                 // chosen
    CloudDocActionLabel.PURGE -> Icons.Filled.CloudOff                  // chosen; matches bulkIcon
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
    if (isPureMonochrome()) {
        when (status) {
            CloudDocStatus.BLOCKED, CloudDocStatus.WONT_SYNC,
            CloudDocStatus.REMOVED, CloudDocStatus.REMOVED_STILL_INSTALLED -> LocalAbColors.current.monoDisabled
            else -> MaterialTheme.colorScheme.onSurface
        }
    } else Color(accentArgbFor(statusBaseArgb(status), LocalDisplayColorMode.current))

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
    AbDocumentListRow(
        title = item.name,
        subtitle = AnnotatedString(subtitle(item, status, strings)),
        onClick = onClick,
        onLongClick = onLongClick,
        leading = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                // Round 17e-2: the DOCUMENT CATEGORY, as in the download list — the two lists now
                // answer "what kind of book is this?" in the same place. The sync status has not
                // been dropped; it moved to the trailing slot below, which is where the download
                // list has always shown a per-row status. `category` is nullable on a cloud
                // listing, and OTHER is the row the category icon seam draws for "no category".
                Icon(
                    painter = LocalCategoryIcon.current(item.category ?: DocCategory.OTHER),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        trailing = {
            // Icon AND the subtitle's status word, never colour alone — the e-ink and BW themes
            // grey the tint away (accentArgbFor), so the status has to survive without it.
            Icon(statusIcon(status), contentDescription = statusLabel(status, strings),
                tint = statusColor(status), modifier = Modifier.size(20.dp))
            if (!selectionMode) {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = strings.menu)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    documentMenuActions(item, syncEnabled).forEach { action ->
                        val kind = actionLabelKind(action, item.localOnly, syncEnabled)
                        AbMenuItem(
                            text = actionLabel(kind, strings),
                            onClick = { expanded = false; onAction(action) },
                            icon = { Icon(cloudActionIcon(kind), contentDescription = null) },
                        )
                    }
                }
            }
        },
    )
}

private fun subtitle(item: CloudDocItem, status: CloudDocStatus, strings: Strings): String {
    val version = item.localVersion ?: item.cloudVersion
    return buildList {
        if (version != null) add(strings.cloudVersionPrefix(version))
        item.sizeLabel?.let { add(it) }
        add(statusLabel(status, strings))
    }.joinToString(" · ")
}
