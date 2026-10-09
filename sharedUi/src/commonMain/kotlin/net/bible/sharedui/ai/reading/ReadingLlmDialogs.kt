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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import net.bible.sharedui.components.AbAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import net.bible.sharedui.components.AbModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import net.bible.sharedui.components.AbSheetHeader
import net.bible.sharedui.components.AbSheetScrollBound
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.components.toggleStateSemantics
import net.bible.sharedui.strings.LocalStrings

/** Bounded height for the scrollable list bodies (prompt selector / model chooser). Kept at 480dp
 *  through round 14a's dialog->sheet conversion: the spec's brief for these two is "same content,
 *  same rows", so the container is the only thing that changes. New wrappers use the port's
 *  `AbSheetContentMaxHeight` (400dp) instead. */
private val maxListHeight = 480.dp

/**
 * Renders the reading-view AI/LLM surfaces (mirrors classic `LlmDialogHelper`): the grouped prompt
 * selector, the free-text "specify before run" prompt, the model chooser, and the regenerate-page
 * confirmation. Stateless — [dialog] is the single source of truth for which (if any) is shown; every
 * user action is reported via a callback, never mutated locally except for this-session-only UI state
 * (typed instructions, checked checkboxes) that is discarded once the surface closes.
 *
 * **Two arms are SHEETS since round 14a and two are still dialogs; the difference decides how they
 * can be tested.** `PromptSelector` and `ModelSelection` are list pickers, so they are
 * `ModalBottomSheet`s (spec §3 group 2). `SpecifyBeforeRun` is one short field plus the IME — a
 * keyboard and a bottom sheet fight each other — and `Regenerate` is a confirmation, so both stay
 * `AlertDialog`s (spec §3 group 4). Consequently a Roborazzi golden may still capture THIS composable
 * for the two dialog arms but NEVER for the two sheet arms: an open `ModalBottomSheet` hangs the
 * capture and takes the whole `:app` suite with it. Capture [PromptSelectorSheetContent] /
 * [ModelSelectionSheetContent] instead. Both halves are machine-enforced by
 * `SettingsEditorSheetGuardTest` — by wrapper name, and by arm construction inside a capturing file.
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

        is ReadingLlmDialog.PromptSelector -> PromptSelectorSheet(
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

        is ReadingLlmDialog.ModelSelection -> ModelSelectionSheet(
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

/**
 * The prompt selector's shell. Private: it is reachable only through [ReadingLlmDialogs]' `when`,
 * which is also its open gate — hence no hoisted `open` parameter, unlike the generic
 * `AbChoiceSheet`.
 *
 * The dialog's `dismissButton` cancel is DROPPED: a sheet is dismissed by swipe, scrim tap or back,
 * and round 13a established the header ✕ as the explicit close affordance (spec §4).
 *
 * There is deliberately no `LaunchedEffect(sheetState.isVisible)` re-show. Material3 does run `hide()`
 * before invoking `onDismissRequest`, and `SpeakSettingsSheet.kt:81-90` carries such an effect — but
 * only because ITS dismiss can step back a page without changing what is shown. Here dismiss means
 * close: `onDismiss` is the controller's `dismiss()`, which sets the state to `None`, and this whole
 * composable leaves the tree. Copying that effect here would fight the close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptSelectorSheet(
    groups: List<ReadingPromptGroupVd>,
    onPromptChosen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AbModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PromptSelectorSheetContent(
            groups = groups,
            // NOT wrapped in a dismiss, unlike AbChoiceSheet's onSelect. Choosing a prompt does not
            // end the flow: the controller advances the SAME state machine to SpecifyBeforeRun or
            // ModelSelection, or executes and dismisses itself. Dismissing here would cancel the run.
            onPromptChosen = onPromptChosen,
            onToggleFavorite = onToggleFavorite,
            onCategoryExpandedChanged = onCategoryExpandedChanged,
            onClose = onDismiss,
        )
    }
}

/** [PromptSelectorSheet]'s body: the group headers with counts and caret, the two-line rows and the
 *  trailing favourite toggles, exactly as [PromptGroupHeader] and [PromptRow] render them. */
@Composable
fun PromptSelectorSheetContent(
    groups: List<ReadingPromptGroupVd>,
    onPromptChosen: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit,
    onClose: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = strings.selectLlmPrompt, onClose = onClose)
        AbSheetScrollBound(
            canScrollForward = { listState.canScrollForward },
            maxHeight = maxListHeight,
        ) {
            LazyColumn(state = listState) {
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
        }
    }
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
    val strings = LocalStrings.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TwoLineListItem(
            title = name,
            subtitle = description,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleFavorite, modifier = Modifier.toggleStateSemantics(isFavorite)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) strings.promptFavoriteRemove else strings.promptFavoriteAdd,
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
        }
    }
}

/** The model chooser's shell. Same private/no-`open` shape and same no-re-show reasoning as
 *  [PromptSelectorSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSheet(
    models: List<ReadingModelVd>,
    allowSetDefault: Boolean,
    onModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AbModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ModelSelectionSheetContent(
            models = models,
            allowSetDefault = allowSetDefault,
            onModelChosen = onModelChosen,
            onClose = onDismiss,
        )
    }
}

/**
 * [ModelSelectionSheet]'s body: the model list scrolls; the "set as default" checkbox is a
 * NON-SCROLLING footer row below it.
 *
 * That split is the point, not decoration. A bounded scroll region that ends flush at its clip with
 * nothing beneath it is exactly what made the Speak settings page read as finished when it was not
 * (spec §1.1); a footer outside the scroll gives the region a bottom frame, the same way
 * `ColorSettingsEditorSheet`'s confirm row does. It is padded like [SheetConfirmRow]
 * (`horizontal = 8.dp, vertical = 4.dp`) so the two read as one idiom, but it is not that composable:
 * there is no confirm button to draw. Tapping a model row IS the commit.
 */
@Composable
fun ModelSelectionSheetContent(
    models: List<ReadingModelVd>,
    allowSetDefault: Boolean,
    onModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit,
    onClose: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val strings = LocalStrings.current
    var setDefault by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = strings.selectModelBeforeRunTitle, onClose = onClose)
        AbSheetScrollBound(
            canScrollForward = { listState.canScrollForward },
            maxHeight = maxListHeight,
        ) {
            LazyColumn(state = listState) {
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
        }
        if (allowSetDefault) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = setDefault, role = Role.Checkbox, onValueChange = { setDefault = it })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = setDefault, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.setDefaultModelForPrompt)
            }
        }
    }
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
    AbAlertDialog(
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
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).toggleable(value = keepPrevious, role = Role.Checkbox, onValueChange = { keepPrevious = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keepPrevious, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.aiRegenerateKeepPrevious)
                }
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).toggleable(value = freshRun, role = Role.Checkbox, onValueChange = { freshRun = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = freshRun, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
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
