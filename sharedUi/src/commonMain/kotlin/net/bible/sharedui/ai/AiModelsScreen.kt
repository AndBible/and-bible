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

package net.bible.sharedui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.ModelEditState
import net.bible.sharedcore.ai.ModelVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchablePicker
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings

/**
 * The AI models list + add/edit dialog. Mirrors the classic `AiModelsFragment`/`ModelDialogs.kt`:
 * a scaffolded list of configured models (row = "★ " default prefix + display name + " ✓" supported
 * suffix, summary = the host's pre-formatted [ModelVd.pricingSummary]), an add action in the top
 * bar, and a dialog driven entirely by [editState] (`null` = no dialog open):
 *
 * - [ModelEditState.Step.PICK_PROVIDER]: only reached from the add flow ([ModelEditState.id] ==
 *   null) — a single-choice picker over [ModelEditState.providerChoices]. Selecting a provider
 *   calls [onPickProvider], which the controller resolves into the PICK_MODEL step (kicking off an
 *   async available-models fetch).
 * - [ModelEditState.Step.PICK_MODEL]: the detail form. For the **add** flow ([ModelEditState.id] ==
 *   null) this is a full picker — an optional category filter (shown only when any available model
 *   id contains "/", e.g. OpenRouter-style ids), an [AbSearchablePicker] over the (category- and
 *   supported-)filtered available models plus a synthetic "Custom…" entry
 *   ([AiModelsController.CUSTOM_MODEL_ID]), and — when custom is picked — a free-text model-id
 *   field. For the **edit** flow ([ModelEditState.id] != null) the model id is fixed and shown
 *   read-only instead (mirrors the classic edit dialog, which never lets you re-pick a model,
 *   consistent with [ModelEditState.availableModels] staying empty there). Both flows share:
 *   editable price fields (only when [ModelEditState.showPriceFields] — unknown pricing), a
 *   "set as default" switch, and — edit only — a neutral Delete action opening an
 *   [AbConfirmDialog].
 *
 * Two deliberate extensions beyond a literal 1:1 mirror of [AiProvidersScreen]'s signature, both
 * driven by controller surface that has no Providers-screen analogue:
 * - [onSetDefault] wires the controller's `setDefault(id)` (immediate, list-scoped — no dialog
 *   involved) to a small leading star toggle on each row, so a model can be made default without
 *   opening the edit dialog. This is distinct from the in-dialog "set as default" switch, which
 *   edits [ModelEditState.setAsDefault] (only applied on [onSave]) and is wired through the added
 *   [onSetAsDefault] parameter.
 * - [providers] (the full configured-provider list, e.g. the same list [AiProvidersScreen] shows)
 *   resolves [ModelEditState.providerId] to a display name for the read-only "Provider" field in
 *   the **edit** flow, where [ModelEditState.providerChoices] is empty (only populated by
 *   [AiModelsController.startAdd]). The add flow prefers `providerChoices` (guaranteed to include
 *   the just-picked provider even before any `providers` refresh lands).
 *
 * The "show unsupported" filter is round-tripped through the controller like every other field:
 * [ModelEditState.showUnsupported] is the single source of truth and [onSetShowUnsupported] wires
 * the switch to [AiModelsController.setShowUnsupported], analogous to [onSetAsDefault]. (Previously
 * this was screen-local `remember` state that never reached the controller.) The add flow's
 * category filter has no controller-side counterpart and remains screen-local `remember` state.
 */
