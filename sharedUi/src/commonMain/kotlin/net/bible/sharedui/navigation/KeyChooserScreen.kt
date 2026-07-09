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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/** A single-line key list with current-item highlight + initial scroll. Reused shape for the key choosers. */
@Composable
internal fun KeyListBody(
    rows: List<KeyRow>,
    currentKeyId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentKeyId, rows) {
        val idx = rows.indexOfFirst { it.keyId == currentKeyId }
        if (idx >= 0) listState.scrollToItem(idx)
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(rows, key = { it.keyId }) { row ->
            val highlight = if (row.keyId == currentKeyId) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(row.keyId) }
                    .background(highlight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
fun ChooseGeneralBookKeyScreen(
    title: String,
    rows: List<KeyRow>,
    currentKeyId: String?,
    error: ChooserError?,
    onSelect: (String) -> Unit,
    onDismissError: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        KeyListBody(rows, currentKeyId, onSelect, Modifier.padding(padding))
    }
    if (error != null) {
        AbErrorDialog(message = strings.errorOccurred, confirmText = strings.okay, onDismiss = onDismissError)
    }
}
