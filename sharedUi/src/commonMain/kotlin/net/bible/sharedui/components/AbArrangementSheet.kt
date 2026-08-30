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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.DocGroupBy
import net.bible.sharedcore.navigation.DocSortCriterion
import net.bible.sharedcore.navigation.DocSortKey

/** Every label the sheet renders. Supplied by the caller so `:sharedUi` needs no screen knowledge. */
data class AbArrangementLabels(
    val title: String,
    val repositoryLabel: String,
    val allRepositories: String,
    val sortLabel: String,
    val groupLabel: String,
    val rememberLabel: String,
    val resetLabel: String,
    val reorderLabel: String,
    val ascending: String,
    val descending: String,
    val sortKeyLabel: (DocSortKey) -> String,
    val groupKeyLabel: (DocGroupBy) -> String,
)

/**
 * The "more filters" sheet: repository filter, drag-orderable sort criteria with a direction
 * toggle each, grouping, a "remember these settings" switch and a reset.
 *
 * ROBORAZZI: never capture this composable — an open ModalBottomSheet is a popup and popups hang
 * the capture (and take the whole :app suite with them). Capture [AbArrangementSheetContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbArrangementSheet(
    labels: AbArrangementLabels,
    sort: List<DocSortCriterion>,
    groupBy: DocGroupBy,
    groupKeys: List<DocGroupBy>,
    repositories: List<String>,
    selectedRepository: String?,
    rememberSettings: Boolean,
    resultCount: String,
    onMoveSort: (from: Int, to: Int) -> Unit,
    onToggleDirection: (DocSortKey) -> Unit,
    onGroupByChange: (DocGroupBy) -> Unit,
    onRepositoryChange: (String?) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AbArrangementSheetContent(
            labels = labels, sort = sort, groupBy = groupBy, groupKeys = groupKeys,
            repositories = repositories, selectedRepository = selectedRepository,
            rememberSettings = rememberSettings, resultCount = resultCount,
            onMoveSort = onMoveSort, onToggleDirection = onToggleDirection,
            onGroupByChange = onGroupByChange, onRepositoryChange = onRepositoryChange,
            onRememberChange = onRememberChange, onReset = onReset,
            onClose = onDismiss,
        )
    }
}

/**
 * The sheet's body, separate from [ModalBottomSheet] so goldens can capture it.
 *
 * The sort list is an [AbReorderableColumn], which is a LazyColumn — it is given an explicit
 * bounded height here, because a bottom sheet's column is unbounded and an unbounded LazyColumn is
 * a measurement crash rather than a layout wobble. The height is sized for the longest list this
 * sheet ever shows (seven criteria on the download screen): each row is 56dp (an `IconButton`'s
 * 48dp minimum touch target plus 4dp top/bottom padding), so seven rows need 392dp — confirmed
 * against `ArrangementSheetGoldenTest`'s recorded PNG, where a 336dp bound (six rows) silently
 * dropped the seventh criterion off the bottom rather than scrolling to it.
 *
 * [AbSheetHeader] (`AbSheetChrome.kt`) has no `subtitle` parameter — it only takes a title, an
 * `onClose`, and optional back/actions slots — so [resultCount] is rendered as a plain [Text] under
 * the header instead. [onClose] defaults to a no-op so a golden capture (which never dismisses
 * anything) does not have to supply one; [AbArrangementSheet] above wires it to its own `onDismiss`.
 */
@Composable
fun AbArrangementSheetContent(
    labels: AbArrangementLabels,
    sort: List<DocSortCriterion>,
    groupBy: DocGroupBy,
    groupKeys: List<DocGroupBy>,
    repositories: List<String>,
    selectedRepository: String?,
    rememberSettings: Boolean,
    resultCount: String,
    onMoveSort: (from: Int, to: Int) -> Unit,
    onToggleDirection: (DocSortKey) -> Unit,
    onGroupByChange: (DocGroupBy) -> Unit,
    onRepositoryChange: (String?) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        AbSheetHeader(title = labels.title, onClose = onClose)
        Text(
            resultCount,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (repositories.isNotEmpty()) {
            SectionLabel(labels.repositoryLabel)
            Column(Modifier.selectableGroup()) {
                RadioRow(labels.allRepositories, selectedRepository == null) { onRepositoryChange(null) }
                repositories.forEach { repo ->
                    RadioRow(repo, selectedRepository == repo) { onRepositoryChange(repo) }
                }
            }
        }

        SectionLabel(labels.sortLabel)
        AbReorderableColumn(
            items = sort,
            key = { it.key.name },
            onMove = onMoveSort,
            modifier = Modifier.heightIn(max = 392.dp),
        ) { criterion, dragHandleModifier ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = labels.reorderLabel,
                    modifier = dragHandleModifier,
                )
                Spacer(Modifier.width(16.dp))
                Text(labels.sortKeyLabel(criterion.key), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onToggleDirection(criterion.key) }) {
                    Icon(
                        if (criterion.descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                        contentDescription = if (criterion.descending) labels.descending else labels.ascending,
                    )
                }
            }
        }

        SectionLabel(labels.groupLabel)
        Column(Modifier.selectableGroup()) {
            groupKeys.forEach { key ->
                RadioRow(labels.groupKeyLabel(key), groupBy == key) { onGroupByChange(key) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(labels.rememberLabel, modifier = Modifier.weight(1f))
            Switch(checked = rememberSettings, onCheckedChange = onRememberChange)
        }
        TextButton(onClick = onReset, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(labels.resetLabel)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