@Composable
fun AiModelsScreen(
    models: List<ModelVd>,
    providers: List<ProviderVd>,
    editState: ModelEditState?,
    onUp: () -> Unit,
    onAdd: () -> Unit,
    onPickProvider: (String) -> Unit,
    onPickModel: (String) -> Unit,
    onStartEdit: (String) -> Unit,
    onField: (AiModelsController.Field, String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onSetAsDefault: (Boolean) -> Unit,
    onSetShowUnsupported: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    helpBody: String,
    helpReadMoreUrl: String,
    initiallyHelpDialogOpen: Boolean = false,
) {
    val strings = LocalStrings.current
    var showHelpMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(initiallyHelpDialogOpen) }

    AbScaffold(
        title = strings.aiModelsTitle,
        onNavigateUp = onUp,
        actions = {
            AbActionIcon(Icons.Filled.Add, contentDescription = strings.addModel, onClick = onAdd)
            IconButton(onClick = { showHelpMenu = true }) {
                Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
            }
            DropdownMenu(expanded = showHelpMenu, onDismissRequest = { showHelpMenu = false }) {
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { showHelpMenu = false; showHelp = true })
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(models, key = { it.id }) { model ->
                ModelRow(
                    model = model,
                    onClick = { onStartEdit(model.id) },
                    onSetDefault = { onSetDefault(model.id) },
                )
            }
        }
    }

    if (editState != null) {
        when (editState.step) {
            ModelEditState.Step.PICK_PROVIDER -> AbListChoiceDialog(
                title = strings.modelSelectProviderLabel,
                choices = editState.providerChoices.map { SettingsItem.Choice(it.id, it.displayName) },
                selectedValue = editState.providerId,
                onSelect = onPickProvider,
                onDismiss = onDismiss,
            )
            ModelEditState.Step.PICK_MODEL -> ModelFormDialog(
                state = editState,
                models = models,
                providers = providers,
                onPickModel = onPickModel,
                onField = onField,
                onSave = onSave,
                onDelete = onDelete,
                onSetAsDefault = onSetAsDefault,
                onSetShowUnsupported = onSetShowUnsupported,
                onDismiss = onDismiss,
            )
        }
    }

    if (showHelp) {
        AbInfoDialog(
            title = strings.helpLabel,
            body = helpBody,
            onDismiss = { showHelp = false },
            readMoreLabel = strings.helpReadMoreLink,
            readMoreUrl = helpReadMoreUrl,
        )
    }
}

