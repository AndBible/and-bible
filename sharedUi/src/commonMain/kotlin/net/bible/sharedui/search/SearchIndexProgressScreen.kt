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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexError
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.ProgressRow
import net.bible.sharedui.strings.LocalStrings

@Composable
fun SearchIndexProgressScreen(
    title: String,
    jobs: List<ProgressJob>,
    noTasks: Boolean,
    error: SearchIndexError?,
    onHide: () -> Unit,
    onDismissError: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(title = title) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(text = strings.indexingWaitMsg)
            if (noTasks) {
                Text(text = strings.noTasksRunning)
            }
            jobs.forEach { job ->
                ProgressRow(label = job.label, percent = job.percent, indeterminate = job.indeterminate)
            }
            Button(onClick = onHide, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(text = strings.doInBackground)
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
