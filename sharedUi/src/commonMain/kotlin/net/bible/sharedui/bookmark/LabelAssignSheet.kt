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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedui.components.AbIcons
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.components.AbQuickSheetContent
import net.bible.sharedui.components.LocalAbSheetAnimatedClose
import net.bible.sharedui.components.abFilterChipBorder
import net.bible.sharedui.components.abFilterChipColors
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.isPureMonochrome

/** Test tags of the assign sheet's own controls. */
const val LABEL_ASSIGN_SEARCH_TAG = "label-assign-search"
/** The collapsed search: a chip in the chip row that opens the field. */
const val LABEL_ASSIGN_SEARCH_CHIP_TAG = "label-assign-search-chip"
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
 * workspace switcher: a header with the New (+) and Edit labels (pencil) actions, the search field and filter chips, the SAME
 * label list the full-screen [ManageLabelsScreen] draws ([ManageLabelsList] in [ManageLabelsMode.ASSIGN]).
 * The pencil opens that full-screen list; there is no footer (#4207: the sheet is capped at half the window).
 *
 * A row tap ticks the label (in the full screen it opens the editor). A long press, and New (+), open the
 * full-screen label editor: on a phone the rows have no toggles ([manageLabelsRowTogglesShown]), so the editor is
 * where the main label, favourite and auto-assign are set. [onDismiss] is every close (swipe, scrim, back, ✕); the
 * host saves on it when something changed, as the full screen's Back does.
 *
 * The pencil, New (+) and the long press slide the sheet away before they act, like ✕ does
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
        actions = { HeaderActions(actions) },
        onDismiss = onDismiss,
        canScrollForward = { listState.canScrollForward },
        bodyFillsSheetCap = true,
    ) {
        LabelAssignSheetContent(state, actions, listState)
    }
}

