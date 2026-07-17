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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.settings.AbSettingsScreen
import net.bible.sharedui.strings.LocalStrings

/** Stable keys this screen intercepts BEFORE they reach [AbSettingsScreen]'s generic renderer. */
private const val KEY_CUSTOM_AGENT_PROMPT = "custom_agent_prompt"
private const val KEY_CUSTOM_TEXT_TRANSFORM_PROMPT = "custom_text_transform_prompt"
private const val KEY_RAW_LOG_RETENTION = "raw_log_retention"
private const val KEY_AI_LANGUAGE = "ai_language"

private val SPECIAL_KEYS = setOf(
    KEY_CUSTOM_AGENT_PROMPT,
    KEY_CUSTOM_TEXT_TRANSFORM_PROMPT,
    KEY_RAW_LOG_RETENTION,
    KEY_AI_LANGUAGE,
)

/**
 * The AI connection settings screen. Wraps [AbSettingsScreen] (the generic declarative renderer,
 * Task 5) and intercepts four keys that need bespoke editors it does not know how to render:
 *
 * - [KEY_CUSTOM_AGENT_PROMPT] / [KEY_CUSTOM_TEXT_TRANSFORM_PROMPT] (controller emits these as
 *   `TextInputRow`s with `numeric = false`): a multiline prompt editor with a "Reset to default"
 *   action, prefilled from [customPromptTextFor]. Save → [onCustomPromptSave] with the typed text
 *   (blank = reset to default); Reset → [onCustomPromptSave] with `null`.
 * - [KEY_RAW_LOG_RETENTION] (a numeric `TextInputRow`): a numeric editor with a "disable" checkbox
 *   that greys the field. Save → `onTextInputInt("raw_log_retention", days)`, `-1` when disabled.
 * - [KEY_AI_LANGUAGE] (a `ListChoiceRow` with an empty `entries` list — the real locale list is
 *   Android-resource data that can't live in :sharedUi): rendered as a plain clickable summary
 *   row; the click is forwarded to [onEditLanguage] so the `:app` host (Task 8) can show the real
 *   locale/custom-language picker.
 *
 * **Interception approach.** Rather than removing the four items from the state (which would lose
 * their position in the list) or re-implementing the whole row-rendering switch here, this screen
 * rewrites just those four items into `NavigationRow`s with the same `key`/`title`/`summary`/
 * `visible`/`enabled` — so [AbSettingsScreen] draws them as ordinary clickable rows (chevron
 * affordance, correct position) but never opens ITS generic list-choice/text-input dialog for them.
 * All navigation clicks funnel through one `onNavigate` lambda, which this screen overrides: the
 * four special keys open a local dialog (or call [onEditLanguage]); every other key (including
 * [net.bible.sharedcore.ai.AiConnectionNav.RESET_USAGE] and the other nav rows) is forwarded
 * unchanged to the real [onNavigate]. Every other row type (switches, the `agent_permission_mode`
 * `ListChoiceRow`, the two numeric `TextInputRow`s `commentary_max_response`/`agent_max_iterations`,
 * info rows, categories) passes through untouched.
 */
