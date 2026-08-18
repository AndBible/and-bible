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

package net.bible.sharedui.bookmark

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedui.components.AbSearchableOptionSheet
import net.bible.sharedui.strings.LocalStrings

/**
 * The bookmarks label filter: one chip carrying the current filter's name, opening a searchable
 * bottom sheet. Replaces the always-visible full-width `AbDropdownField` (an
 * `ExposedDropdownMenuBox`, documented as being for short option lists only — a user's label set is
 * not bounded, and it offered no way to search).
 *
 * This deliberately does not reuse `DocumentFilterBar`: that composable's substance is
 * `ShrinkingChipPair`, a custom `Layout` that splits a width shortfall between exactly TWO chips.
 * A lone chip has nothing to shrink against, so the reusable half — [AbSearchableOptionSheet] — is
 * what is shared instead.
 */
@Composable
fun BookmarkFilterBar(
    filterLabels: List<BookmarkFilterLabel>,
    selectedFilterIndex: Int,
    onSelectFilter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var sheetOpen by remember { mutableStateOf(false) }
    // Same fallback chain the dropdown had, so an index that outruns the list renders the first
    // label rather than an empty chip.
    val selected = filterLabels.firstOrNull { it.index == selectedFilterIndex }
        ?: filterLabels.firstOrNull()
        ?: BookmarkFilterLabel(selectedFilterIndex, "")

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = { sheetOpen = true },
            // On the chip itself, not the leading icon: the chip's accessible label must not
            // depend on which of its slots happen to be present (DocumentFilterBar's type chip
            // learned this the hard way).
            modifier = Modifier.semantics { contentDescription = strings.bookmarkLabelFilter },
            label = { Text(selected.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.Label,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }

    if (sheetOpen) {
        AbSearchableOptionSheet(
            options = filterLabels,
            selected = selected,
            optionLabel = { it.displayName },
            onSelect = { onSelectFilter(it.index); sheetOpen = false },
            onDismiss = { sheetOpen = false },
            // Non-null => the type-to-filter field renders. A label set can be long, unlike the
            // download screen's seven document types.
            searchPlaceholder = strings.search,
        )
    }
}
