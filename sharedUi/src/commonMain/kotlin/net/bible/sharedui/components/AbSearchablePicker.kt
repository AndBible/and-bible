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

package net.bible.sharedui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.filterPickerOptions

/**
 * A compact, searchable single-select picker for long option lists.
 *
 * The anchor is a read-only `OutlinedTextField`-styled field (floating [label] + the selected
 * value + a ▾ icon) that opens a [ModalBottomSheet] containing a search field and a **virtualized**
 * [LazyColumn] of options, filtered case-insensitively by [optionLabel] via [filterPickerOptions].
 * Selecting an option calls [onSelect] and dismisses the sheet.
 *
 * This is the type-to-filter, fast-opening replacement for [AbDropdownField] on long lists: it
 * fixes both the "not typeable" and the "slow to open / eagerly composes every item" regressions.
 * Keep [AbDropdownField] for short enum lists. The document-selection language filter (its
 * original motivating case, ~100+ languages) moved to [net.bible.sharedui.navigation.DocumentFilterBar]'s
 * chip + [AbSearchableOptionSheet] in round 6; this field form's remaining caller is
 * [net.bible.sharedui.ai.AiModelsScreen].
 *
 * Fully portable (commonMain, no Android APIs) so it compiles for iOS too.
 *
 * @param searchPlaceholder placeholder shown in the sheet's search field (defaults to empty).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AbSearchablePicker(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    searchPlaceholder: String = "",
) {
    var open by remember { mutableStateOf(false) }

    // No built-in horizontal margin: the caller owns spacing (e.g. the single-row filter bar).
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only OutlinedTextField swallows taps without routing them to us, so overlay a
        // transparent, ripple-less click target the exact size of the field to open the sheet.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    open = true
                },
        )
    }

    if (open) {
        AbSearchableOptionSheet(
            options = options,
            selected = selected,
            optionLabel = optionLabel,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
            // Pass the String through as-is, NEVER mapping "" to null: AbSearchablePicker has
            // always rendered its search field, and its other caller (AiModelsScreen) may rely on
            // the default empty placeholder. Only a caller that explicitly passes null gets no field.
            searchPlaceholder = searchPlaceholder,
        )
    }
}

/**
 * The bottom sheet half of [AbSearchablePicker], usable on its own by callers that already have
 * their own trigger (e.g. the document-selection filter chips) and therefore want no field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AbSearchableOptionSheet(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    searchPlaceholder: String? = null,
    leadingIcon: (@Composable (T) -> Unit)? = null,
) {
    // This sheet passed NO state at all before round 14b, so it inherited the partially-expanded
    // default — and its body is a LazyColumn capped at 480dp, i.e. exactly the bounded-scroll shape
    // §7.a is about. skipPartiallyExpanded = true makes it open at content height like the rest.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AbSearchableOptionSheetContent(
            options = options,
            selected = selected,
            optionLabel = optionLabel,
            onSelect = onSelect,
            searchPlaceholder = searchPlaceholder,
            leadingIcon = leadingIcon,
        )
    }
}

/**
 * The sheet's contents, separate from [ModalBottomSheet] so goldens can capture it: a live
 * ModalBottomSheet is a popup, and popups hang Roborazzi captures.
 *
 * @param searchPlaceholder placeholder for the type-to-filter field; null renders no field at all
 *   (right for short lists such as the seven document types).
 * @param leadingIcon optional per-option leading slot. Supply a fixed-size slot for every option —
 *   including the ones with no icon — so the labels stay aligned.
 */
@Composable
fun <T> AbSearchableOptionSheetContent(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    searchPlaceholder: String? = null,
    leadingIcon: (@Composable (T) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (searchPlaceholder == null) options else filterPickerOptions(options, query, optionLabel)

    Column(modifier = modifier.fillMaxWidth()) {
        if (searchPlaceholder != null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(searchPlaceholder) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // heightIn(max) keeps the incoming constraint bounded (a ModalBottomSheet column is
        // otherwise unbounded → an unconstrained LazyColumn would crash), while still wrapping
        // to content for short filtered lists.
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            // No label-derived key: LazyColumn requires unique keys, but two options can
            // share a display name (e.g. same-named languages), which would throw
            // "Key ... was already used" and crash the picker. The filtered list is not
            // reordered by stable identity, so the default positional key is correct here.
            items(filtered) { opt ->
                ListItem(
                    headlineContent = { Text(optionLabel(opt)) },
                    leadingContent = leadingIcon?.let { icon -> { icon(opt) } },
                    trailingContent = if (opt == selected) {
                        // Test-only hook (no visual/accessibility effect): tagged per-option so a
                        // test can assert the check renders on the selected row and only on it.
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.testTag("ab-searchable-option-check-${optionLabel(opt)}"),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) },
                )
            }
        }
    }
}
