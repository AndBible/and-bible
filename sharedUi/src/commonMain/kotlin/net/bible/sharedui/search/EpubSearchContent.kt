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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings

/**
 * EPUB result rows for the reading view's search sheet — the EPUB counterpart of [bibleResultRows].
 *
 * One row per hit: line 1 the display key name, line 2 the highlighted FTS snippet. There is no
 * expansion state and no per-row translation label, because an EPUB search targets exactly one
 * document by definition (`EpubSearchService.searchEpub(docId, ...)`) — which is also why the
 * sheet's header `actions` slot is empty for EPUB.
 */
fun LazyListScope.epubResultRows(
    rows: List<EpubResultRow>,
    onSelect: (keyId: String) -> Unit,
) {
    items(rows, key = { it.keyId }) { row ->
        TwoLineListItem(
            title = row.keyName,
            subtitle = styledTextToAnnotatedString(row.text),
            onClick = { onSelect(row.keyId) },
        )
    }
}

/**
 * The EPUB search settings — the 4-way word-mode row (including raw FTS5) — for the modal settings
 * sheet, the EPUB counterpart of [BibleSearchSettings]. EPUB has no section and no translation
 * picker: both are Bible concepts.
 */
@Composable
fun EpubSearchSettings(
    mode: EpubSearchMode,
    onMode: (EpubSearchMode) -> Unit,
) {
    val strings = LocalStrings.current
    val modes = EpubSearchMode.entries
    val modeLabels = listOf(strings.allWords, strings.anyWord, strings.phrase, strings.ftsQuery)

    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        modes.forEachIndexed { index, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onMode(m) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(modeLabels[index]) }
        }
    }
}
