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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbCreateItemSheet
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbReorderableColumn
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless page editor for one document. Sibling of [MyDocumentsScreen] minus the description; rows
 * carry a name + content-type subtitle, and the create-page sheet carries a content-type dropdown.
 * All persistence is hoisted (the host wires [net.bible.sharedcore.mydocuments.MyDocumentPagesController]
 * to these callbacks); only which dialog is open and its content-type buffer are local UI state.
 */
@Composable
fun MyDocumentPagesScreen(
    title: String,
    pages: List<MyDocPageItem>,
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
    onDelete: (id: Long) -> Unit,
    onExport: (id: Long) -> Unit,
    onCreate: (name: String, type: ContentType) -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onNavigateUp: () -> Unit,
    /**
     * F60: go to the My-documents list to pick a different document. NOT the up arrow's job -- the
     * arrow means "previous view", and entered from the reading view there is no My-documents entry
     * above this screen to go up to. This is a distinct action with a distinct affordance.
     */
    onSwitchDocument: () -> Unit,
    // Selection mode (long-press to enter), driven by hoisted host state.
    selection: Set<Long>,
    onToggleSelected: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
) {
    val s = LocalStrings.current
    var createOpen by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf(ContentType.MARKDOWN) }
    var renameFor by remember { mutableStateOf<MyDocPageItem?>(null) }
    var deleteFor by remember { mutableStateOf<MyDocPageItem?>(null) }
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
                AbActionIcon(Icons.Filled.Add, s.newPageTitle) {
                    createType = ContentType.MARKDOWN
                    createOpen = true
                }
                AbActionIcon(Icons.Filled.LibraryBooks, s.switchDocument, onSwitchDocument)
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
                if (pages.isEmpty()) {
                    Text(
                        s.myDocumentsEmpty,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    AbReorderableColumn(items = pages, key = { it.id }, onMove = onMove) { item, handle ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The drag handle must sit OUTSIDE the clickable area. sh.calvin.reorderable's
                            // press detector delegates to detectDragGestures, which neither consumes the
                            // down nor claims the gesture before touch slop — so a parent long-press timer
                            // runs concurrently, and holding the handle still to aim a deliberate reorder
                            // fired onLongClick: selection mode came on, the handle was swapped for a
                            // Checkbox, and the draggableHandle node vanished mid-gesture (aborting the
                            // drag and removing every handle in the list). longPressToDrag = false does not
                            // help — the conflict is with the row, not with the library's handle modes.
                            if (!selectionMode) {
                                if (!filtering) {
                                    Icon(
                                        Icons.Filled.DragHandle, contentDescription = null,
                                        modifier = handle.padding(horizontal = 12.dp),
                                    )
                                } else {
                                    Spacer(Modifier.size(48.dp))
                                }
                            }
                            Row(
                                modifier = Modifier.weight(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) onToggleSelected(item.id) else onOpen(item.id)
                                        },
                                        onLongClick = { onToggleSelected(item.id) },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // onCheckedChange = null so the enclosing clickable owns the gesture and the
                                // row announces itself once (precedent: RawLogHistoryScreen, BookmarksScreen).
                                // The 48.dp box is load-bearing: a non-interactive Checkbox no longer gets
                                // minimumInteractiveComponentSize, so it would measure its bare 24.dp visual
                                // and the label column would land at a different x than in the drag-handle
                                // mode — i.e. the whole list would jump sideways when selection mode starts.
                                // This gives it exactly the handle's 48.dp leading slot instead.
                                if (selectionMode) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        Checkbox(checked = item.id in selection, onCheckedChange = null)
                                    }
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
                                        item.contentType.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!selectionMode) {
                                PageOverflow(
                                    onRename = { renameFor = item },
                                    onExport = { onExport(item.id) },
                                    onDelete = { deleteFor = item },
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
            title = s.newPageTitle,
            initialName = s.newPageName(totalCount + 1),
            confirmText = s.okay,
            importText = s.importPage,
            // No blank/trim guard needed: AbCreateItemSheet disables its confirm button while the name
            // is blank and hands over an already-trimmed value.
            onCreate = { createOpen = false; onCreate(it, createType) },
            onImport = { createOpen = false; onImport() },
            onDismiss = { createOpen = false },
            extraContent = {
                AbDropdownField(
                    label = s.contentTypeLabel, selected = createType,
                    options = listOf(ContentType.MARKDOWN, ContentType.HTML),
                    optionLabel = { it.name }, onSelect = { createType = it },
                )
            },
        )
    }
    renameFor?.let { item ->
        AbTextInputDialog(
            title = s.pageRenameTitle, initial = item.name, confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { renameFor = null; if (it.isNotBlank()) onRename(item.id, it.trim()) },
            onDismiss = { renameFor = null },
        )
    }
    deleteFor?.let { item ->
        AbConfirmDialog(
            title = null, message = s.deletePageConfirmation(item.name),
            confirmText = s.yes, dismissText = s.no,
            onConfirm = { deleteFor = null; onDelete(item.id) }, onDismiss = { deleteFor = null },
        )
    }
    if (confirmBatchDelete) {
        AbConfirmDialog(
            title = null, message = s.deletePagesConfirmation,
            confirmText = s.yes, dismissText = s.no,
            onConfirm = { confirmBatchDelete = false; onDeleteSelected() },
            onDismiss = { confirmBatchDelete = false },
        )
    }
}

@Composable
private fun PageOverflow(onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = LocalStrings.current.menu) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AbMenuItem(
                text = s.rename,
                onClick = { expanded = false; onRename() },
                icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
            )
            AbMenuItem(
                text = s.export,
                onClick = { expanded = false; onExport() },
                icon = { Icon(Icons.Filled.Share, contentDescription = null) },
            )
            AbMenuItem(
                text = s.deleteLabel,
                onClick = { expanded = false; onDelete() },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            )
        }
    }
}
