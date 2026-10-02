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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import net.bible.sharedui.components.AbModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.components.AbIcons
import net.bible.sharedui.components.AbSheetHeader
import net.bible.sharedui.components.AbSheetScrollBound
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings

/**
 * The labels search bar's options, as a modal bottom sheet (round 17b). It replaces the
 * `DropdownMenu` this screen used to open from the same Tune icon, for the reason rounds 12c/14a
 * moved every other settings-shaped choice: a sheet is where this port puts a set of controls the
 * user reads and adjusts, and a dropdown is where it puts commands.
 *
 * Two sections. **Search** is the match-mode radio group the dropdown already had (the content
 * option is StudyPad-only, because only StudyPads have searchable content). **Show** is new: the
 * two row filters, offered only where the matching row control is drawn
 * ([ManageLabelsMode.workspaceEdits]) so a filter never refers to a state the list does not
 * display. Each filter row wears the list column's own icon pair, filled/hollow, so the sheet
 * teaches the column.
 *
 * Picking a search mode dismisses (it is a mode switch and the list is behind the sheet); toggling
 * a filter does not, so several can be set in one visit.
 *
 * `skipPartiallyExpanded = true` and no `LaunchedEffect(sheetState.isVisible)` re-show, per
 * `AbChoiceSheet`'s kdoc — dismiss means close here, and nothing has a page stack.
 *
 * ROBORAZZI: never capture this composable; capture [ManageLabelsSearchOptionsSheetContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageLabelsSearchOptionsSheet(
    open: Boolean,
    mode: ManageLabelsMode,
    searchMode: SearchMode,
    filters: Set<LabelFilter>,
    onSetSearchMode: (SearchMode) -> Unit,
    onToggleFilter: (LabelFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AbModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ManageLabelsSearchOptionsSheetContent(
            mode = mode,
            searchMode = searchMode,
            filters = filters,
            onSetSearchMode = { onSetSearchMode(it); onDismiss() },
            onToggleFilter = onToggleFilter,
            onClose = onDismiss,
        )
    }
}

/** [ManageLabelsSearchOptionsSheet]'s body. Stateless — it never dismisses anything itself, which
 *  is what makes it capturable. */
@Composable
fun ManageLabelsSearchOptionsSheetContent(
    mode: ManageLabelsMode,
    searchMode: SearchMode,
    filters: Set<LabelFilter>,
    onSetSearchMode: (SearchMode) -> Unit,
    onToggleFilter: (LabelFilter) -> Unit,
    onClose: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = strings.searchOptions, onClose = onClose)
        AbSheetScrollBound(canScrollForward = { scrollState.canScrollForward }) {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                SheetSectionTitle(strings.labelsSearchSection)
                SearchModeRow(
                    text = strings.searchModeNameStart,
                    icon = Icons.Filled.TextFields,
                    selected = searchMode == SearchMode.NAME_START,
                    onSelect = { onSetSearchMode(SearchMode.NAME_START) },
                )
                SearchModeRow(
                    text = strings.searchModeNameContains,
                    icon = Icons.Filled.Abc,
                    selected = searchMode == SearchMode.NAME_CONTAINS,
                    onSelect = { onSetSearchMode(SearchMode.NAME_CONTAINS) },
                )
                if (mode == ManageLabelsMode.STUDYPAD) {
                    SearchModeRow(
                        text = strings.searchModeContent,
                        icon = Icons.AutoMirrored.Filled.Article,
                        selected = searchMode == SearchMode.CONTENT,
                        onSelect = { onSetSearchMode(SearchMode.CONTENT) },
                    )
                }
                if (mode.workspaceEdits) {
                    SheetSectionTitle(strings.labelsFilterSection)
                    val autoAdd = filters.contains(LabelFilter.AUTO_ADD)
                    AbSwitchRow(
                        label = strings.labelsFilterAutoAssign,
                        checked = autoAdd,
                        onCheckedChange = { onToggleFilter(LabelFilter.AUTO_ADD) },
                        // The list column's own pair: filled on, HOLLOW off (not Material's
                        // "outlined" bolt, which is the same solid silhouette).
                        leadingIcon = {
                            Icon(if (autoAdd) Icons.Filled.Bolt else AbIcons.BoltOutline, contentDescription = null)
                        },
                    )
                    val favourite = filters.contains(LabelFilter.FAVOURITE)
                    AbSwitchRow(
                        label = strings.labelsFilterFavourite,
                        checked = favourite,
                        onCheckedChange = { onToggleFilter(LabelFilter.FAVOURITE) },
                        leadingIcon = {
                            Icon(if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

/** A section label inside the sheet. 16dp horizontal to line up with [AbSwitchRow]'s own padding. */
@Composable
private fun SheetSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * One match-mode choice: radio, the mode's own glyph, its name. `Role.RadioButton` plus
 * `selectable` on the ROW (not on the `RadioButton`, whose own `onClick` is null) is Material's
 * own pattern — one announcement and one hit target per row, at the row's full width.
 */
@Composable
private fun SearchModeRow(text: String, icon: ImageVector, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
