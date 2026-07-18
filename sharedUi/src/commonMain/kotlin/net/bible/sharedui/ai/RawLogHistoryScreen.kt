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

package net.bible.sharedui.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.RawLogSummaryVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * The saved AI-conversation raw-log list (mirrors classic
 * `net.bible.android.view.activity.ai.RawLogHistoryActivity` / `RawLogHistoryAdapter`).
 *
 * Fully stateless: [summaries]/[selection]/[selectionMode] come straight from
 * `net.bible.sharedcore.ai.RawLogHistoryController`'s identically-named flows, and every
 * interaction is forwarded 1:1 back to the controller ([onToggleSelect] → `toggleSelect`,
 * [onClearSelection] → `clearSelection`, [onDeleteSelected] → `deleteSelected`,
 * [onDeleteOlderThan]/[onDeleteAll] → `deleteOlderThan`/`deleteAll`, [onOpenLog] → `openLog`) —
 * this screen owns only the "delete old logs" dialog's visibility.
 *
 * **Selection.** [AbSelectionScaffold] supplies the contextual bar: a row click toggles selection
 * while [selectionMode], otherwise opens the log; a long-press always toggles selection (entering
 * it from a normal row). The contextual bar's only action is delete-selected (no confirmation,
 * matching classic's action-mode behaviour); exiting selection clears it.
 *
 * **Top-bar overflow (non-selection).** "Delete old logs…" opens an [AbListChoiceDialog] with the
 * four classic cutoffs (1 week / 1 month / 3 months / all), routing to [onDeleteOlderThan] with
 * the day count or to [onDeleteAll]; "Help" is a pure host-navigation callback ([onHelp]), like the
 * other AI screens' overflow "Help" items.
 */
@Composable
fun RawLogHistoryScreen(
    summaries: List<RawLogSummaryVd>,
    selection: Set<String>,
    selectionMode: Boolean,
    onOpenLog: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteOlderThan: (Int) -> Unit,
    onDeleteAll: () -> Unit,
    onHelp: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    var showDeleteOldDialog by remember { mutableStateOf(false) }

    AbSelectionScaffold(
        title = strings.rawLogHistoryTitle,
        selectionMode = selectionMode,
        selectedCount = selection.size,
        onNavigateUp = onNavigateUp,
        onExitSelection = onClearSelection,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(
                    text = { Text(strings.rawLogDeleteOld) },
                    onClick = { close(); showDeleteOldDialog = true },
                )
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); onHelp() })
            }
        },
        selectionActions = {
            AbActionIcon(Icons.Filled.Delete, contentDescription = strings.rawLogDeleteSelectedLabel, onClick = onDeleteSelected)
        },
    ) { padding ->
        if (summaries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(strings.rawLogHistoryEmpty, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(summaries, key = { it.id }) { summary ->
                    RawLogRow(
                        summary = summary,
                        selectionMode = selectionMode,
                        selected = summary.id in selection,
                        onClick = { if (selectionMode) onToggleSelect(summary.id) else onOpenLog(summary.id) },
                        onLongClick = { onToggleSelect(summary.id) },
                    )
                }
            }
        }
    }

    if (showDeleteOldDialog) {
        AbListChoiceDialog(
            title = strings.rawLogDeleteOld,
            choices = listOf(
                SettingsItem.Choice("7", strings.rawLogOlder1Week),
                SettingsItem.Choice("30", strings.rawLogOlder1Month),
                SettingsItem.Choice("90", strings.rawLogOlder3Months),
                SettingsItem.Choice("all", strings.rawLogDeleteAll),
            ),
            selectedValue = "",
            onSelect = { value -> if (value == "all") onDeleteAll() else onDeleteOlderThan(value.toInt()) },
            onDismiss = { showDeleteOldDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RawLogRow(
    summary: RawLogSummaryVd,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Blank promptName -> em dash is a display-only concern per RawLogSummaryVd's KDoc
                // (mirrors classic RawLogHistoryAdapter's `ifBlank { "—" }`, no string resource involved).
                Text(
                    text = summary.promptName.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (summary.hasError) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.rawLogErrorIndicator,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (summary.modelInfo.isNotBlank()) {
                Text(summary.modelInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val tokenCost = listOf(summary.tokenInfo, summary.costInfo).filter { it.isNotBlank() }.joinToString(" · ")
            if (tokenCost.isNotBlank()) {
                Text(tokenCost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(summary.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
