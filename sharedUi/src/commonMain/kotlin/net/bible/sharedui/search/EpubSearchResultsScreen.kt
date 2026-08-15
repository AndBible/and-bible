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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.strings.LocalStrings

/**
 * EPUB full-text-search results list. Pure state-in / callbacks-out: the host owns the FTS query and
 * key navigation. Each [EpubResultRow] is one hit — line 1 the display key name, line 2 the highlighted
 * FTS snippet (the `<b>…</b>` match), rendered via [styledTextToAnnotatedString] so the highlight shows.
 * Tapping a row addresses the hit by its stable [EpubResultRow.keyId].
 */
@Composable
fun EpubSearchResultsScreen(
    title: String,
    loading: Boolean,
    rows: List<EpubResultRow>,
    onSelect: (keyId: String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> AbLoadingIndicator(
                    Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
                )
                rows.isEmpty() -> Text(
                    strings.emptyList,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    epubResultRows(rows = rows, onSelect = onSelect)
                }
            }
        }
    }
}
