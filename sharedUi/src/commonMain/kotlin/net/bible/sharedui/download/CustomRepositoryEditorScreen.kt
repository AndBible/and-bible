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
package net.bible.sharedui.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.download.EditorState
import net.bible.sharedcore.download.Validation
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless port of classic `CustomRepositoryEditor`: the manifest-URL-driven form for adding or
 * editing a single custom repository. [state] drives everything (mirrors `EditorState`); typing in
 * the URL field debounces into validation host-side (the `:sharedCore`
 * `CustomRepositoryEditorController`), reflected here purely via [EditorState.validation].
 *
 * The discard-changes-confirmation (classic `cancelOrConfirmDiscard`) and delete-confirmation
 * (classic `delete()`) dialogs are both local UI state here (same pattern as
 * `PromptEditScreen`/`MyDocumentsScreen`): [onUp]/[onDelete] fire only after the user confirms, so
 * the host needs no extra wiring for them. The help icon opens this composable's own [AbInfoDialog]
 * (mirrors classic's `help()` custom `AlertDialog`, body built from `custom_repositories_help0/1/2`
 * + a clickable wiki-page link) -- see [CustomRepositoriesScreen]'s kdoc for the same [onHelp] note.
 */
@Composable
fun CustomRepositoryEditorScreen(
    state: EditorState,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onPackageDirChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onHelp: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyHelpDialogOpen: Boolean = false,
) {
    val strings = LocalStrings.current
    var showHelp by remember { mutableStateOf(initiallyHelpDialogOpen) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    // Mirrors classic `cancelOrConfirmDiscard()`: only prompt when there are unsaved changes.
    val requestUp: () -> Unit = { if (state.isDirty) showDiscardConfirm = true else onUp() }

    AbScaffold(
        title = strings.customRepositories,
        onNavigateUp = requestUp,
        actions = {
            // Not AbActionIcon: needs `enabled` (classic `saveMenuItem?.isEnabled = valid`), which
            // AbActionIcon doesn't expose -- mirrors PromptEditScreen's save-icon action exactly.
            IconButton(onClick = onSave, enabled = state.canSave) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = strings.okay,
                    modifier = Modifier.size(AbActionIconSize),
                )
            }
            if (state.isExisting) {
                AbActionIcon(Icons.Filled.Delete, contentDescription = strings.deleteLabel) {
                    showDeleteConfirm = true
                }
            }
            AbActionIcon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = strings.help) {
                showHelp = true
                onHelp()
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                singleLine = true,
                label = { Text(strings.repositorySpecification) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPaste) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null)
                        }
                        when (state.validation) {
                            Validation.Validating -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                            Validation.Valid -> Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Validation.Invalid, Validation.Idle -> {}
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            val resolved = state.resolved
            if (resolved != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${resolved.name}\n\n${resolved.description}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.packageDirectory,
                onValueChange = onPackageDirChange,
                singleLine = true,
                label = { Text(strings.packagesDir) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDiscardConfirm) {
        AbConfirmDialog(
            title = null,
            message = strings.discardChangesConfirmation,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { showDiscardConfirm = false; onUp() },
            onDismiss = { showDiscardConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        AbConfirmDialog(
            title = null,
            message = strings.deleteCustomRepository(state.resolved?.name ?: ""),
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    if (showHelp) {
        AbInfoDialog(
            title = strings.customRepositories,
            body = "${strings.customRepositoriesHelp0}\n\n${strings.customRepositoriesHelp1}\n\n" +
                strings.customRepositoriesHelp2(strings.wikiPage),
            onDismiss = { showHelp = false },
            readMoreLabel = strings.wikiPage,
            readMoreUrl = customRepositoriesWikiUrl,
        )
    }
}
