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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.ProviderEditState
import net.bible.sharedcore.ai.ProviderTypeVd
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings

/**
 * The two API formats a CUSTOM (OpenAI-compatible) provider can speak. Mirrors
 * `net.bible.service.llm.ApiFormat` (Android-only enum, not portable to :sharedUi) — kept as a
 * plain string list here since [ProviderEditState.apiFormatId] is already flattened to the enum's
 * `.name`, same as the classic `AiProvidersActivity` spinner (raw enum names, not localized).
 */
private val API_FORMATS = listOf("OPENAI", "ANTHROPIC")

/**
 * The AI providers list + add/edit dialog. Mirrors the classic `AiProvidersActivity`/
 * `AiProvidersFragment`: a scaffolded list of configured providers (row = display name + masked/"not
 * set" API key), an add action in the top bar, and a two-step dialog driven entirely by
 * [editState] (`null` = no dialog open):
 *
 * - [ProviderEditState.Step.PICK_TYPE]: a single-choice picker over [providerTypes] (the host
 *   supplies the filtered/ordered list, e.g. excluding already-configured builtin providers).
 *   Selecting a type calls [onPickType] with its id, which the controller resolves into the FORM
 *   step's prefilled state.
 * - [ProviderEditState.Step.FORM]: the detail form — display name (editable only when
 *   [ProviderEditState.isCustom], since builtin providers use a fixed name), API key, an optional
 *   api-key-url link, and (custom only) endpoint + API-format fields. OK is enabled only when
 *   [ProviderEditState.canSave]; a neutral "Delete" action appears only when editing
 *   ([ProviderEditState.id] non-null) and opens an [AbConfirmDialog] before calling [onDelete].
 *
 * Field edits go through one [onField] callback keyed by [AiProvidersController.Field] (reusing the
 * controller's own enum rather than a duplicate — the controller already exposes it as part of its
 * public `updateField(field, value)` API, so there is nothing to gain from a second, shadow enum
 * here; this also keeps the call site a direct pass-through: `onField = controller::updateField`).
 *
 * F31: [showAcceptDisclaimerDialog] renders the "Accept AI disclaimer" `AbConfirmDialog` (replacing
 * classic's `AlertDialog.Builder`-based accept flow, ported from `AiSettingsFragmentBase`). The host
 * gates opening the add-provider dialog / Quick-setup wizard on `LlmProviderService.disclaimerAccepted()`
 * and stashes the pending continuation while the dialog is shown; [onAcceptDisclaimer] must reach
 * [AiProvidersController.acceptDisclaimer] (not the service directly) and then resume that
 * continuation, [onDismissAcceptDisclaimer] just drops it.
 */
@Composable
fun AiProvidersScreen(
    providers: List<ProviderVd>,
    providerTypes: List<ProviderTypeVd>,
    editState: ProviderEditState?,
    onUp: () -> Unit,
    onAdd: () -> Unit,
    onPickType: (String) -> Unit,
    onStartEdit: (String) -> Unit,
    onField: (AiProvidersController.Field, String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    helpBody: String,
    helpReadMoreUrl: String,
    initiallyHelpDialogOpen: Boolean = false,
    showAcceptDisclaimerDialog: Boolean = false,
    onAcceptDisclaimer: () -> Unit = {},
    onDismissAcceptDisclaimer: () -> Unit = {},
) {
    val strings = LocalStrings.current
    var showHelpMenu by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(initiallyHelpDialogOpen) }

    AbScaffold(
        title = strings.aiProvidersTitle,
        onNavigateUp = onUp,
        actions = {
            AbActionIcon(Icons.Filled.Add, contentDescription = strings.addProvider, onClick = onAdd)
            IconButton(onClick = { showHelpMenu = true }) {
                Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
            }
            DropdownMenu(expanded = showHelpMenu, onDismissRequest = { showHelpMenu = false }) {
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { showHelpMenu = false; showHelp = true })
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(providers, key = { it.id }) { provider ->
                TwoLineListItem(
                    title = provider.displayName,
                    subtitle = if (provider.apiKeySet) strings.providerApiKeyMasked(strings.providerApiKeyLabel, "") else strings.providerApiKeyNotSet,
                    onClick = { onStartEdit(provider.id) },
                )
            }
        }
    }

    if (editState != null) {
        when (editState.step) {
            ProviderEditState.Step.PICK_TYPE -> AbListChoiceDialog(
                title = strings.providerSelectType,
                choices = providerTypes.map { SettingsItem.Choice(it.id, it.displayName) },
                selectedValue = editState.typeId,
                onSelect = onPickType,
                onDismiss = onDismiss,
            )
            ProviderEditState.Step.FORM -> ProviderFormDialog(
                state = editState,
                onField = onField,
                onSave = onSave,
                onDelete = onDelete,
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

    if (showAcceptDisclaimerDialog) {
        AbConfirmDialog(
            title = strings.aiDisclaimerAcceptTitle,
            message = strings.aiDisclaimerBody,
            confirmText = strings.aiDisclaimerAcceptButton,
            dismissText = strings.cancel,
            onConfirm = onAcceptDisclaimer,
            onDismiss = onDismissAcceptDisclaimer,
        )
    }
}

/**
 * The FORM-step add/edit dialog. All field values are driven by [state] (single source of truth,
 * owned by the controller) — this composable holds only the local "is the delete confirmation
 * open" flag, reset whenever the edited provider changes ([state]'s `id`).
 */
@Composable
private fun ProviderFormDialog(
    state: ProviderEditState,
    onField: (AiProvidersController.Field, String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    var showDeleteConfirm by remember(state.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id == null) strings.addProvider else strings.providerEditTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { onField(AiProvidersController.Field.NAME, it) },
                    enabled = state.isCustom,
                    singleLine = true,
                    label = { Text(strings.providerNameLabel) },
                    placeholder = { Text(strings.providerNameHint) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = { onField(AiProvidersController.Field.API_KEY, it) },
                    singleLine = true,
                    label = { Text(strings.providerApiKeyLabel) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val apiKeyUrl = state.apiKeyUrl
                if (apiKeyUrl != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${strings.apiKeyInstructionsPrefix} $apiKeyUrl",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { uriHandler.openUri(apiKeyUrl) },
                    )
                }
                if (state.isCustom) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.endpoint,
                        onValueChange = { onField(AiProvidersController.Field.ENDPOINT, it) },
                        singleLine = true,
                        label = { Text(strings.providerEndpointLabel) },
                        placeholder = { Text(strings.providerEndpointHint) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(text = strings.providerEndpointDescription, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    AbDropdownField(
                        label = strings.providerApiFormatLabel,
                        selected = state.apiFormatId,
                        options = API_FORMATS,
                        optionLabel = { it },
                        onSelect = { onField(AiProvidersController.Field.API_FORMAT, it) },
                        horizontalPadding = 0.dp,
                        verticalPadding = 0.dp,
                    )
                }
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
            message = strings.providerDeleteConfirm(state.displayName),
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
