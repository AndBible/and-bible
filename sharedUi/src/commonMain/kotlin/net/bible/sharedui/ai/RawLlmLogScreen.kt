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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ai.RawLogEntryVd
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings
import androidx.compose.foundation.lazy.rememberLazyListState
import net.bible.sharedui.components.volumeScrollTarget
import net.bible.sharedui.components.volumeVerticalScroll

/**
 * The raw LLM conversation log detail screen (mirrors classic
 * `net.bible.android.view.activity.ai.RawLlmLogActivity` / `RawLlmLogAdapter`).
 *
 * Two mutually-exclusive content modes, derived from which of [recordText]/[entries] is
 * populated (matching `net.bible.sharedcore.ai.RawLlmLogController`'s KDoc: DB mode leaves
 * [entries] empty, in-memory/session mode leaves [recordText] null):
 * - **DB mode** ([recordText] non-null): a scrollable, selectable monospace text block — the
 *   whole decompressed log, verbatim.
 * - **In-memory mode** ([entries] non-empty): a [LazyColumn] of expandable rows (title +
 *   token/cost header; body shown, monospace, when expanded per [expandedIndices]).
 *
 * While [loading] (the host hasn't yet resolved which mode applies / the async load hasn't
 * returned) an [AbLoadingIndicator] is shown instead of any content, and the DB-only delete
 * action is hidden (mode isn't known to be DB mode yet). Once loaded with neither populated,
 * shows the empty state (covers both "session has no entries" and "record was deleted/missing").
 *
 * **Top bar.** Copy/share are always-visible [AbActionIcon]s forwarding straight to [onCopy]/
 * [onShare] (host performs the actual clipboard/share-sheet platform call and any toast — kept
 * out of commonMain). Delete (DB mode only) and report-bug ([canReportBug] only) live in an
 * overflow menu, omitted entirely when neither applies. Delete asks for confirmation locally
 * (mirrors classic's `are_you_sure` dialog) before calling [onDelete]; report-bug forwards
 * straight to [onReportBug] (the host resolves availability into [canReportBug]).
 */
@Composable
fun RawLlmLogScreen(
    title: String,
    loading: Boolean,
    recordText: String?,
    entries: List<RawLogEntryVd>,
    expandedIndices: Set<Int>,
    canReportBug: Boolean,
    onToggleExpanded: (Int) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReportBug: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    val isDbMode = !loading && recordText != null
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AbScaffold(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = {
            AbActionIcon(Icons.Filled.ContentCopy, contentDescription = strings.copyLabel, onClick = onCopy)
            AbActionIcon(Icons.Filled.Share, contentDescription = strings.shareLabel, onClick = onShare)
            if (isDbMode || canReportBug) {
                AbOverflowMenu(contentDescription = null) { close ->
                    if (isDbMode) {
                        DropdownMenuItem(
                            text = { Text(strings.deleteLabel) },
                            onClick = { close(); showDeleteConfirm = true },
                        )
                    }
                    if (canReportBug) {
                        DropdownMenuItem(text = { Text(strings.reportBugLabel) }, onClick = { close(); onReportBug() })
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                recordText != null -> RawLlmLogTextBody(recordText, modifier = Modifier.weight(1f))
                entries.isNotEmpty() -> RawLlmLogEntryList(
                    entries = entries,
                    expandedIndices = expandedIndices,
                    onToggleExpanded = onToggleExpanded,
                    modifier = Modifier.weight(1f),
                )
                else -> RawLlmLogEmptyState(strings.rawLlmLogEmpty, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showDeleteConfirm) {
        AbConfirmDialog(
            title = null,
            message = strings.areYouSure,
            confirmText = strings.yes,
            dismissText = strings.no,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun RawLlmLogTextBody(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier.fillMaxSize().volumeVerticalScroll(rememberScrollState())) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun RawLlmLogEntryList(
    entries: List<RawLogEntryVd>,
    expandedIndices: Set<Int>,
    onToggleExpanded: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().volumeScrollTarget(listState)) {
        itemsIndexed(entries) { index, entry ->
            RawLlmLogEntryRow(entry = entry, expanded = index in expandedIndices, onToggle = { onToggleExpanded(index) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun RawLlmLogEntryRow(entry: RawLogEntryVd, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                Text(entry.tokenInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (expanded) {
            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.body,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RawLlmLogEmptyState(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
