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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.aiLanguageSelection
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbListChoiceContent
import net.bible.sharedui.components.AbTextInputContent
import net.bible.sharedui.settings.AbSettingsScreen
import net.bible.sharedui.settings.SettingsEditorSheet
import net.bible.sharedui.settings.SheetConfirmRow
import net.bible.sharedui.strings.LocalStrings

/** Stable keys this screen intercepts BEFORE they reach [AbSettingsScreen]'s generic renderer. */
private const val KEY_CUSTOM_AGENT_PROMPT = "custom_agent_prompt"
private const val KEY_CUSTOM_TEXT_TRANSFORM_PROMPT = "custom_text_transform_prompt"
private const val KEY_RAW_LOG_RETENTION = "raw_log_retention"
private const val KEY_AI_LANGUAGE = "ai_language"

/** [SettingsItem.InfoRow.onClickKey] the controller sets on the disclaimer row (F28); clicking it
 *  opens [AbInfoDialog] with the full disclaimer text rather than being forwarded to [onNavigate]. */
private const val KEY_DISCLAIMER = "ai_disclaimer_warning"

/** A synthetic page key: the "Custom language…" text-input page has no settings row of its own —
 *  it is the second step of the [KEY_AI_LANGUAGE] picker. */
private const val CUSTOM_LANGUAGE_PAGE_KEY = "ai_language_custom"

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
 *   row; the click opens a [SettingsEditorSheet] picker page, populated from the host-supplied
 *   [languageChoices] (F32 — replaces the classic `AlertDialog` locale picker; migrated to a sheet
 *   page in Task 7). Picking the [customLanguageValue] sentinel entry `push`es a second sheet page
 *   (keyed by the synthetic [CUSTOM_LANGUAGE_PAGE_KEY]) for a free-form language name/code, instead
 *   of opening a second dialog on top of the first — cancelling that second step pops back to the
 *   picker rather than leaving both open. Both pages report the chosen value back through the
 *   existing [onListChoice] callback (the controller's `onListChoice("ai_language", value)`
 *   already persists it via [net.bible.sharedcore.ai.AiSettingsService.setAiLanguage] — no new
 *   controller callback needed).
 *
 * **Interception approach.** Rather than removing the four items from the state (which would lose
 * their position in the list) or re-implementing the whole row-rendering switch here, this screen
 * rewrites just those four items into `NavigationRow`s with the same `key`/`title`/`summary`/
 * `visible`/`enabled` — so [AbSettingsScreen] draws them as ordinary clickable rows (chevron
 * affordance, correct position) but never opens ITS generic list-choice/text-input dialog for them.
 * All navigation clicks funnel through one `onNavigate` lambda, which this screen overrides: the
 * four special keys open a local dialog or sheet; every other key (including
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
    /** Host-resolved `(value, label)` options for the AI-language picker (Android locale-array
     *  data — read in the `:app` host, never touched here). Must include an entry whose `value`
     *  equals [customLanguageValue] (the "Custom…" row); every other entry is a real language
     *  option, `""` conventionally meaning "app default" (see [net.bible.sharedcore.ai.AiSettingsService]). */
    languageChoices: List<SettingsItem.Choice>,
    /** Sentinel [SettingsItem.Choice.value] identifying the "Custom…" row in [languageChoices]; picking
     *  it pushes the custom-language sheet page instead of committing the sentinel itself as the
     *  language. */
    customLanguageValue: String,
    onNavigate: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    /** Test-only seam (matches `initiallyXxxOpen` elsewhere, e.g. `SearchScreen`'s
     *  `initiallySettingsOpen`): lets golden tests capture the disclaimer [AbInfoDialog] open
     *  without a click-simulation harness (this module has no Compose UI-test dependency). */
    initiallyDisclaimerDialogOpen: Boolean = false,
    /** Test-only seam: capture the [CustomPromptDialog] (agent system prompt) open, e.g. with a
     *  long [customPromptTextFor] result, to verify its height stays bounded (F36). */
    initiallyCustomPromptDialogOpen: Boolean = false,
) {
    val displayState = remember(state) {
        state.copy(items = state.items.map { item -> if (item.key in SPECIAL_KEYS) item.asNavigationRow() else item })
    }

    var customPromptDialogKey by remember {
        mutableStateOf(if (initiallyCustomPromptDialogOpen) KEY_CUSTOM_AGENT_PROMPT else null)
    }
    var retentionDialogOpen by remember { mutableStateOf(false) }
    var disclaimerDialogOpen by remember { mutableStateOf(initiallyDisclaimerDialogOpen) }

    // The AI-language picker + its "Custom…" second step (Task 7): a two-page SettingsEditorStack
    // dedicated to this screen's own ai_language flow, distinct from AbSettingsScreen's own
    // generic editor stack — see the invariant comment below the two-page sheet render block for
    // why one screen safely owns two SettingsEditorSheet call sites.
    val editor = remember { SettingsEditorStack() }
    val editorPages by editor.pages.collectAsState()

    // Defensive parity with AbSettingsScreen's own dialog-state handling: if the async state stops
    // carrying a key while its dialog is open (item removed outright), close the dialog rather than
    // showing stale content next recomposition.
    LaunchedEffect(customPromptDialogKey, state) {
        val key = customPromptDialogKey
        if (key != null && state.visibleItems.none { it.key == key }) {
            customPromptDialogKey = null
        }
    }
    LaunchedEffect(retentionDialogOpen, state) {
        if (retentionDialogOpen && state.visibleItems.none { it.key == KEY_RAW_LOG_RETENTION }) {
            retentionDialogOpen = false
        }
    }
    // Same discipline, expressed via SettingsEditorStack.closeIf: if the ai_language row vanishes
    // while either of its two pages (picker or custom-language) is open, close the WHOLE stack —
    // the custom-language page was reached through the picker, so leaving it open would strand the
    // user on a page whose parent row no longer exists.
    LaunchedEffect(editorPages, state) {
        editor.closeIf { state.visibleItems.none { item -> item.key == KEY_AI_LANGUAGE } }
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
                KEY_DISCLAIMER -> disclaimerDialogOpen = true
                KEY_CUSTOM_AGENT_PROMPT, KEY_CUSTOM_TEXT_TRANSFORM_PROMPT -> customPromptDialogKey = key
                KEY_RAW_LOG_RETENTION -> retentionDialogOpen = true
                KEY_AI_LANGUAGE -> editor.open(SettingsEditorPage.Row(KEY_AI_LANGUAGE))
                else -> onNavigate(key)
            }
        },
        actions = actions,
    )

    customPromptDialogKey?.let { key ->
        val title = (state.visibleItems.firstOrNull { it.key == key } as? SettingsItem.TextInputRow)?.title ?: ""
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
        val row = state.visibleItems.firstOrNull { it.key == KEY_RAW_LOG_RETENTION } as? SettingsItem.TextInputRow
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

    if (disclaimerDialogOpen) {
        val strings = LocalStrings.current
        AbInfoDialog(
            title = strings.aiDisclaimerDialogTitle,
            body = strings.aiDisclaimerBody,
            onDismiss = { disclaimerDialogOpen = false },
        )
    }

    // F32: AI-language picker. `currentAiLanguage` comes from the ORIGINAL (un-rewritten) `state`
    // — the ai_language item in `displayState` was already rewritten to a NavigationRow above and
    // no longer carries `selectedValue`. The picker-selected / custom-page-prefill logic is pulled
    // out into the pure aiLanguageSelection() (Task 7 fix round 1) so a unit test can reach it —
    // the sheet page itself cannot be golden-captured (an open ModalBottomSheet hangs Roborazzi).
    val aiLanguageRow = state.items.firstOrNull { it.key == KEY_AI_LANGUAGE } as? SettingsItem.ListChoiceRow
    val currentAiLanguage = aiLanguageRow?.selectedValue ?: ""
    val languageSelection = aiLanguageSelection(currentAiLanguage, languageChoices, customLanguageValue)

    // Two SettingsEditorSheet call sites live in this composable's subtree: AbSettingsScreen's own
    // generic sheet (rendered inside AbSettingsScreen above, for the three generic row kinds it
    // opens editors for) and this screen's own `editor`'s sheet below (for the ai_language
    // picker/custom-language page). This is safe ONLY because SettingsEditorSheet composes nothing
    // while its `page` is null, and a single row tap can populate at most one of the two stacks:
    // AbSettingsContent's `onOpenEditor` fires for ListChoiceRow/TextInputRow/MultiSelectRow rows,
    // none of which is ai_language (rewritten to a NavigationRow above, so AbSettingsContent never
    // sees it as a ListChoiceRow at all) — while ai_language's own click is intercepted by THIS
    // screen's `onNavigate` override, above, before it ever reaches AbSettingsScreen's generic
    // renderer. So the two sheets can never both be open at once. A future row able to feed both
    // stacks from one tap (e.g. a special key AbSettingsScreen's generic renderer ALSO treats as
    // list-choice/text-input) would break this invariant and would need an explicit guard.
    val page = editorPages.lastOrNull() as? SettingsEditorPage.Row
    if (page != null) {
        SettingsEditorSheet(
            page = page,
            title = aiLanguageRow?.title ?: "",
            showBack = editorPages.size > 1,
            onDismiss = { editor.pop() },
            onClose = { editor.close() },
        ) {
            if (page.key == CUSTOM_LANGUAGE_PAGE_KEY) {
                val strings = LocalStrings.current
                var current by remember { mutableStateOf(languageSelection.customLanguageInitial) }
                AbTextInputContent(
                    initial = languageSelection.customLanguageInitial,
                    onValueChange = { current = it },
                )
                Text(strings.aiLanguageCustomHint, style = MaterialTheme.typography.bodySmall)
                SheetConfirmRow(
                    confirmLabel = strings.okay,
                    cancelLabel = strings.cancel,
                    onConfirm = { onListChoice(KEY_AI_LANGUAGE, current.trim()); editor.close() },
                    // Cancelling the second step returns to the picker (pop), not close: the old
                    // nested AlertDialogs left both open on cancel, which is the bug this two-page
                    // sheet fixes — see the class-level KDoc's KEY_AI_LANGUAGE bullet.
                    onCancel = { editor.pop() },
                )
            } else {
                // languageChoices may list every locale the host offers; bound the viewport from
                // this ANCESTOR Box, never from AbListChoiceContent's own `modifier` param — that
                // lands inside its `verticalScroll` and collapses the scroll range to 0 instead of
                // bounding it (see that composable's KDoc). Matches GenericSettingsEditorSheet's
                // identical Box(heightIn(max = 400.dp)) wrap for its own list-choice page.
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    AbListChoiceContent(
                        choices = languageChoices,
                        selectedValue = languageSelection.pickerSelectedValue,
                        onSelect = { value ->
                            // "Custom…" pushes the text-input page onto THIS sheet instead of
                            // opening a second dialog on top of the first (the old F32 behaviour).
                            if (value == customLanguageValue) {
                                editor.push(SettingsEditorPage.Row(CUSTOM_LANGUAGE_PAGE_KEY))
                            } else {
                                onListChoice(KEY_AI_LANGUAGE, value)
                                editor.close()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Rewrites a special-editor item into a plain [SettingsItem.NavigationRow], keeping its key/title/
 *  summary/visible/enabled so it renders in the same position with the same text. Only ever called
 *  for keys in [SPECIAL_KEYS], which the controller emits as [SettingsItem.TextInputRow] (the two
 *  custom prompts, the retention row) or [SettingsItem.ListChoiceRow] (`ai_language`). */
private fun SettingsItem.asNavigationRow(): SettingsItem.NavigationRow = when (this) {
    is SettingsItem.TextInputRow ->
        SettingsItem.NavigationRow(
            key = key, title = title, summary = summary, iconKey = iconKey, visible = visible, enabled = enabled,
        )
    is SettingsItem.ListChoiceRow ->
        SettingsItem.NavigationRow(
            key = key, title = title, summary = summary, iconKey = iconKey, visible = visible, enabled = enabled,
        )
    else -> error("AiConnectionSettingsScreen: key '$key' is not a special-editor row type (${this::class})")
}

/**
 * Multiline system-prompt editor: an [OutlinedTextField] prefilled with [initialText] (the host
 * supplies the current custom value, or the built-in default text when unset — see
 * `customPromptTextFor`), a "Reset to default" action, and Save/Cancel. Mirrors the classic
 * `AiConnectionSettingsActivity.showCustomSystemPromptEditor` dialog.
 *
 * F36: the text field is height-bounded (`heightIn(max = 320.dp)`) rather than growing without
 * limit — a long default/custom prompt was stretching the whole dialog to full screen height.
 * `OutlinedTextField`/`BasicTextField` scrolls its own content internally once its constrained
 * height is smaller than the text needs, so a long prompt scrolls within the field instead of
 * growing the dialog further.
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
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
