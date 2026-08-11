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

package net.bible.sharedui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexError
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.ProgressRow
import net.bible.sharedui.strings.LocalStrings

/**
 * What the reading view's search sheet holds while the current document has no usable index: first
 * the prompt to build one, then — once [indexing] — the wait message and a [ProgressRow] per job.
 *
 * This is deliberately **not** `SearchIndexProgressScreen`, which is a full `AbScaffold` screen and
 * is shared with `ProgressStatusComposeActivity` (Batch 13 T7); making it sheet-shaped would break
 * that second caller. Only `ProgressRow` and the wording are reused. Likewise not
 * `SearchIndexScreen`, whose prompt is wrapped in its own scaffold for the standalone Activity.
 *
 * There is **no "Continue in background" button**: in an Activity that button existed only to get
 * the user off a screen that owned the whole window, whereas here closing (or just dragging away
 * from) the sheet leaves the indexing running by itself — the spec records the old button as
 * redundant.
 *
 * @param isRebuild selects the prompt wording and the confirm label (a rebuild of an existing but
 *   unusable index versus a first build).
 * @param indexing switches the panel from the prompt to progress; the caller owns that transition
 *   because it also owns the JSword job it started.
 * @param error non-null shows the failure dialog **over a sheet that stays open** — the "job says
 *   finished but `indexStatus` never reached DONE" case that `IndexPollDecision` gives up on.
 */
@Composable
fun SearchIndexPanel(
    documentName: String,
    isRebuild: Boolean,
    indexing: Boolean,
    jobs: List<ProgressJob>,
    error: SearchIndexError?,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        if (indexing) {
            Text(text = strings.indexingWaitMsg, style = MaterialTheme.typography.bodyMedium)
            // ProgressRow carries its own 16.dp horizontal padding, as it does inside
            // SearchIndexProgressScreen's equally-padded Column; the rows are meant to sit inset
            // from the paragraph above them.
            jobs.forEach { job ->
                ProgressRow(label = job.label, percent = job.percent, indeterminate = job.indeterminate)
            }
        } else {
            Text(
                text = if (isRebuild) strings.rebuildIndexFor(documentName) else strings.createIndexFor(documentName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            // End-aligned, unlike SearchIndexScreen's start-aligned Row: the panel is as wide as the
            // sheet, so the actions anchor to the trailing edge the way any Material action row does.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(strings.cancel) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCreate) {
                    Text(if (isRebuild) strings.rebuildIndexButton else strings.create)
                }
            }
        }
    }
    if (error != null) {
        AbErrorDialog(
            message = strings.errorOccurred,
            confirmText = strings.okay,
            onDismiss = onDismissError,
        )
    }
}