/** The header's actions: New (+), then Edit labels (the pencil, which [LabelAssignSheet] shows instead of a footer). */
@Composable
private fun RowScope.HeaderActions(actions: LabelAssignSheetActions) {
    val onClick = hideSheetThen(actions.onNewLabel)
    actions.newLabelIcon(this, onClick)
    IconButton(onClick = hideSheetThen(actions.onEditLabels), modifier = Modifier.testTag(LABEL_ASSIGN_EDIT_LABELS_TAG)) {
        Icon(Icons.Outlined.Edit, contentDescription = LocalStrings.current.labelAssignEditLabels)
    }
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

/** [LabelAssignSheet] without the modal window: the same header and body, for goldens and UI tests. */
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
        actions = { HeaderActions(actions) },
        canScrollForward = { listState.canScrollForward },
        bodyFillsSheetCap = true,
    ) {
        LabelAssignSheetContent(state, actions, listState)
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
        // Open = the user tapped the chip (saveable) or a search text is already there (restored, or set by the host).
        var tapped by rememberSaveable { mutableStateOf(false) }
        val open = tapped || state.searchText.isNotEmpty()
        // Latch: a field shown only because of host-set text must not close when the user backspaces to empty.
        LaunchedEffect(open) { if (open) tapped = true }
        val collapse = {
            tapped = false
            actions.onSearch("")
        }
        // Closed: one line that scrolls sideways, not a wrapping FlowRow. Open: the field takes the remaining width
        // (a weight cannot sit inside a scroll), with Favourites and Auto-assign reduced to their icons.
        val rowModifier = Modifier.fillMaxWidth().then(if (open) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(horizontal = 16.dp)
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open) {
                SearchField(state, actions, focusOnOpen = tapped, onCollapse = collapse, modifier = Modifier.weight(1f))
            } else {
                AssignChip(
                    selected = false,
                    text = strings.labelsSearchHint,
                    tag = LABEL_ASSIGN_SEARCH_CHIP_TAG,
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    onClick = { tapped = true },
                )
            }
            val favourite = LabelFilter.FAVOURITE in state.filters
            AssignChip(
                selected = favourite,
                compact = open,
                text = if (open) null else strings.labelAssignFilterFavourites,
                tag = LABEL_ASSIGN_FILTER_FAVOURITE_TAG,
                icon = {
                    Icon(
                        if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (open) strings.labelAssignFilterFavourites else null,
                    )
                },
                onClick = { actions.onToggleFilter(LabelFilter.FAVOURITE) },
            )
            val autoAssign = LabelFilter.AUTO_ADD in state.filters
            AssignChip(
                selected = autoAssign,
                compact = open,
                text = if (open) null else strings.labelAssignFilterAutoAssign,
                tag = LABEL_ASSIGN_FILTER_AUTO_ASSIGN_TAG,
                icon = {
                    Icon(
                        if (autoAssign) Icons.Filled.Bolt else AbIcons.BoltOutline,
                        contentDescription = if (open) strings.labelAssignFilterAutoAssign else null,
                    )
                },
                onClick = { actions.onToggleFilter(LabelFilter.AUTO_ADD) },
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

/**
 * The open search: a chip-shaped text field (same 32dp height as the chips, in the same 48dp touch row, so the sheet
 * does not grow when it opens) with the Abc contains toggle and a clear icon at its end. Clear and Escape both empty
 * the text and collapse back to the chip; Escape is consumed only here, so with the search closed it falls through to
 * the sheet's own dismissal. Nothing animates.
 */
@Composable
private fun SearchField(
    state: LabelAssignSheetState,
    actions: LabelAssignSheetActions,
    focusOnOpen: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusOnOpen) {
        if (focusOnOpen) runCatching { focusRequester.requestFocus() }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val contains = state.searchMode == SearchMode.NAME_CONTAINS
    Row(
        modifier = modifier.height(32.dp)
            .border(abFilterChipBorder(true, false), FilterChipDefaults.shape)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
        Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
            if (state.searchText.isEmpty()) {
                Text(
                    strings.labelsSearchHint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = state.searchText,
                onValueChange = actions.onSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge.copy(color = ink),
                cursorBrush = SolidColor(ink),
                modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = strings.labelsSearchHint }
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape) {
                            if (event.type == KeyEventType.KeyDown) onCollapse()
                            true
                        } else false
                    }
                    .testTag(LABEL_ASSIGN_SEARCH_TAG),
            )
        }
        // Borderless and compact: a bordered chip inside the field's own border read as a chip in a chip.
        CompactToggle(
            selected = contains,
            tag = LABEL_ASSIGN_SEARCH_CONTAINS_TAG,
            width = 32.dp,
            height = 32.dp,
            bordered = false,
            onClick = { actions.onSetSearchMode(if (contains) SearchMode.NAME_START else SearchMode.NAME_CONTAINS) },
        ) { Icon(Icons.Filled.Abc, contentDescription = strings.labelAssignSearchContains) }
        IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Clear, contentDescription = strings.searchClear, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * A filter chip; [compact] (icon only, while the search is open) is a [CompactToggle] 40dp wide instead of the
 * ~56dp an icon-only FilterChip takes, still 48dp tall to touch and drawn 32dp tall like the field.
 */
@Composable
private fun AssignChip(
    selected: Boolean,
    text: String?,
    tag: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    if (compact) {
        CompactToggle(selected, tag, width = 40.dp, height = 48.dp, bordered = true, onClick = onClick, content = icon)
        return
    }
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

/**
 * A small toggle with the chip's colours (selected: secondaryContainer, or inverted ink in monochrome, no hue). The
 * outer box is [height] tall for touch; the drawn shape is at most 32dp tall, centred. Toggleable semantics like a chip.
 */
@Composable
private fun CompactToggle(
    selected: Boolean,
    tag: String,
    width: Dp,
    height: Dp,
    bordered: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val mono = isPureMonochrome()
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !selected -> Color.Transparent
        mono -> scheme.onSurface
        else -> scheme.secondaryContainer
    }
    val ink = when {
        !selected -> scheme.onSurfaceVariant
        mono -> scheme.surface
        else -> scheme.onSecondaryContainer
    }
    val shape = FilterChipDefaults.shape
    val drawn = Modifier.height(minOf(height, 32.dp)).width(width)
        .clip(shape)
        .background(container)
        .let { if (bordered) it.border(abFilterChipBorder(true, selected) ?: BorderStroke(0.dp, Color.Transparent), shape) else it }
    Box(
        modifier = Modifier.size(width, height)
            .testTag(tag)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(drawn, contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { content() }
            }
        }
    }
}
