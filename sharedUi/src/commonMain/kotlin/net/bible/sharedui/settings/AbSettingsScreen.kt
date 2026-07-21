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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSliderRow
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
 *
 * A thin wrapper around [AbSettingsContent]: this is just [AbScaffold] (title + top app bar) plus
 * that content. Use [AbSettingsContent] directly when embedding the settings list inside a host that
 * already renders its own top bar (e.g. a tab body) — see its kdoc.
 */
@Composable
fun AbSettingsScreen(
    state: SettingsScreenState,
    onUp: (() -> Unit)?,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    onSliderChange: (String, Int) -> Unit = { _, _ -> },
    onMultiSelectChange: (String, Set<String>) -> Unit = { _, _ -> },
    actions: @Composable RowScope.() -> Unit = {},
) {
    AbScaffold(title = state.title, onNavigateUp = onUp, actions = actions) { padding ->
        AbSettingsContent(
            state = state,
            onSwitch = onSwitch,
            onListChoice = onListChoice,
            onTextInput = onTextInput,
            onNavigate = onNavigate,
            onSliderChange = onSliderChange,
            onMultiSelectChange = onMultiSelectChange,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * Scaffold-less counterpart of [AbSettingsScreen]: renders the same [SettingsScreenState] settings
 * list (and owns the same screen-local list-choice/text-input dialog state) WITHOUT wrapping it in
 * an [AbScaffold] — i.e. no top app bar. Intended for hosts that already render their own top bar
 * (e.g. a screen with tabs, where this is one tab's body) and would otherwise get a redundant, near
 * empty second app bar from [AbSettingsScreen]. [AbSettingsScreen] itself now delegates to this
 * composable, so the two stay behaviourally identical for the shared rendering logic.
 */
@Composable
fun AbSettingsContent(
    state: SettingsScreenState,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    onSliderChange: (String, Int) -> Unit = { _, _ -> },
    onMultiSelectChange: (String, Set<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // Screen-local dialog state: the STABLE KEY of the editor row (if any) currently open, not a
    // captured item snapshot. The item itself is re-resolved from state.visibleItems on every
    // recomposition below, so if the async SettingsScreenState changes while the dialog is open
    // (entries/selectedValue/value updated, or the row removed) the dialog always renders the
    // fresh item — and closes itself if the key is no longer present.
    var listChoiceDialogKey by remember { mutableStateOf<String?>(null) }
    var textInputDialogKey by remember { mutableStateOf<String?>(null) }
    var multiSelectDialogKey by remember { mutableStateOf<String?>(null) }

    // fillMaxSize() FIRST, caller's modifier (e.g. AbSettingsScreen's scaffold padding) applied
    // after — matches the original inline `Modifier.fillMaxSize().padding(padding)` chain exactly,
    // so AbSettingsScreen's delegation below is behaviour-preserving (order matters for layout).
    LazyColumn(modifier = Modifier.fillMaxSize().then(modifier)) {
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
                        iconKey = item.iconKey,
                    )
                }

                is SettingsItem.TextInputRow -> SettingsRow(
                    title = item.title,
                    summary = item.summary ?: item.value,
                    enabled = item.enabled,
                    onClick = { textInputDialogKey = item.key },
                    iconKey = item.iconKey,
                )

                is SettingsItem.SliderRow -> AbSliderRow(
                    label = item.title,
                    value = item.value,
                    // Persists once per drag gesture (AbSliderRow calls this on release only).
                    onValueChange = { onSliderChange(item.key, it) },
                    valueRange = item.min.toFloat()..item.max.toFloat(),
                    valueLabel = item.valueLabel,
                    valueLabelFor = item.valueFormat?.let { fmt ->
                        { v -> fmt.replace("%d", v.toString()).replace("%%", "%") }
                    },
                )

                is SettingsItem.MultiSelectRow -> SettingsRow(
                    title = item.title,
                    summary = item.summary,
                    enabled = item.enabled,
                    onClick = { multiSelectDialogKey = item.key },
                    iconKey = item.iconKey,
                )

                is SettingsItem.NavigationRow -> SettingsRow(
                    title = item.title,
                    summary = item.summary,
                    enabled = item.enabled,
                    onClick = { onNavigate(item.key) },
                    iconKey = item.iconKey,
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )

                // InfoRow is non-interactive by default (no clickable, no ripple) — a plain title +
                // summary, optionally with a leading icon. When onClickKey is set, the row becomes
                // clickable and fires the SAME onNavigate callback NavigationRow uses, passing
                // onClickKey (not item.key) — e.g. to open an info/disclaimer dialog by that key.
                is SettingsItem.InfoRow -> {
                    val iconPainter = item.iconKey?.let { LocalSettingsIcon.current(it) }
                    val onClickKey = item.onClickKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (onClickKey != null) {
                                    Modifier.clickable(onClick = { onNavigate(onClickKey) })
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (iconPainter != null) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                        }
                        Column(Modifier.weight(1f)) {
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
            masked = row.masked,
            onConfirm = {
                onTextInput(row.key, it)
                textInputDialogKey = null
            },
            onDismiss = { textInputDialogKey = null },
        )
    }

    val multiSelectRow = multiSelectDialogKey?.let { key ->
        state.visibleItems.firstOrNull { it.key == key } as? SettingsItem.MultiSelectRow
    }
    LaunchedEffect(multiSelectDialogKey, multiSelectRow) {
        if (multiSelectDialogKey != null && multiSelectRow == null) {
            multiSelectDialogKey = null
        }
    }
    multiSelectRow?.let { row ->
        val strings = LocalStrings.current
        AbMultiSelectDialog(
            title = row.title,
            options = row.options,
            selectedIds = row.selectedValues.toList(),
            idOf = { it.value },
            labelOf = { it.label },
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { onMultiSelectChange(row.key, it.toSet()); multiSelectDialogKey = null },
            onDismiss = { multiSelectDialogKey = null },
            selectAllText = strings.selectAll,
            selectNoneText = strings.selectNone,
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
 * A generic clickable settings row (title + optional summary + optional leading icon + optional
 * trailing content), used for the list-choice, text-input and navigation item types. Disabled rows
 * dim and stop responding to clicks. [TwoLineListItem] isn't reused here because these rows may have
 * a single line (no summary) and an optional trailing slot.
 *
 * [iconKey] defaults to `null` (no icon): [SettingsItem.NavigationRow], [SettingsItem.ListChoiceRow] and
 * [SettingsItem.TextInputRow] all carry an optional `iconKey`, resolved here via [LocalSettingsIcon].
 * [SettingsItem.SwitchRow] also has an `iconKey` field, but [net.bible.sharedui.components.AbSwitchRow]
 * has no leading-icon slot yet, so a switch row's `iconKey` (if any) is currently ignored — F29 left this
 * as a follow-up rather than adding an icon slot to that shared component (used beyond settings screens).
 */
@Composable
private fun SettingsRow(
    title: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    iconKey: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val iconPainter = iconKey?.let { LocalSettingsIcon.current(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .rowEnabled(enabled)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
