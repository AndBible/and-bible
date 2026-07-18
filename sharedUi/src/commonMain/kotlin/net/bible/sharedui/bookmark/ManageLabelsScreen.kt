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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.bookmark.LabelCategory
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedui.components.AbColor
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * Stateless port of the classic `ManageLabels` activity / `manage_labels.xml` +
 * `ManageLabelItemAdapter`. Every value comes from [rows]/[mode] (mirroring the classic adapter's
 * derived per-item control visibility); every mutation is forwarded to the host via the action
 * lambdas, which the host wires 1:1 to a `ManageLabelsController` (a later task). [iconSlot] renders
 * the label's leading glyph (custom icon, or the built-in default when `customIcon == null`) —
 * Android drawable resources live host-side, so this screen never touches them; [actions] is the
 * top-bar overflow (reset/reorder/etc, also host-built).
 *
 * Design note on the leading icon: the classic adapter shows two *separate* `ImageView`s per row
 * (a label/auto-assign-circle glyph, and — further along the row — an optional custom-icon glyph).
 * This port's [iconSlot] contract only carries one glyph slot, so the two are consolidated here:
 * when the label is auto-assigned in a workspace-editing [mode], the leading slot is a plain
 * colour-filled circle (mirrors `ic_label_circle`, drawn natively — no host round-trip needed for a
 * solid dot); otherwise [iconSlot] renders the label's own icon (custom, or the host's built-in
 * default when [net.bible.sharedcore.bookmark.LabelItem.customIcon] is `null`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageLabelsScreen(
    title: String,
    rows: List<ManageLabelsRow>,
    mode: ManageLabelsMode,
    searchText: String,
    nameSearchInside: Boolean,
    onSearch: (String) -> Unit,
    onToggleSearchInside: () -> Unit,
    onRowClick: (labelId: String) -> Unit,
    onRowLongClick: (labelId: String) -> Unit,
    onToggleChecked: (labelId: String) -> Unit,
    onToggleFavourite: (labelId: String) -> Unit,
    onSetPrimary: (labelId: String) -> Unit,
    onToggleAutoAssign: (labelId: String) -> Unit,
    onUp: () -> Unit,
    iconSlot: @Composable (customIcon: String?, colorArgb: Int) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val strings = LocalStrings.current

    AbScaffold(title = title, onNavigateUp = onUp, actions = actions) { padding: PaddingValues ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbSearchField(
                    value = searchText,
                    onValueChange = onSearch,
                    placeholder = strings.labelsSearchHint,
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 8.dp,
                )
                TextButton(
                    onClick = onToggleSearchInside,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (nameSearchInside) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(if (nameSearchInside) strings.matchAnyText else strings.matchStartOfText)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows, key = ::rowKey) { row ->
                    when (row) {
                        is ManageLabelsRow.Header -> CategoryHeaderRow(row.category, strings)
                        is ManageLabelsRow.Item -> LabelItemRow(
                            row = row,
                            mode = mode,
                            onRowClick = onRowClick,
                            onRowLongClick = onRowLongClick,
                            onToggleChecked = onToggleChecked,
                            onToggleFavourite = onToggleFavourite,
                            onSetPrimary = onSetPrimary,
                            onToggleAutoAssign = onToggleAutoAssign,
                            iconSlot = iconSlot,
                            strings = strings,
                        )
                        // TODO(Compose Batch 7b-2 Task 3): render StudyPad content-search hits.
                        is ManageLabelsRow.SearchResult -> {}
                    }
                }
            }
        }
    }
}

private fun rowKey(row: ManageLabelsRow): String = when (row) {
    is ManageLabelsRow.Header -> "header_${row.category}"
    is ManageLabelsRow.Item -> "item_${row.label.id}"
    is ManageLabelsRow.SearchResult -> "search_${row.labelId}"
}

/** Non-interactive section header ("Selected labels" / "Recent labels" / "Other labels"). */
@Composable
private fun CategoryHeaderRow(category: LabelCategory, strings: Strings) {
    Text(
        text = when (category) {
            LabelCategory.ACTIVE -> strings.activeLabelsHeader
            LabelCategory.RECENT -> strings.recentLabelsHeader
            LabelCategory.OTHER -> strings.otherLabelsHeader
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LabelItemRow(
    row: ManageLabelsRow.Item,
    mode: ManageLabelsMode,
    onRowClick: (String) -> Unit,
    onRowLongClick: (String) -> Unit,
    onToggleChecked: (String) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onSetPrimary: (String) -> Unit,
    onToggleAutoAssign: (String) -> Unit,
    iconSlot: @Composable (customIcon: String?, colorArgb: Int) -> Unit,
    strings: Strings,
) {
    val label = row.label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onRowClick(label.id) },
                onLongClick = { onRowLongClick(label.id) },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading glyph: auto-assign circle (workspace-editing modes only) or the icon rendered by
        // the host's iconSlot (custom icon, or its built-in default when null). Clicking it toggles
        // auto-assign membership, same as the classic labelIcon click.
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(
                    if (mode.workspaceEdits && !label.isUnlabeled) {
                        Modifier.clickable { onToggleAutoAssign(label.id) }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (mode.workspaceEdits && !label.isUnlabeled && row.isAutoAssign) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(AbColor.toComposeColor(label.color), CircleShape),
                )
            } else {
                iconSlot(label.customIcon, label.color)
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = label.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (row.highlighted) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (label.hasOverride) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = strings.overrideIndicatorDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(start = 4.dp),
            )
        }

        if (mode.showCheckboxes) {
            Checkbox(checked = row.checked, onCheckedChange = { onToggleChecked(label.id) })
        }

        if (mode.workspaceEdits && !label.isUnlabeled) {
            IconButton(onClick = { onToggleFavourite(label.id) }) {
                Icon(
                    if (label.favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = strings.favouriteLabelSwitchLabel,
                    tint = if (label.favourite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (mode.primaryShown && row.checked) {
            IconButton(onClick = { onSetPrimary(label.id) }) {
                Icon(
                    if (row.isPrimary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = strings.primaryLabelSwitchLabel,
                    tint = if (row.isPrimary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
