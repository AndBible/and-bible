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

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

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
    onSelect: (keyId: String, ordinal: Int) -> Unit,
) {
    // `keyId` addresses the fragment, so it repeats when one fragment holds several hits; the list
    // key must be the hit (F44/B1).
    items(rows, key = { it.rowId }) { row ->
        TwoLineListItem(
            title = row.keyName,
            subtitle = styledTextToAnnotatedString(row.text),
            onClick = { onSelect(row.keyId, row.ordinal) },
            // C4: `snippet()` bounds the snippet in TOKENS, which does not bound it in LINES. Three
            // lines keeps a result list scannable whatever the token lengths turn out to be.
            subtitleMaxLines = 3,
        )
    }
}

/**
 * The EPUB search settings — the word-mode dropdown (including raw FTS5) — for the modal settings
 * sheet, the EPUB counterpart of [BibleSearchSettings]. EPUB has no section and no translation
 * picker: both are Bible concepts.
 */
@Composable
fun EpubSearchSettings(
    mode: EpubSearchMode,
    onMode: (EpubSearchMode) -> Unit,
) {
    val strings = LocalStrings.current
    // A dropdown, not a four-way SegmentedButtonRow: four equal segments cannot hold four localized
    // labels — they soft-wrap mid-word and clip, in English at 320dp and worse in Finnish. This is
    // F6-B4's finding for the Bible section row, arriving at the EPUB form (spec D1).
    AbDropdownField(
        label = strings.searchTypePrompt,
        selected = mode,
        options = EpubSearchMode.entries,
        optionLabel = { epubSearchModeLabel(strings, it) },
        onSelect = onMode,
    )
}

/**
 * The label for one EPUB word mode. A plain function, not a composable, so a summary line can call
 * it too — the same shape as [searchSectionLabel] in `BibleSearchSettings.kt`.
 */
internal fun epubSearchModeLabel(strings: Strings, mode: EpubSearchMode): String = when (mode) {
    EpubSearchMode.ALL_WORDS -> strings.allWords
    EpubSearchMode.ANY_WORD -> strings.anyWord
    EpubSearchMode.PHRASE -> strings.phrase
    EpubSearchMode.FTS -> strings.ftsQuery
}
