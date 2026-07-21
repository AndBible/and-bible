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

package net.bible.sharedui.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings

/** One row of the read-history dialog. The host pre-formats the two display lines (parity with
 *  classic `ReadHistoryDialog.show`, which builds `primaryText`/`secondaryText` per-entry). */
data class ReadHistoryRow(
    val id: String,
    val primary: String,
    val secondary: String,
)

/** Cap on the row list's height, so a long history scrolls instead of growing the dialog past the
 *  screen (classic used a `ScrollView` capped at 60% of the display height; a fixed dp cap is used
 *  here since commonMain has no display-metrics access). */
private val maxListHeight = 480.dp

/**
 * Read-history dialog (reference: classic `ReadHistoryDialog.show:107-235`). Shows [rows], each
 * with a trailing toggle button that marks it for deletion (dimmed, undo glyph) without removing
 * it from the list immediately — mirrors the classic per-row alpha/glyph toggle backed by a
 * `pendingDeleteIds` set. The accumulated set is only applied once, via [onApplyDeletes], right
 * before [onDismiss] fires (matches classic's `setOnDismissListener { applyPendingDeletes() }`).
 */
@Composable
fun AbReadHistoryDialog(
    title: String,
    rows: List<ReadHistoryRow>,
    onApplyDeletes: (ids: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var pendingDelete by remember { mutableStateOf(setOf<String>()) }

    fun confirm() {
        onApplyDeletes(pendingDelete.toList())
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::confirm,
        title = { Text(title) },
        text = {
            if (rows.isEmpty()) {
                Text(
                    text = strings.readingProgressHistoryNoEntries,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxListHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (row in rows) {
                        val isPending = row.id in pendingDelete
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().alpha(if (isPending) 0.45f else 1f),
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(text = row.primary, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = row.secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    pendingDelete = if (isPending) {
                                        pendingDelete - row.id
                                    } else {
                                        pendingDelete + row.id
                                    }
                                },
                            ) {
                                if (isPending) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                                } else {
                                    Icon(Icons.Filled.Close, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::confirm) { Text(strings.okay) } },
    )
}
