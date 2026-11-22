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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.components.AbIcons
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.components.AbQuickSheetFooterRow
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.components.LocalAbSheetAnimatedClose
import net.bible.sharedui.components.abFilterChipBorder
import net.bible.sharedui.components.abFilterChipColors
import net.bible.sharedui.strings.LocalStrings

/** Test tags of the assign sheet's own controls. */
const val LABEL_ASSIGN_SEARCH_TAG = "label-assign-search"
const val LABEL_ASSIGN_FILTER_FAVOURITE_TAG = "label-assign-filter-favourite"
const val LABEL_ASSIGN_FILTER_AUTO_ASSIGN_TAG = "label-assign-filter-auto-assign"
const val LABEL_ASSIGN_SEARCH_CONTAINS_TAG = "label-assign-search-contains"
const val LABEL_ASSIGN_EDIT_LABELS_TAG = "label-assign-edit-labels"

/**
 * Everything the assign sheet shows and every action it offers. One value instead of fifteen
 * parameters, because the sheet and its golden-capturable twin ([LabelAssignSheetFrame]) take the same set.
 */
@kotlin.native.HiddenFromObjC
class LabelAssignSheetState(
    val title: String,
    val rows: List<ManageLabelsRow>,
    val styleTagsVisible: Boolean,
    val searchText: String,
    val searchMode: SearchMode,
    val filters: Set<LabelFilter>,
)

/** The assign sheet's actions; see [LabelAssignSheet]. */
@kotlin.native.HiddenFromObjC
class LabelAssignSheetActions(
    val onSearch: (String) -> Unit,
    val onSetSearchMode: (SearchMode) -> Unit,
    val onToggleFilter: (LabelFilter) -> Unit,
    val onToggleChecked: (String) -> Unit,
    val onToggleFavourite: (String) -> Unit,
    val onSetPrimary: (String) -> Unit,
    val onToggleAutoAssign: (String) -> Unit,
    /** A row's long press: the full-screen label editor on that label. */
    val onEditLabel: (String) -> Unit,
    /** New (+): the full-screen label editor on a new label. */
    val onNewLabel: () -> Unit,
    val onEditLabels: () -> Unit,
    /** Draws the New (+) button, calling the given click handler. */
    val newLabelIcon: @Composable RowScope.(onClick: () -> Unit) -> Unit,
    val iconSlot: @Composable (customIcon: String?, tint: Color) -> Unit,
)

/**
 * "Assign labels" as a bottom sheet (issue #4201), on the reading view's quick-sheet shell like the
 * workspace switcher: a header with the New (+) action, the search field and filter chips, the SAME
 * label list the full-screen [ManageLabelsScreen] draws ([ManageLabelsList] in [ManageLabelsMode.ASSIGN]),
 * and an "Edit labels" footer that opens the full-screen list.
 *
 * A row tap ticks the label (in the full screen it opens the editor). A long press, and New (+), open the
 * full-screen label editor: on a phone the rows have no toggles ([manageLabelsRowTogglesShown]), so the editor is
 * where the main label, favourite and auto-assign are set. [onDismiss] is every close (swipe, scrim, back, ✕); the
 * host saves on it when something changed, as the full screen's Back does.
 *
 * The footer, New (+) and the long press slide the sheet away before they act, like ✕ does
 * ([LocalAbSheetAnimatedClose]; at once with "Disable animations").
 *
 * ROBORAZZI: never capture this composable; capture [LabelAssignSheetFrame].
 */
@kotlin.native.HiddenFromObjC
@Composable
fun LabelAssignSheet(state: LabelAssignSheetState, actions: LabelAssignSheetActions, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    AbQuickSheet(
        open = true,
        title = state.title,
        actions = { NewLabelAction(actions) },
        onDismiss = onDismiss,
        canScrollForward = { listState.canScrollForward },
        footer = { EditLabelsFooter(actions.onEditLabels) },
        bodyFillsSheetCap = true,
    ) {
        LabelAssignSheetContent(state, actions, listState)
    }
}

@Composable
private fun RowScope.NewLabelAction(actions: LabelAssignSheetActions) {
    val onClick = hideSheetThen(actions.onNewLabel)
    actions.newLabelIcon(this, onClick)
}

/**
 * [action] after the enclosing modal sheet has slid out of view ([LocalAbSheetAnimatedClose]); [action] itself
 * outside a modal sheet (goldens, UI tests) and with "Disable animations".
 */
@Composable
private fun hideSheetThen(action: () -> Unit): () -> Unit {
    val animatedClose = LocalAbSheetAnimatedClose.current
    return { if (animatedClose != null) animatedClose(action) else action() }
}

