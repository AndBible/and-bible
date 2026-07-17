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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.strings.LocalStrings

/**
 * SWORD search form: query text, bible-section and word-mode segmented pickers, a translations
 * multi-select, and a submit button. Pure state-in / callbacks-out; the host owns the search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    title: String,
    query: String,
    searchType: SearchType,
    bibleSection: SearchBibleSection,
    availableTranslations: List<Pair<String, String>>,
    selectedTranslationIds: List<String>,
    onQueryChange: (String) -> Unit,
    onSearchType: (SearchType) -> Unit,
    onBibleSection: (SearchBibleSection) -> Unit,
    onTranslations: (List<String>) -> Unit,
    onSubmit: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    val sections = SearchBibleSection.entries
    val sectionLabels = listOf(
        strings.searchAllBible,
        strings.searchOldTestament,
        strings.searchNewTestament,
        strings.searchCurrentBook,
    )
    val types = SearchType.entries
    val typeLabels = listOf(strings.allWords, strings.anyWord, strings.phrase)

    var dialogOpen by remember { mutableStateOf(false) }

    AbScaffold(title = title, onNavigateUp = onNavigateUp) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            AbSearchField(value = query, onValueChange = onQueryChange, placeholder = strings.search)

            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                sections.forEachIndexed { index, section ->
                    SegmentedButton(
                        selected = bibleSection == section,
                        onClick = { onBibleSection(section) },
                        shape = SegmentedButtonDefaults.itemShape(index, sections.size),
                        icon = {}, // no default check icon: 4 long labels need the full width
                    ) { Text(sectionLabels[index], maxLines = 1) }
                }
            }

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
                    val summary = availableTranslations
                        .filter { it.first in selectedTranslationIds }
                        .joinToString(", ") { it.second }
                    Text(
                        summary.ifEmpty { strings.all },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = { dialogOpen = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = strings.chooseTranslations)
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(strings.search) }
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

@Composable
private fun TranslationsDialog(
    availableTranslations: List<Pair<String, String>>,
    selectedTranslationIds: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val working: SnapshotStateList<String> =
        remember { selectedTranslationIds.toMutableStateList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.chooseTranslations) },
        text = {
            Column {
                availableTranslations.forEach { (id, abbreviation) ->
                    val checked = id in working
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                onValueChange = { if (it) working.add(id) else working.remove(id) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            abbreviation,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(working.toList()) }) { Text(strings.okay) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