@Composable
fun AiConnectionSettingsScreen(
    state: SettingsScreenState,
    onUp: () -> Unit,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInputInt: (String, Int) -> Unit,
    onCustomPromptSave: (key: String, value: String?) -> Unit,
    customPromptTextFor: (key: String) -> String,
    onEditLanguage: () -> Unit,
    onNavigate: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val displayState = remember(state) {
        state.copy(items = state.items.map { item -> if (item.key in SPECIAL_KEYS) item.asNavigationRow() else item })
    }

    var customPromptDialogKey by remember { mutableStateOf<String?>(null) }
    var retentionDialogOpen by remember { mutableStateOf(false) }

    // Defensive parity with AbSettingsScreen's own dialog-state handling: if the async state stops
    // carrying a key while its dialog is open (item removed outright), close the dialog rather than
    // showing stale content next recomposition.
    LaunchedEffect(customPromptDialogKey, state) {
        val key = customPromptDialogKey
        if (key != null && state.items.none { it.key == key }) {
            customPromptDialogKey = null
        }
    }
    LaunchedEffect(retentionDialogOpen, state) {
        if (retentionDialogOpen && state.items.none { it.key == KEY_RAW_LOG_RETENTION }) {
            retentionDialogOpen = false
        }
    }

    AbSettingsScreen(
        state = displayState,
        onUp = onUp,
        onSwitch = onSwitch,
        onListChoice = onListChoice,
        // Only commentary_max_response / agent_max_iterations (both numeric TextInputRows) still
        // reach here — the other TextInputRow keys (custom prompts, retention) were rewritten to
        // NavigationRow above, so this screen never needs a plain-String text callback.
        onTextInput = { key, value -> value.toIntOrNull()?.let { onTextInputInt(key, it) } },
        onNavigate = { key ->
            when (key) {
                KEY_CUSTOM_AGENT_PROMPT, KEY_CUSTOM_TEXT_TRANSFORM_PROMPT -> customPromptDialogKey = key
                KEY_RAW_LOG_RETENTION -> retentionDialogOpen = true
                KEY_AI_LANGUAGE -> onEditLanguage()
                else -> onNavigate(key)
            }
        },
        actions = actions,
    )

    customPromptDialogKey?.let { key ->
        val title = (state.items.firstOrNull { it.key == key } as? SettingsItem.TextInputRow)?.title ?: ""
        CustomPromptDialog(
            title = title,
            initialText = customPromptTextFor(key),
            onSave = {
                onCustomPromptSave(key, it.ifBlank { null })
                customPromptDialogKey = null
            },
            onReset = {
                onCustomPromptSave(key, null)
                customPromptDialogKey = null
            },
            onDismiss = { customPromptDialogKey = null },
        )
    }

    if (retentionDialogOpen) {
        val row = state.items.firstOrNull { it.key == KEY_RAW_LOG_RETENTION } as? SettingsItem.TextInputRow
        RetentionDialog(
            title = row?.title ?: "",
            currentDays = row?.value?.toIntOrNull() ?: -1,
            onSave = {
                onTextInputInt(KEY_RAW_LOG_RETENTION, it)
                retentionDialogOpen = false
            },
            onDismiss = { retentionDialogOpen = false },
        )
    }
}

/** Rewrites a special-editor item into a plain [SettingsItem.NavigationRow], keeping its key/title/
 *  summary/visible/enabled so it renders in the same position with the same text. Only ever called
 *  for keys in [SPECIAL_KEYS], which the controller emits as [SettingsItem.TextInputRow] (the two
 *  custom prompts, the retention row) or [SettingsItem.ListChoiceRow] (`ai_language`). */
private fun SettingsItem.asNavigationRow(): SettingsItem.NavigationRow = when (this) {
    is SettingsItem.TextInputRow -> SettingsItem.NavigationRow(key, title, summary, visible, enabled)
    is SettingsItem.ListChoiceRow -> SettingsItem.NavigationRow(key, title, summary, visible, enabled)
    else -> error("AiConnectionSettingsScreen: key '$key' is not a special-editor row type (${this::class})")
}

/**
 * Multiline system-prompt editor: an [OutlinedTextField] prefilled with [initialText] (the host
 * supplies the current custom value, or the built-in default text when unset — see
 * `customPromptTextFor`), a "Reset to default" action, and Save/Cancel. Mirrors the classic
 * `AiConnectionSettingsActivity.showCustomSystemPromptEditor` dialog.
 */
@Composable
private fun CustomPromptDialog(
    title: String,
    initialText: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(strings.okay) } },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(strings.resetToDefault) }
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        },
    )
}

/**
 * Raw-log retention editor: a numeric field plus a "disable" checkbox that greys the field out.
 * Checked → Save sends `-1` (disabled/keep forever); unchecked → Save sends the typed day count
 * (invalid/blank input falls back to 30, matching the classic
 * `AiConnectionSettingsActivity.setupRawLogRetention` dialog).
 */
@Composable
private fun RetentionDialog(
    title: String,
    currentDays: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var disabled by remember(currentDays) { mutableStateOf(currentDays <= 0) }
    var text by remember(currentDays) { mutableStateOf(if (currentDays > 0) currentDays.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !disabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = disabled, onValueChange = { disabled = it }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = disabled, onCheckedChange = { disabled = it })
                    Spacer(Modifier.width(8.dp))
                    Text(strings.rawLogRetentionDisabledLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val days = if (disabled) -1 else (text.toIntOrNull()?.coerceAtLeast(1) ?: 30)
                onSave(days)
            }) { Text(strings.okay) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
