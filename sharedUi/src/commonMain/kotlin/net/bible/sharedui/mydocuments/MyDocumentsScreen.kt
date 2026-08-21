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
package net.bible.sharedui.mydocuments

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbCreateItemSheet
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbReorderableColumn
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless MyDocuments list editor. All persistence is hoisted (the host wires the
 * [net.bible.sharedcore.mydocuments.MyDocumentsController] to these callbacks); only which dialog is
 * open and its text buffer are local UI state.
 */
@Composable
fun MyDocumentsScreen(
    title: String,
    documents: List<MyDocItem>,
    dirty: Boolean,
    query: String,
    filtering: Boolean,
    searchModeActive: Boolean,
    totalCount: Int,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onOpen: (id: Long) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onEditDescription: (id: Long, description: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onExport: (id: Long) -> Unit,
    onCreate: (name: String) -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNavigateUp: () -> Unit,
    // Import name-entry dialog, driven by hoisted host state (the host owns SAF and the picked URIs):
    // non-null [importNamePrompt] ⇒ show the dialog pre-filled with it; null ⇒ hidden. [onConfirmImport]
    // receives the chosen name; [onDismissImport] clears the host's pending state on cancel.
    importNamePrompt: String? = null,
    onConfirmImport: (String) -> Unit = {},
    onDismissImport: () -> Unit = {},
    // Selection mode (long-press to enter), driven by hoisted host state.
    selection: Set<Long>,
    onToggleSelected: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
) {
    val s = LocalStrings.current
    // Local dialog state (which dialog + target item + text buffer). Not hoisted (pure UI).
    var createOpen by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<MyDocItem?>(null) }
    var descFor by remember { mutableStateOf<MyDocItem?>(null) }
    var deleteFor by remember { mutableStateOf<MyDocItem?>(null) }
    var blockedAiDelete by remember { mutableStateOf(false) }
    val selectionMode = selection.isNotEmpty()
    var confirmBatchDelete by remember { mutableStateOf(false) }

    AbSelectionScaffold(
        title = title,
        selectionMode = selectionMode,
        selectedCount = selection.size,
        onNavigateUp = onNavigateUp,
        onExitSelection = onClearSelection,
        actions = {
            if (!searchModeActive) {
                AbActionIcon(Icons.Filled.Search, s.search, onOpenSearch)
                AbActionIcon(Icons.Filled.AddCircleOutline, s.newItem) { createOpen = true }
            }
        },
        selectionActions = {
            AbActionIcon(Icons.Filled.FileUpload, s.export, onExportSelected)
            AbActionIcon(Icons.Filled.Delete, s.deleteLabel) { confirmBatchDelete = true }
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
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (documents.isEmpty()) {
                    Text(
                        s.myDocumentsEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    AbReorderableColumn(items = documents, key = { it.id }, onMove = onMove) { item, handle ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) onToggleSelected(item.id) else onOpen(item.id)
                                    },
                                    onLongClick = { onToggleSelected(item.id) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when {
                                selectionMode -> Checkbox(
                                    checked = item.id in selection,
                                    onCheckedChange = { onToggleSelected(item.id) },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                !filtering -> Icon(
                                    Icons.Filled.DragHandle, contentDescription = null,
                                    modifier = handle.padding(horizontal = 12.dp),
                                )
                                else -> Spacer(Modifier.size(48.dp))
                            }
                            if (item.isAiGenerated) {
                                Icon(
                                    Icons.Filled.AutoAwesome, contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    item.description.ifEmpty { s.noDescription },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!selectionMode) {
                                RowOverflow(
                                    onRename = { renameFor = item },
                                    onEditDescription = { descFor = item },
                                    onExport = { onExport(item.id) },
                                    onDelete = { if (item.canDelete) deleteFor = item else blockedAiDelete = true },
                                )
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(s.dismiss) }
                TextButton(onClick = onSave, enabled = dirty, modifier = Modifier.weight(1f)) { Text(s.saveAndExit) }
            }
        }
    }

    if (createOpen) {
        AbCreateItemSheet(
            title = s.createTitle,
            initialName = s.newDocumentName(totalCount + 1),
            confirmText = s.okay,
            importText = s.importDocument,
            onCreate = { createOpen = false; if (it.isNotBlank()) onCreate(it.trim()) },
            onImport = { createOpen = false; onImport() },
            onDismiss = { createOpen = false },
        )
    }
    // Import name-entry prompt (parity with classic MyDocumentsActivity.showImportNameDialog): the host
    // has already picked URIs via SAF and set the pre-fill string; confirm hands the name back to the host.
    importNamePrompt?.let { prompt ->
        AbTextInputDialog(
            title = s.createTitle, initial = prompt,
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { if (it.isNotBlank()) onConfirmImport(it.trim()) else onDismissImport() },
            onDismiss = onDismissImport,
        )
    }
    renameFor?.let { item ->
        AbTextInputDialog(
            title = s.renameTitle, initial = item.name, confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { renameFor = null; if (it.isNotBlank()) onRename(item.id, it.trim()) },
            onDismiss = { renameFor = null },
        )
    }
    descFor?.let { item ->
        AbTextInputDialog(
            title = s.editDescriptionLabel, initial = item.description, confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { descFor = null; onEditDescription(item.id, it.trim()) },
            onDismiss = { descFor = null },
        )
    }
    deleteFor?.let { item ->
        AbConfirmDialog(
            title = null, message = s.deleteDocumentConfirmation(item.name),
            confirmText = s.yes, dismissText = s.no,
            onConfirm = { deleteFor = null; onDelete(item.id) }, onDismiss = { deleteFor = null },
        )
    }
    if (blockedAiDelete) {
        AbErrorDialog(message = s.cannotDeleteAiDocuments, confirmText = s.okay, onDismiss = { blockedAiDelete = false })
    }
    if (confirmBatchDelete) {
        AbConfirmDialog(
            title = null, message = s.deleteDocumentsConfirmation,
            confirmText = s.yes, dismissText = s.no,
            onConfirm = {
                confirmBatchDelete = false
                // Count the blocked rows BEFORE deleting. Safe to read from `documents`: a selection
                // can only exist over the published rows, because changing the query clears it.
                val blocked = documents.count { it.id in selection && !it.canDelete }
                onDeleteSelected()
                if (blocked > 0) blockedAiDelete = true
            },
            onDismiss = { confirmBatchDelete = false },
        )
    }
}

@Composable
private fun RowOverflow(
    onRename: () -> Unit, onEditDescription: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AbMenuItem(
                text = s.rename,
                onClick = { expanded = false; onRename() },
                icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
            )
            AbMenuItem(
                text = s.editDescriptionLabel,
                onClick = { expanded = false; onEditDescription() },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            )
            AbMenuItem(
                text = s.exportDocumentLabel,
                onClick = { expanded = false; onExport() },
                icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
            )
            AbMenuItem(
                text = s.deleteLabel,
                onClick = { expanded = false; onDelete() },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            )
        }
    }
}
