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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbReorderableColumn
import net.bible.sharedui.components.AbScaffold
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
) {
    val s = LocalStrings.current
    // Local dialog state (which dialog + target item + text buffer). Not hoisted (pure UI).
    var createOpen by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<MyDocItem?>(null) }
    var descFor by remember { mutableStateOf<MyDocItem?>(null) }
    var deleteFor by remember { mutableStateOf<MyDocItem?>(null) }
    var blockedAiDelete by remember { mutableStateOf(false) }

    AbScaffold(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = {
            AbOverflowMenu(contentDescription = null) {
                DropdownMenuItem(text = { Text(s.newItem) }, onClick = { createOpen = true })
                DropdownMenuItem(text = { Text(s.importDocument) }, onClick = { onImport() })
            }
        },
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
                                    item.description.ifEmpty { s.noDescription },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(s.dismiss) }
                TextButton(onClick = onSave, enabled = dirty, modifier = Modifier.weight(1f)) { Text(s.saveAndExit) }
            }
        }
    }

    if (createOpen) {
        TextInputDialog(
            title = s.createTitle, initial = s.newDocumentName(documents.size + 1),
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { createOpen = false; if (it.isNotBlank()) onCreate(it.trim()) },
            onDismiss = { createOpen = false },
        )
    }
    // Import name-entry prompt (parity with classic MyDocumentsActivity.showImportNameDialog): the host
    // has already picked URIs via SAF and set the pre-fill string; confirm hands the name back to the host.
    importNamePrompt?.let { prompt ->
        TextInputDialog(
            title = s.createTitle, initial = prompt,
            confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { if (it.isNotBlank()) onConfirmImport(it.trim()) else onDismissImport() },
            onDismiss = onDismissImport,
        )
    }
    renameFor?.let { item ->
        TextInputDialog(
            title = s.renameTitle, initial = item.name, confirmText = s.okay, dismissText = s.cancel,
            onConfirm = { renameFor = null; if (it.isNotBlank()) onRename(item.id, it.trim()) },
            onDismiss = { renameFor = null },
        )
    }
    descFor?.let { item ->
        TextInputDialog(
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
            DropdownMenuItem(text = { Text(s.rename) }, onClick = { expanded = false; onRename() })
            DropdownMenuItem(text = { Text(s.editDescriptionLabel) }, onClick = { expanded = false; onEditDescription() })
            DropdownMenuItem(text = { Text(s.exportDocumentLabel) }, onClick = { expanded = false; onExport() })
            DropdownMenuItem(text = { Text(s.deleteLabel) }, onClick = { expanded = false; onDelete() })
        }
    }
}

/** A titled AlertDialog wrapping a single-line text field, returning the trimmed value on confirm. */
@Composable
internal fun TextInputDialog(
    title: String, initial: String, confirmText: String, dismissText: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                extraContent?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
