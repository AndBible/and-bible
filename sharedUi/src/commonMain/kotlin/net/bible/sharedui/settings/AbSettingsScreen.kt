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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedcore.settings.filterSettingsItems
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchImeRequest
import net.bible.sharedui.components.AbSettingsCategoryHeader
import net.bible.sharedui.components.AbSettingsRow
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState

/**
 * Reusable declarative settings screen. Renders a [SettingsScreenState] (a flat list of
 * [SettingsItem]s) as Material3 settings rows inside an [AbScaffold]. Every callback is fired with
 * the item's stable `key`, so the consuming host maps a change back to its domain without
 * positional coupling.
 *
 * Owns a [SettingsEditorStack] and renders the one [SettingsEditorSheet] it drives (via
 * [GenericSettingsEditorSheet]) as a sibling of the [AbScaffold] block, in both the searchable and
 * non-searchable branches: [SettingsItem.ListChoiceRow], [SettingsItem.TextInputRow] and
 * [SettingsItem.MultiSelectRow] all open as sheet pages now, replacing the three dialogs this
 * screen used to own directly.
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
    searchable: Boolean = false,
    searchHint: String = "",
    searchQuery: String = "",
    searchModeActive: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onLongPress: ((String) -> Unit)? = null,
) {
    val editor = remember { SettingsEditorStack() }
    val editorPages by editor.pages.collectAsState()
    val editorPage = editorPages.lastOrNull()
    val onOpenEditor: (String) -> Unit = { key -> editor.open(SettingsEditorPage.Row(key)) }

    if (!searchable) {
        // Unchanged legacy path: pixel-identical to the pre-search behaviour (every non-searchable
        // screen — Sync, AI, ReadingProgress — stays exactly as before).
        AbScaffold(title = state.title, onNavigateUp = onUp, actions = actions) { padding ->
            AbSettingsContent(
                state = state,
                onSwitch = onSwitch,
                onListChoice = onListChoice,
                onTextInput = onTextInput,
                onNavigate = onNavigate,
                onOpenEditor = onOpenEditor,
                onSliderChange = onSliderChange,
                onMultiSelectChange = onMultiSelectChange,
                modifier = Modifier.padding(padding),
                onLongPress = onLongPress,
            )
        }
        GenericSettingsEditorSheet(
            state = state,
            editor = editor,
            page = editorPage,
            depth = editor.depth,
            onListChoice = onListChoice,
            onTextInput = onTextInput,
            onMultiSelectChange = onMultiSelectChange,
        )
        return
    }

    // Filter against the already-visibility-filtered items so hidden rows never surface via search.
    val filteredState =
        if (searchQuery.isNotBlank()) state.copy(items = filterSettingsItems(state.visibleItems, searchQuery))
        else state

    val topActions: @Composable RowScope.() -> Unit = {
        AbActionIcon(Icons.Filled.Search, searchHint, onOpenSearch)
        actions()
    }

    AbScaffold(
        title = state.title,
        onNavigateUp = onUp,
        actions = topActions,
        search = if (searchModeActive) {
            AbTopBarSearchState(query = searchQuery, imeRequest = AbSearchImeRequest.Focus, placeholder = searchHint)
        } else null,
        searchCallbacks = if (searchModeActive) {
            AbTopBarSearchCallbacks(
                onQueryChange = onSearchQueryChange,
                onClose = onCloseSearch,
                onImeRequestHandled = {},
            )
        } else null,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AbSettingsContent(
                state = filteredState,
                onSwitch = onSwitch,
                onListChoice = onListChoice,
                onTextInput = onTextInput,
                onNavigate = onNavigate,
                onOpenEditor = onOpenEditor,
                onSliderChange = onSliderChange,
                onMultiSelectChange = onMultiSelectChange,
                modifier = Modifier.weight(1f),   // ColumnScope: list fills the space below the field
                onLongPress = onLongPress,
            )
        }
    }
    GenericSettingsEditorSheet(
        state = filteredState,
        editor = editor,
        page = editorPage,
        depth = editor.depth,
        onListChoice = onListChoice,
        onTextInput = onTextInput,
        onMultiSelectChange = onMultiSelectChange,
    )
}

/**
 * Scaffold-less counterpart of [AbSettingsScreen]: renders the same [SettingsScreenState] settings
 * list WITHOUT wrapping it in an [AbScaffold] — i.e. no top app bar. [onOpenEditor] is called with
 * the row's key for all three of [SettingsItem.ListChoiceRow], [SettingsItem.TextInputRow] and
 * [SettingsItem.MultiSelectRow]; the caller (now [AbSettingsScreen], which owns the
 * [SettingsEditorStack]) opens a [SettingsEditorSheet] page for it and later commits the edit via
 * [onListChoice]/[onTextInput]/[onMultiSelectChange], which remain in this signature for that reason
 * even though this composable no longer calls them itself. Intended for hosts that already render
 * their own top bar (e.g. a screen with tabs, where this is one tab's body) and would otherwise get
 * a redundant, near empty second app bar from [AbSettingsScreen]. [AbSettingsScreen] itself now
 * delegates to this composable, so the two stay behaviourally identical for the shared rendering
 * logic.
 */
