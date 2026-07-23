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

package net.bible.sharedui.ai.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.ai.reading.ReadingModelVd
import net.bible.sharedcore.ai.reading.ReadingPromptGroupVd
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings

/** Bounded height for the scrollable list bodies (prompt selector / model selection), so a long
 *  list scrolls inside the dialog rather than growing the dialog off-screen. */
private val maxListHeight = 480.dp

/**
 * Renders the reading-view AI/LLM dialogs (mirrors classic `LlmDialogHelper`): the grouped
 * prompt selector, the free-text "specify before run" prompt, the model-selection dialog, and the
 * regenerate-page confirmation. Stateless — [dialog] is the single source of truth for which (if
 * any) is shown; every user action is reported via a callback, never mutated locally except for
 * this-dialog-session-only UI state (typed instructions, checked checkboxes) that is discarded
 * once the dialog closes. Each non-`None` arm is a top-level M3 [AlertDialog] — never a popup/menu
 * — so it renders (and can be captured by Roborazzi) without needing a real popup anchor.
 */
@Composable
fun ReadingLlmDialogs(
    dialog: ReadingLlmDialog,
    onPromptChosen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit,
    onSpecifySubmitted: (String) -> Unit,
    onModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit,
    onRegenerateConfirmed: (instructions: String?, keepPrevious: Boolean, freshRun: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    when (dialog) {
        ReadingLlmDialog.None -> Unit

        is ReadingLlmDialog.PromptSelector -> PromptSelectorDialog(
            groups = dialog.groups,
            onPromptChosen = onPromptChosen,
            onToggleFavorite = onToggleFavorite,
            onCategoryExpandedChanged = onCategoryExpandedChanged,
            onDismiss = onDismiss,
        )

        is ReadingLlmDialog.SpecifyBeforeRun -> AbTextInputDialog(
            title = strings.specifyBeforeRunTitle,
            initial = "",
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = onSpecifySubmitted,
            onDismiss = onDismiss,
        )

        is ReadingLlmDialog.ModelSelection -> ModelSelectionDialog(
            models = dialog.models,
            allowSetDefault = dialog.allowSetDefault,
            onModelChosen = onModelChosen,
            onDismiss = onDismiss,
        )

        is ReadingLlmDialog.Regenerate -> RegenerateDialog(
            onRegenerateConfirmed = onRegenerateConfirmed,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun PromptSelectorDialog(
    groups: List<ReadingPromptGroupVd>,
    onPromptChosen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectLlmPrompt) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                groups.forEachIndexed { index, group ->
                    val groupKey = group.categoryId ?: " uncategorized"
                    if (index > 0) item(key = "divider-$index") { HorizontalDivider() }
                    item(key = "header-$groupKey") {
                        PromptGroupHeader(
                            title = group.categoryName,
                            count = group.prompts.size,
                            expanded = !group.collapsed,
                            // Reports the NEW expanded state (i.e. the toggled value of `collapsed`).
                            onToggle = { onCategoryExpandedChanged(group.categoryId, group.collapsed) },
                        )
                    }
                    if (!group.collapsed) {
                        items(group.prompts, key = { "prompt-$groupKey-${it.id}" }) { prompt ->
                            PromptRow(
                                name = prompt.name,
                                description = prompt.description,
                                isFavorite = prompt.isFavorite,
                                onClick = { onPromptChosen(prompt.id) },
                                onToggleFavorite = { onToggleFavorite(prompt.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun PromptGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun PromptRow(
    name: String,
    description: String,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TwoLineListItem(
            title = name,
            subtitle = description,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
    }
}

@Composable
private fun ModelSelectionDialog(
    models: List<ReadingModelVd>,
    allowSetDefault: Boolean,
    onModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var setDefault by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectModelBeforeRunTitle) },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                    items(models, key = { it.id }) { model ->
                        val label = buildString {
                            if (model.supported) append("✓ ")
                            append(model.modelId)
                            append(" — ")
                            append(model.providerName)
                            if (model.isDefault) append(" ★")
                        }
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onModelChosen(model.id, setDefault) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                if (allowSetDefault) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = setDefault, onCheckedChange = { setDefault = it })
                        Text(strings.setDefaultModelForPrompt)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
private fun RegenerateDialog(
    onRegenerateConfirmed: (instructions: String?, keepPrevious: Boolean, freshRun: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var instructions by remember { mutableStateOf("") }
    var keepPrevious by remember { mutableStateOf(false) }
    var freshRun by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.aiRegenerateTitle) },
        text = {
            Column {
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(strings.aiRegenerateInstructionsHint) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepPrevious, onCheckedChange = { keepPrevious = it })
                    Text(strings.aiRegenerateKeepPrevious)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = freshRun, onCheckedChange = { freshRun = it })
                    Text(strings.aiRegenerateFreshRun)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRegenerateConfirmed(instructions.ifBlank { null }, keepPrevious, freshRun)
            }) { Text(strings.aiDocumentRegenerate) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
