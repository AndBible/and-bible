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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.mydocuments.ContentType
import net.bible.sharedcore.mydocuments.MyDocPageItem
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbReorderableColumn
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless page editor for one document. Sibling of [MyDocumentsScreen] minus the description; rows
 * carry a name + content-type subtitle, and the create-page dialog carries a content-type dropdown.
 * All persistence is hoisted (the host wires [net.bible.sharedcore.mydocuments.MyDocumentPagesController]
 * to these callbacks); only which dialog is open and its content-type buffer are local UI state.
 */
@Composable
fun MyDocumentPagesScreen(
    title: String,
    pages: List<MyDocPageItem>,
    dirty: Boolean,
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
) {
    val s = LocalStrings.current
    var createOpen by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf(ContentType.MARKDOWN) }
    var renameFor by remember { mutableStateOf<MyDocPageItem?>(null) }
    var deleteFor by remember { mutableStateOf<MyDocPageItem?>(null) }

    AbScaffold(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(
                    text = { Text(s.newPageTitle) },
                    onClick = { close(); createType = ContentType.MARKDOWN; createOpen = true },
                )
                DropdownMenuItem(text = { Text(s.importPage) }, onClick = { close(); onImport() })
            }
        },
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
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(item.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.DragHandle, contentDescription = null,
                                modifier = handle.padding(horizontal = 12.dp),
                            )
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
                            PageOverflow(
                                onRename = { renameFor = item },
                                onExport = { onExport(item.id) },
                                onDelete = { deleteFor = item },
                            )
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
        AbTextInputDialog(
            title = s.newPageTitle, initial = s.newPageName(pages.size + 1),
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { createOpen = false; if (it.isNotBlank()) onCreate(it.trim(), createType) },
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
}

@Composable
private fun PageOverflow(onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(s.rename) }, onClick = { expanded = false; onRename() })
            DropdownMenuItem(text = { Text(s.export) }, onClick = { expanded = false; onExport() })
            DropdownMenuItem(text = { Text(s.deleteLabel) }, onClick = { expanded = false; onDelete() })
        }
    }
}