/** A configured-model row: leading tap-to-set-default star, title with badges, pricing summary. */
@Composable
private fun ModelRow(
    model: ModelVd,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSetDefault, enabled = !model.isDefault) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (model.isDefault) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
        TwoLineListItem(
            title = buildString {
                append(model.displayName)
                if (model.supported) append(" ✓")
            },
            subtitle = model.pricingSummary,
            onClick = onClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The PICK_MODEL-step add/edit dialog. All field values are driven by [state] (single source of
 * truth, owned by the controller), including the "show unsupported" filter
 * ([ModelEditState.showUnsupported] / [onSetShowUnsupported]); only the (add-only) category filter
 * is screen-local (see [AiModelsScreen] doc). [models]/[providers] supply read-only context the
 * controller state doesn't carry directly: the edited model's `supported`/`pricingSummary` (for the
 * edit flow's read-only badge/price display, keyed by [ModelEditState.id]) and the picked provider's
 * display name (for the read-only "Provider" field), respectively.
 */
@Composable
private fun ModelFormDialog(
    state: ModelEditState,
    models: List<ModelVd>,
    providers: List<ProviderVd>,
    onPickModel: (String) -> Unit,
    onField: (AiModelsController.Field, String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onSetAsDefault: (Boolean) -> Unit,
    onSetShowUnsupported: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var showDeleteConfirm by remember(state.id) { mutableStateOf(false) }

    val providerName = (state.providerChoices + providers).firstOrNull { it.id == state.providerId }?.displayName
        ?: state.providerId
    val editedModel = models.firstOrNull { it.id == state.id }

    // Screen-local filter for the add flow's picker; the category filter has no controller-side
    // counterpart (see AiModelsScreen doc). The show-unsupported filter itself comes from
    // state.showUnsupported below, not screen-local state.
    var category by remember(state.providerId) { mutableStateOf("") } // "" = All

    fun categoryOf(modelId: String) = modelId.substringBefore('/', "")

    val categories = state.availableModels.map { categoryOf(it.modelId) }.filter { it.isNotBlank() }.distinct().sorted()
    val hasSupported = state.availableModels.any { it.supported }
    val hasUnsupported = state.availableModels.any { !it.supported }
    // If NO model is supported, the toggle is hidden (only shown for a genuine mix, mirroring
    // classic) but the list must not filter itself down to empty — treat unsupported as visible.
    val effectiveShowUnsupported = state.showUnsupported || !hasSupported

    val filteredModels = state.availableModels
        .filter { category.isBlank() || categoryOf(it.modelId) == category }
        .filter { effectiveShowUnsupported || it.supported }

    fun modelLabel(id: String): String = when {
        id == AiModelsController.CUSTOM_MODEL_ID -> strings.llmCustomModel
        else -> filteredModels.firstOrNull { it.modelId == id }
            ?.let { (if (it.supported) "✓ " else "") + it.label }
            ?: id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id == null) strings.addModel else strings.editModelTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = providerName,
                    onValueChange = {},
                    enabled = false,
                    singleLine = true,
                    label = { Text(strings.modelSelectProviderLabel) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                if (state.id == null) {
                    // Add flow: full model picker.
                    if (state.loadingModels) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    } else {
                        if (categories.isNotEmpty()) {
                            AbDropdownField(
                                label = strings.llmOpenrouterCategoryLabel,
                                selected = category,
                                options = listOf("") + categories,
                                optionLabel = { if (it.isBlank()) strings.llmOpenrouterCategoryAll else it.replaceFirstChar { c -> c.uppercaseChar() } },
                                onSelect = { category = it },
                                horizontalPadding = 0.dp,
                                verticalPadding = 0.dp,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (hasSupported && hasUnsupported) {
                            AbSwitchRow(
                                label = strings.showUnsupportedModels,
                                checked = state.showUnsupported,
                                onCheckedChange = onSetShowUnsupported,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        AbSearchablePicker(
                            label = strings.llmOpenrouterModelLabel,
                            selected = state.modelId,
                            options = filteredModels.map { it.modelId } + AiModelsController.CUSTOM_MODEL_ID,
                            optionLabel = ::modelLabel,
                            onSelect = onPickModel,
                        )
                        if (state.isCustom) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.customModelId,
                                onValueChange = { onField(AiModelsController.Field.CUSTOM_MODEL_ID, it) },
                                singleLine = true,
                                label = { Text(strings.llmCustomModelHint) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    // Edit flow: model id is fixed, never re-picked.
                    OutlinedTextField(
                        value = state.modelId,
                        onValueChange = {},
                        enabled = false,
                        singleLine = true,
                        label = { Text(strings.llmOpenrouterModelLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (editedModel?.supported == true) {
                        Spacer(Modifier.height(4.dp))
                        Text(strings.modelSupportedBadge, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!state.showPriceFields && editedModel != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(editedModel.pricingSummary, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (state.showPriceFields) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.priceInput,
                        onValueChange = { onField(AiModelsController.Field.PRICE_INPUT, it) },
                        singleLine = true,
                        label = { Text(strings.llmCustomInputPriceLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.priceOutput,
                        onValueChange = { onField(AiModelsController.Field.PRICE_OUTPUT, it) },
                        singleLine = true,
                        label = { Text(strings.llmCustomOutputPriceLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(12.dp))
                AbSwitchRow(
                    label = strings.modelSetDefault,
                    checked = state.setAsDefault,
                    onCheckedChange = onSetAsDefault,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = state.canSave) { Text(strings.okay) }
        },
        dismissButton = {
            Row {
                if (state.id != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text(strings.deleteLabel) }
                }
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        },
    )

    val id = state.id
    if (showDeleteConfirm && id != null) {
        AbConfirmDialog(
            title = null,
            message = strings.modelDeleteConfirm(editedModel?.displayName ?: state.modelId),
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = {
                showDeleteConfirm = false
                onDelete(id)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
