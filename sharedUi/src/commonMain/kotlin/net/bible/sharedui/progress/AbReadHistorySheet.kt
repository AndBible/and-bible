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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedui.components.AbSheetHeader
import net.bible.sharedui.components.AbSheetScrollBound
import net.bible.sharedui.strings.LocalStrings

/** One row of the read-history dialog. The host pre-formats the two display lines (parity with
 *  classic `ReadHistoryDialog.show`, which builds `primaryText`/`secondaryText` per-entry). */
data class ReadHistoryRow(
    val id: String,
    val primary: String,
    val secondary: String,
)

/**
 * Cap on the row list's height. 480dp, unchanged from the dialog this replaces: the spec's brief for
 * G2.5 is "same content, same bound, same commit-on-dismiss semantics", so the container is the only
 * thing that changes. (New wrappers use the port's 400dp `AbSheetContentMaxHeight` instead; classic
 * used a `ScrollView` capped at 60% of the display height, which commonMain cannot measure.)
 */
private val ReadHistoryMaxHeight = 480.dp

/**
 * Read history as a modal bottom sheet (round 14a G2.5; reference: classic
 * `ReadHistoryDialog.show:107-235`).
 *
 * It is converted rather than left a dialog because by round 12c's own rule it is an EDITOR and not a
 * confirmation: each row's trailing button STAGES that row for deletion (dimmed, undo glyph) without
 * removing it, and the accumulated set is applied exactly once, via [onApplyDeletes], immediately
 * before [onDismiss] — mirroring classic's `setOnDismissListener { applyPendingDeletes() }`.
 *
 * The staged set lives HERE, in the wrapper, not in the body: it must survive every recomposition
 * while the sheet is open and be readable at commit time, and hoisting it is also what lets
 * [AbReadHistorySheetContent] be captured with a row already staged.
 *
 * Commit-on-dismiss is why there is no ✕/OK asymmetry: both the header ✕ and a swipe/scrim/back land
 * on the same `commit()`, exactly as the dialog's `onDismissRequest` and its OK button both did.
 *
 * ROBORAZZI: never capture this composable — an open `ModalBottomSheet` hangs the capture and takes
 * the whole `:app` suite with it. Capture [AbReadHistorySheetContent] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbReadHistorySheet(
    title: String,
    rows: List<ReadHistoryRow>,
    onApplyDeletes: (ids: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf(setOf<String>()) }

    fun commit() {
        onApplyDeletes(pendingDelete.toList())
        onDismiss()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = ::commit, sheetState = sheetState) {
        AbReadHistorySheetContent(
            title = title,
            rows = rows,
            pendingDelete = pendingDelete,
            onTogglePending = { id ->
                pendingDelete = if (id in pendingDelete) pendingDelete - id else pendingDelete + id
            },
            onClose = ::commit,
        )
    }
}

/** [AbReadHistorySheet]'s body. Stateless: [pendingDelete] comes in, toggles go out. */
@Composable
fun AbReadHistorySheetContent(
    title: String,
    rows: List<ReadHistoryRow>,
    pendingDelete: Set<String>,
    onTogglePending: (String) -> Unit,
    onClose: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        AbSheetHeader(title = title, onClose = onClose)
        if (rows.isEmpty()) {
            Text(
                text = strings.readingProgressHistoryNoEntries,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            AbSheetScrollBound(
                canScrollForward = { scrollState.canScrollForward },
                maxHeight = ReadHistoryMaxHeight,
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState).padding(horizontal = 16.dp)) {
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
                                onClick = { onTogglePending(row.id) },
                                // Test-only hook (no visual/accessibility effect, mirroring
                                // AbSearchablePicker.kt's per-option check tag): the icon itself
                                // swaps (✕/↩) rather than carrying a stable contentDescription, so
                                // AbReadHistorySheetContentTest needs a stable way to find "row X's
                                // toggle" regardless of its current pending state.
                                modifier = Modifier.testTag("ab-read-history-toggle-${row.id}"),
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
        }
    }
}
