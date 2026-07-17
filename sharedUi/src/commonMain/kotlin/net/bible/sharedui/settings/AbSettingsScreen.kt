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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.components.AbTextInputDialog
import net.bible.sharedui.strings.LocalStrings

/**
 * Reusable declarative settings screen. Renders a [SettingsScreenState] (a flat list of
 * [SettingsItem]s) as Material3 settings rows inside an [AbScaffold], and owns the screen-local
 * dialog state for the two editors it manages ([SettingsItem.ListChoiceRow] → [AbListChoiceDialog],
 * [SettingsItem.TextInputRow] → [AbTextInputDialog]). Every callback is fired with the item's stable
 * `key`, so the consuming host maps a change back to its domain without positional coupling.
 *
 * The framework is deliberately lean: it renders only the generic item types. Screen-specific editors
 * (multiline prompt, retention-with-disable, language pickers, …) are intercepted by the consuming
 * screen BEFORE the items reach this renderer, so they never need special handling here.
 */
@Composable
fun AbSettingsScreen(
    state: SettingsScreenState,
    onUp: (() -> Unit)?,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Screen-local dialog state: the STABLE KEY of the editor row (if any) currently open, not a
    // captured item snapshot. The item itself is re-resolved from state.visibleItems on every
    // recomposition below, so if the async SettingsScreenState changes while the dialog is open
    // (entries/selectedValue/value updated, or the row removed) the dialog always renders the
    // fresh item — and closes itself if the key is no longer present.
    var listChoiceDialogKey by remember { mutableStateOf<String?>(null) }
    var textInputDialogKey by remember { mutableStateOf<String?>(null) }

    AbScaffold(title = state.title, onNavigateUp = onUp, actions = actions) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(state.visibleItems, key = { it.key }) { item ->
                when (item) {
                    is SettingsItem.Category -> CategoryHeader(item.title)

                    is SettingsItem.SwitchRow -> AbSwitchRow(
                        label = item.title,
                        summary = item.summary,
                        checked = item.checked,
                        onCheckedChange = { onSwitch(item.key, it) },
                        enabled = item.enabled,
                    )

                    is SettingsItem.ListChoiceRow -> {
                        val selectedLabel = item.entries.firstOrNull { it.value == item.selectedValue }?.label
                        SettingsRow(
                            title = item.title,
                            summary = selectedLabel ?: item.summary,
                            enabled = item.enabled,
                            onClick = { listChoiceDialogKey = item.key },
                        )
                    }

                    is SettingsItem.TextInputRow -> SettingsRow(
                        title = item.title,
                        summary = item.summary ?: item.value,
                        enabled = item.enabled,
                        onClick = { textInputDialogKey = item.key },
                    )

                    is SettingsItem.NavigationRow -> SettingsRow(
                        title = item.title,
                        summary = item.summary,
                        enabled = item.enabled,
                        onClick = { onNavigate(item.key) },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                    )

                    // InfoRow is non-interactive (no clickable, no ripple): a plain title + summary.
                    is SettingsItem.InfoRow -> Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        if (item.summary != null) {
                            Text(
                                item.summary!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Re-resolve against the CURRENT state.visibleItems on every recomposition (never render from
    // the click-time snapshot): if the key has disappeared (item removed/hidden), the dialog closes
    // itself; otherwise it renders from the fresh item, so an async state update that changes
    // entries/selectedValue/value while the dialog is open is reflected immediately.
    val listChoiceRow = listChoiceDialogKey?.let { key ->
        state.visibleItems.firstOrNull { it.key == key } as? SettingsItem.ListChoiceRow
    }
    LaunchedEffect(listChoiceDialogKey, listChoiceRow) {
        if (listChoiceDialogKey != null && listChoiceRow == null) {
            listChoiceDialogKey = null
        }
    }
    listChoiceRow?.let { row ->
        AbListChoiceDialog(
            title = row.title,
            choices = row.entries,
            selectedValue = row.selectedValue,
            onSelect = { onListChoice(row.key, it) },
            onDismiss = { listChoiceDialogKey = null },
        )
    }

    val textInputRow = textInputDialogKey?.let { key ->
        state.visibleItems.firstOrNull { it.key == key } as? SettingsItem.TextInputRow
    }
    LaunchedEffect(textInputDialogKey, textInputRow) {
        if (textInputDialogKey != null && textInputRow == null) {
            textInputDialogKey = null
        }
    }
    textInputRow?.let { row ->
        val strings = LocalStrings.current
        AbTextInputDialog(
            title = row.title,
            initial = row.value,
            confirmText = strings.okay,
            dismissText = strings.cancel,
            numeric = row.numeric,
            onConfirm = {
                onTextInput(row.key, it)
                textInputDialogKey = null
            },
            onDismiss = { textInputDialogKey = null },
        )
    }
}

/** M3 settings section label: small, coloured with the primary accent. */
@Composable
private fun CategoryHeader(title: String) = Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

/**
 * A generic clickable settings row (title + optional summary + optional trailing content), used for
 * the list-choice, text-input and navigation item types. Disabled rows dim and stop responding to
 * clicks. [TwoLineListItem] isn't reused here because these rows may have a single line (no summary)
 * and an optional trailing slot.
 */
@Composable
private fun SettingsRow(
    title: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .rowEnabled(enabled)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/** Dim a row when disabled (matches the classic preference-screen greyed-out affordance). */
private fun Modifier.rowEnabled(enabled: Boolean): Modifier =
    if (enabled) this else this.then(Modifier.alpha(DISABLED_ALPHA))

private const val DISABLED_ALPHA = 0.38f
