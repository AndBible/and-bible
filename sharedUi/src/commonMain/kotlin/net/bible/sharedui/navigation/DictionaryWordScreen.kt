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
package net.bible.sharedui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.strings.LocalStrings

@Composable
fun ChooseDictionaryWordScreen(
    title: String,
    hint: String,
    loading: Boolean,
    query: String,
    rows: List<DictRow>,
    error: ChooserError?,
    loadSnippet: suspend (String) -> String,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismissError: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AbSearchField(value = query, onValueChange = onQueryChange, placeholder = hint)
            if (loading) {
                AbLoadingIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.keyId }) { row ->
                    // Snippet loaded lazily per visible row (classic KeyInfo.toString() behavior).
                    val snippet by produceState(initialValue = "", row.keyId) {
                        value = try { loadSnippet(row.keyId) } catch (e: Exception) { "" }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(row.keyId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        if (snippet.isNotEmpty()) {
                            Text(snippet, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (error != null) {
        AbErrorDialog(message = strings.errorOccurred, confirmText = strings.okay, onDismiss = onDismissError)
    }
}