@Composable
fun AbSettingsContent(
    state: SettingsScreenState,
    onSwitch: (String, Boolean) -> Unit,
    onListChoice: (String, String) -> Unit,
    onTextInput: (String, String) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    onSliderChange: (String, Int) -> Unit = { _, _ -> },
    onMultiSelectChange: (String, Set<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onLongPress: ((String) -> Unit)? = null,
) {
    // fillMaxSize() FIRST, caller's modifier (e.g. AbSettingsScreen's scaffold padding) applied
    // after — matches the original inline `Modifier.fillMaxSize().padding(padding)` chain exactly,
    // so AbSettingsScreen's delegation below is behaviour-preserving (order matters for layout).
    LazyColumn(modifier = Modifier.fillMaxSize().then(modifier)) {
        items(state.visibleItems, key = { it.key }) { item ->
            // A/B batch 4a F2: the badge is a row parameter now, not a Box overlay drawn on top of
            // the row (which covered the summary and the switch). `null` keeps every screen that
            // does not provide LocalSettingsRowBadge on exactly the path it had before.
            RenderSettingsItem(
                item = item,
                badge = LocalSettingsRowBadge.current(item.key),
                onSwitch = onSwitch,
                onNavigate = onNavigate,
                onSliderChange = onSliderChange,
                onLongPress = onLongPress,
                openListChoice = onOpenEditor,
                openTextInput = onOpenEditor,
                openMultiSelect = onOpenEditor,
            )
        }
    }
}

/**
 * Renders one [SettingsItem] row — mechanically extracted from [AbSettingsContent]'s per-item
 * `when` (Batch 12d-A Task 3). The render logic itself is UNCHANGED from before the extraction:
 * [onLongPress] threads into every row that goes through [AbSwitchRow]/[SettingsRow] (the row types
 * those two support: switch/list-choice/text-input/multi-select/navigation); when it's null (every
 * existing settings screen) those two composables' null-branches render byte-identical to their
 * pre-extension bodies. [SettingsItem.SliderRow] (drag gesture) and [SettingsItem.InfoRow]
 * (non-interactive by default) are left unwired, matching the brief — neither is a long-press
 * target.
 *
 * [badge] (A/B batch 4a F2) is **not** defaulted, unlike [onLongPress] — every call site of
 * [RenderSettingsItem] itself must state a badge explicitly (`null` for "no badge"), so a future
 * caller cannot silently forget to decide one. It threads into [AbSwitchRow]/[SettingsRow]'s own
 * `badge` parameter, which renders it INSIDE the row's text column, not as a `Box` overlay drawn on
 * top of the row — that covers [SettingsItem.SwitchRow]/[ListChoiceRow]/[TextInputRow]/
 * [MultiSelectRow]/[NavigationRow], the five item types with a real persisted, overridable value.
 *
 * (Corrected A/B batch 4a whole-batch review M2 — this kdoc previously claimed the parameter
 * being non-defaulted meant "a future item type added to the `when` cannot silently drop it by
 * omission", which the two branches below already contradicted.) [SettingsItem.Category] and
 * [SettingsItem.InfoRow] ignore [badge]: neither is a real overridable *setting* (a header and a
 * non-interactive display row, respectively), so there is no inherited value to badge.
 * [SettingsItem.SliderRow] also ignores it — not for the same reason (a slider genuinely IS an
 * overridable setting), but because [AbSliderRow] has no `badge` parameter of its own to thread it
 * into; no current slider-backed setting needs one. Adding one is straightforward if that changes,
 * but is out of scope here.
 */
@Composable
private fun RenderSettingsItem(
    item: SettingsItem,
    badge: String?,
    onSwitch: (String, Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onSliderChange: (String, Int) -> Unit,
    onLongPress: ((String) -> Unit)?,
    openListChoice: (String) -> Unit,
    openTextInput: (String) -> Unit,
    openMultiSelect: (String) -> Unit,
) {
    when (item) {
        is SettingsItem.Category -> AbSettingsCategoryHeader(item.title)

        is SettingsItem.SwitchRow -> AbSwitchRow(
            label = item.title,
            summary = item.summary,
            checked = item.checked,
            onCheckedChange = { onSwitch(item.key, it) },
            enabled = item.enabled,
            onLongClick = onLongPress?.let { press -> { press(item.key) } },
            iconKey = item.iconKey,
            badge = badge,
        )

        is SettingsItem.ListChoiceRow -> {
            val selectedLabel = item.entries.firstOrNull { it.value == item.selectedValue }?.label
            AbSettingsRow(
                title = item.title,
                summary = selectedLabel ?: item.summary,
                enabled = item.enabled,
                onClick = { openListChoice(item.key) },
                iconKey = item.iconKey,
                onLongClick = onLongPress?.let { press -> { press(item.key) } },
                badge = badge,
            )
        }

        is SettingsItem.TextInputRow -> AbSettingsRow(
            title = item.title,
            summary = item.summary ?: item.value,
            enabled = item.enabled,
            onClick = { openTextInput(item.key) },
            iconKey = item.iconKey,
            onLongClick = onLongPress?.let { press -> { press(item.key) } },
            badge = badge,
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

        is SettingsItem.MultiSelectRow -> AbSettingsRow(
            title = item.title,
            summary = item.summary,
            enabled = item.enabled,
            onClick = { openMultiSelect(item.key) },
            iconKey = item.iconKey,
            onLongClick = onLongPress?.let { press -> { press(item.key) } },
            badge = badge,
        )

        is SettingsItem.NavigationRow -> AbSettingsRow(
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
            onLongClick = onLongPress?.let { press -> { press(item.key) } },
            badge = badge,
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
