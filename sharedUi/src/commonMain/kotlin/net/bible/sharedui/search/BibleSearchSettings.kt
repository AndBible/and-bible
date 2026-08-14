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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.components.AbDropdownField
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * The SWORD/Bible search settings **form**, with no chrome of its own: the bible-section and
 * word-mode segmented rows plus the translations picker row (and the picker's dialog).
 *
 * Lifted verbatim out of [SearchScreen]'s settings sheet so the same form can be shown either in
 * that screen's `AbSettingsSummarySheet` or, in the reading view, in [SearchSettingsSheet] opened
 * from the toolbar. Emits its three children as plain siblings — it introduces no layout node of
 * its own, so hosting it inside a `Column` renders exactly as the inlined body did.
 *
 * @param initiallyDialogOpen open the translations dialog on first composition (deterministic
 *   golden capture only; mirrors the `initiallyOpen`/`initiallyChooserOpen` patterns elsewhere).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleSearchSettings(
    searchType: SearchType,
    bibleSection: SearchBibleSection,
    availableTranslations: List<Pair<String, String>>,
    selectedTranslationIds: List<String>,
    currentBookName: String,
    onSearchType: (SearchType) -> Unit,
    onBibleSection: (SearchBibleSection) -> Unit,
    onTranslations: (List<String>) -> Unit,
    initiallyDialogOpen: Boolean = false,
) {
    val strings = LocalStrings.current
    val types = SearchType.entries
    val typeLabels = listOf(strings.allWords, strings.anyWord, strings.phrase)

    var dialogOpen by remember { mutableStateOf(initiallyDialogOpen) }

    val translationsSummary = translationsSummaryOf(strings, availableTranslations, selectedTranslationIds)

    // F6-B4: four localized labels never fit four equal-width segments — they soft-wrapped MID-WORD
    // ("Old Testam / ent") and clipped, in English at 320dp and worse in Finnish. A dropdown absorbs
    // any label length, which is also what lets `CURRENT_BOOK` carry the open book's name.
    AbDropdownField(
        label = strings.searchWhere,
        selected = bibleSection,
        options = SearchBibleSection.entries,
        optionLabel = { searchSectionLabel(strings, it, currentBookName) },
        onSelect = onBibleSection,
    )

    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = searchType == type,
                onClick = { onSearchType(type) },
                shape = SegmentedButtonDefaults.itemShape(index, types.size),
            ) { Text(typeLabels[index]) }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(strings.chooseTranslations, style = MaterialTheme.typography.titleSmall)
            Text(translationsSummary, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = { dialogOpen = true }) {
            Icon(Icons.Filled.Edit, contentDescription = strings.chooseTranslations)
        }
    }

    if (dialogOpen) {
        TranslationsDialog(
            availableTranslations = availableTranslations,
            selectedTranslationIds = selectedTranslationIds,
            onConfirm = {
                onTranslations(it)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

/**
 * One-line read-only rendering of the current [BibleSearchSettings] selections:
 * `section · word mode · translations`.
 *
 * Takes [strings] as a parameter rather than reading `LocalStrings` so it stays a plain function:
 * its one caller, [SearchScreen], already has `LocalStrings.current` in hand and derives this
 * summary as a value, not as a composable. (An earlier version of this comment claimed the reading
 * toolbar needed a non-composable summary; the reading-view search mode does not use this at all —
 * F6's settings live in their own sheet, whose contents are the real [BibleSearchSettings].)
 */
fun bibleSearchSettingsSummary(
    strings: Strings,
    searchType: SearchType,
    bibleSection: SearchBibleSection,
    availableTranslations: List<Pair<String, String>>,
    selectedTranslationIds: List<String>,
    currentBookName: String,
): String {
    val types = SearchType.entries
    val typeLabels = listOf(strings.allWords, strings.anyWord, strings.phrase)
    val translationsSummary = translationsSummaryOf(strings, availableTranslations, selectedTranslationIds)
    return searchSectionLabel(strings, bibleSection, currentBookName) +
        " · ${typeLabels[types.indexOf(searchType)]}" +
        " · $translationsSummary"
}

/** Selected translation abbreviations, comma-joined; [Strings.all] when nothing is selected. */
private fun translationsSummaryOf(
    strings: Strings,
    availableTranslations: List<Pair<String, String>>,
    selectedTranslationIds: List<String>,
): String = availableTranslations
    .filter { it.first in selectedTranslationIds }
    .joinToString(", ") { it.second }
    .ifEmpty { strings.all }

/**
 * The label for one search scope. `CURRENT_BOOK` is named after the open book, which is what classic
 * did by overwriting its radio button's text at runtime (`Search.kt:168-179`,
 * `SearchControl.currentBookName`). The static label is the fallback for callers with no book to name
 * — `SearchScreen`'s `currentBookName` parameter is defaulted to `""`.
 */
internal fun searchSectionLabel(
    strings: Strings,
    section: SearchBibleSection,
    currentBookName: String,
): String = when (section) {
    SearchBibleSection.ALL -> strings.searchAllBible
    SearchBibleSection.OLD_TESTAMENT -> strings.searchOldTestament
    SearchBibleSection.NEW_TESTAMENT -> strings.searchNewTestament
    SearchBibleSection.CURRENT_BOOK -> currentBookName.ifBlank { strings.searchCurrentBook }
}