@Composable
private fun hideSheetThen(action: (String) -> Unit): (String) -> Unit {
    val animatedClose = LocalAbSheetAnimatedClose.current
    return { id -> if (animatedClose != null) animatedClose { action(id) } else action(id) }
}

/** [LabelAssignSheet] without the modal window: the same header, body and footer, for goldens and UI tests. */
@kotlin.native.HiddenFromObjC
@Composable
fun LabelAssignSheetFrame(
    state: LabelAssignSheetState,
    actions: LabelAssignSheetActions,
    onClose: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    AbQuickSheetContent(
        title = state.title,
        onClose = onClose,
        actions = { NewLabelAction(actions) },
        canScrollForward = { listState.canScrollForward },
        footer = { EditLabelsFooter(actions.onEditLabels) },
        bodyFillsSheetCap = true,
    ) {
        LabelAssignSheetContent(state, actions, listState)
    }
}

@Composable
private fun EditLabelsFooter(onEditLabels: () -> Unit) {
    Column(Modifier.testTag(LABEL_ASSIGN_EDIT_LABELS_TAG)) {
        AbQuickSheetFooterRow(text = LocalStrings.current.labelAssignEditLabels, onClick = hideSheetThen(onEditLabels))
    }
}

/**
 * The sheet's body: search field, filter chips, then the shared list. The chips are always shown
 * (unlike the full screen's options sheet, which offers the filters only beside visible ⚡/♥
 * columns): their icons are the columns' own, so they explain themselves, and in a short sheet they
 * are the quickest way to the user's favourite labels.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun LabelAssignSheetContent(
    state: LabelAssignSheetState,
    actions: LabelAssignSheetActions,
    listState: LazyListState = rememberLazyListState(),
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        AbSearchField(
            value = state.searchText,
            onValueChange = actions.onSearch,
            placeholder = strings.labelsSearchHint,
            modifier = Modifier.testTag(LABEL_ASSIGN_SEARCH_TAG),
            verticalPadding = 4.dp,
        )
        // One line that scrolls sideways, not a wrapping FlowRow: three wrapped chips took a third of the sheet.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val favourite = LabelFilter.FAVOURITE in state.filters
            AssignChip(
                selected = favourite,
                text = strings.labelAssignFilterFavourites,
                tag = LABEL_ASSIGN_FILTER_FAVOURITE_TAG,
                icon = { Icon(if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null) },
                onClick = { actions.onToggleFilter(LabelFilter.FAVOURITE) },
            )
            val autoAssign = LabelFilter.AUTO_ADD in state.filters
            AssignChip(
                selected = autoAssign,
                text = strings.labelAssignFilterAutoAssign,
                tag = LABEL_ASSIGN_FILTER_AUTO_ASSIGN_TAG,
                icon = { Icon(if (autoAssign) Icons.Filled.Bolt else AbIcons.BoltOutline, contentDescription = null) },
                onClick = { actions.onToggleFilter(LabelFilter.AUTO_ADD) },
            )
            // Icon only, named for screen readers: with three labelled chips the third was cut off on a phone.
            val contains = state.searchMode == SearchMode.NAME_CONTAINS
            AssignChip(
                selected = contains,
                text = null,
                tag = LABEL_ASSIGN_SEARCH_CONTAINS_TAG,
                icon = { Icon(Icons.Filled.Abc, contentDescription = strings.labelAssignSearchContains) },
                onClick = { actions.onSetSearchMode(if (contains) SearchMode.NAME_START else SearchMode.NAME_CONTAINS) },
            )
        }
        ManageLabelsList(
            rows = state.rows,
            mode = ManageLabelsMode.ASSIGN,
            styleTagsVisible = state.styleTagsVisible,
            // The row ticks; the long press opens the editor (the full screen's row tap and long press both edit).
            onRowClick = actions.onToggleChecked,
            onRowLongClick = hideSheetThen(actions.onEditLabel),
            onToggleChecked = actions.onToggleChecked,
            onToggleFavourite = actions.onToggleFavourite,
            onSetPrimary = actions.onSetPrimary,
            onToggleAutoAssign = actions.onToggleAutoAssign,
            iconSlot = actions.iconSlot,
            listState = listState,
        )
    }
}

@Composable
private fun AssignChip(selected: Boolean, text: String?, tag: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        colors = abFilterChipColors(),
        border = abFilterChipBorder(true, selected),
        // A text chip leads with its icon; an icon-only chip carries the icon as its label, centred.
        leadingIcon = if (text != null) icon else null,
        label = { if (text != null) Text(text) else icon() },
        modifier = Modifier.testTag(tag),
    )
}
