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

package net.bible.sharedui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbListChoiceContent
import net.bible.sharedui.components.AbMultiSelectContent
import net.bible.sharedui.components.AbTextInputContent
import net.bible.sharedui.strings.LocalStrings

/**
 * The settings editor sheet shell. Renders nothing when [page] is null, so a host can render it
 * unconditionally — the same self-hiding contract `SearchSettingsSheet` uses, which is what lets the
 * reading view keep it as an unconditional sibling overlay.
 *
 * [onDismiss] is wired to the sheet's single `onDismissRequest`. Material3 gives one callback for
 * back, scrim tap and swipe-down and they cannot be told apart, and `BackHandler` cannot live in
 * commonMain — so a host maps it to `SettingsEditorStack.pop()`, which steps back one page and
 * closes at depth 1. That is deliberate: DO NOT "fix" it into a whole-sheet dismiss, or the page
 * stack becomes unreachable by back. [onClose] is the header ✕, which always closes outright.
 *
 * ROBORAZZI: never capture this composable with a non-null [page]. Forcing a `ModalBottomSheet` open
 * in a capture hangs Roborazzi and takes the whole `:app` suite with it. Golden the page's `*Content`
 * composable directly inside a plain `Column` instead — see `SettingsEditorSheetGoldenTest`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEditorSheet(
    page: SettingsEditorPage?,
    title: String,
    showBack: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (page == null) return
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.settingsEditorBack)
                    }
                } else {
                    Spacer(Modifier.padding(horizontal = 12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = strings.settingsEditorClose)
                }
            }
            content()
        }
    }
}

/** A sheet page's confirm/cancel pair, right-aligned — the sheet analogue of AlertDialog's button row. */
@Composable
fun SheetConfirmRow(
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(cancelLabel) }
        TextButton(onClick = onConfirm) { Text(confirmLabel) }
    }
}

/**
 * The generic settings editor sheet: the three item kinds `AbSettingsContent` used to open dialogs
 * for. Re-resolves the row from [state] on every recomposition and closes the sheet if the key has
 * vanished — the same discipline the three dialogs had, now expressed through
 * [SettingsEditorStack.closeIf].
 *
 * `internal`, not `private`: [AbSettingsScreen] is not [AbSettingsContent]'s only direct caller —
 * [net.bible.sharedui.settings.SyncSettingsScreen] and [net.bible.sharedui.ai.PromptEditScreen]'s
 * Advanced tab also render it standalone (a host with its own top bar, per [AbSettingsContent]'s
 * kdoc), so they reuse this composable rather than duplicating its `when` block. Kotlin `internal`
 * is module-scoped, which covers both — they live in `:sharedUi` alongside this file. Lives here,
 * not in `AbSettingsScreen.kt`, for the same module-shared-infrastructure reason as
 * [SettingsEditorSheet]/[SheetConfirmRow] above.
 */
@Composable
internal fun GenericSettingsEditorSheet(
    state: SettingsScreenState,
    editor: SettingsEditorStack,
    page: SettingsEditorPage?,
    depth: Int,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onMultiSelectChange: (String, Set<String>) -> Unit,
) {
    val rowPage = page as? SettingsEditorPage.Row ?: return
    val row = state.visibleItems.firstOrNull { it.key == rowPage.key }
    LaunchedEffect(rowPage, row) {
        if (row == null) editor.closeIf { it is SettingsEditorPage.Row && it.key == rowPage.key }
    }
    if (row == null) return
    val strings = LocalStrings.current
    val title = when (row) {
        is SettingsItem.ListChoiceRow -> row.title
        is SettingsItem.TextInputRow -> row.title
        is SettingsItem.MultiSelectRow -> row.title
        // RenderSettingsItem only ever calls openListChoice/openTextInput/openMultiSelect for these
        // three row kinds, so a Row page can never resolve to any other SettingsItem subtype today.
        // Not exhaustively provable from this function alone, so kept as a safe no-op (a blank sheet
        // never renders — SettingsEditorSheet only renders once `page` is non-null AND this function
        // has returned a title) rather than an assertion that would crash if that ever changed.
        else -> return
    }
    SettingsEditorSheet(
        page = page,
        title = title,
        showBack = depth > 1,
        onDismiss = { editor.pop() },
        onClose = { editor.close() },
    ) {
        when (row) {
            is SettingsItem.ListChoiceRow -> {
                // AbListChoiceContent's own modifier parameter lands INSIDE its `verticalScroll`
                // (`Modifier.verticalScroll(rememberScrollState()).then(modifier)`), so a height bound
                // passed there is measured by verticalScroll's child with maxHeight = Infinity, clamps
                // the inner Column's reported size only, and never reduces verticalScroll's own
                // viewport — scroll range collapses to 0 and rows past the bound become unreachable
                // rather than merely scroll-capped. Bounding from this true ANCESTOR Box instead makes
                // verticalScroll receive the finite maxHeight it needs to compute a real scroll range.
                // Matches AbMultiSelectContent's 400.dp — no reason for the sheet's two list editors to
                // clip at different heights.
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    AbListChoiceContent(
                        choices = row.entries,
                        selectedValue = row.selectedValue,
                        // A single-choice pick commits and closes, exactly as the dialog's row onClick did.
                        onSelect = { onListChoice(row.key, it); editor.pop() },
                    )
                }
            }
            is SettingsItem.TextInputRow -> {
                var current by remember(row.key, row.value) { mutableStateOf(row.value) }
                AbTextInputContent(
                    initial = row.value,
                    onValueChange = { current = it },
                    numeric = row.numeric,
                    masked = row.masked,
                )
                SheetConfirmRow(
                    confirmLabel = strings.settingsEditorApply,
                    cancelLabel = strings.cancel,
                    onConfirm = { onTextInput(row.key, current); editor.pop() },
                    onCancel = { editor.pop() },
                )
            }
            is SettingsItem.MultiSelectRow -> {
                var current by remember(row.key, row.selectedValues) {
                    mutableStateOf(row.selectedValues.toList())
                }
                AbMultiSelectContent(
                    options = row.options,
                    selectedIds = row.selectedValues.toList(),
                    idOf = { it.value },
                    labelOf = { it.label },
                    onCheckedChange = { current = it },
                    selectAllText = strings.selectAll,
                    selectNoneText = strings.selectNone,
                )
                SheetConfirmRow(
                    confirmLabel = strings.settingsEditorApply,
                    cancelLabel = strings.cancel,
                    onConfirm = { onMultiSelectChange(row.key, current.toSet()); editor.pop() },
                    onCancel = { editor.pop() },
                )
            }
            else -> Unit
        }
    }
}
