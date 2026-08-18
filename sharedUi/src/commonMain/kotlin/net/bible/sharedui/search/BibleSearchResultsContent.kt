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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.BibleOption
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.strings.LocalStrings

/**
 * The SWORD/Bible result-list **actions**, with no layout node of its own: the translation-selector
 * chip, the scripture/deuterocanonical toggle and the overflow menu, emitted as plain siblings so
 * the hosting `Row` (a top app bar's `actions`, or [SearchSheetContent]'s header) lays them out
 * exactly as if they were written inline.
 *
 * Lifted verbatim out of [SearchResultsScreen]'s toolbar, together with the chooser dialog it opens.
 * The chooser's open state lives here (not in the caller) because both the affordance and the dialog
 * are part of this action set.
 *
 * @param selectedAbbreviations comma-joined abbreviations of the currently-searched translations
 *   (e.g. "KJV" or "KJV, BSB"), shown on the chip.
 * @param initiallyChooserOpen open the translation chooser on first composition (deterministic
 *   golden capture; false in production, where the user taps the chip to open it).
 */
@Composable
fun BibleResultsActions(
    candidates: List<BibleOption>,
    selectedIds: List<String>,
    selectedAbbreviations: String,
    scriptureToggleVisible: Boolean,
    scriptureShown: Boolean,
    onToggleScripture: () -> Unit,
    onOpenInWindow: () -> Unit,
    onSelectTranslations: (List<String>) -> Unit,
    initiallyChooserOpen: Boolean = false,
) {
    val strings = LocalStrings.current
    var chooserOpen by remember { mutableStateOf(initiallyChooserOpen) }

    if (candidates.isNotEmpty()) {
        AssistChip(
            onClick = { chooserOpen = true },
            label = { Text(selectedAbbreviations) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            modifier = Modifier
                .padding(end = 4.dp)
                .semantics { contentDescription = strings.chooseTranslations },
        )
    }
    if (scriptureToggleVisible) {
        AbActionIcon(
            icon = if (scriptureShown) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Book,
            contentDescription = if (scriptureShown) strings.deuterocanonical else strings.bible,
            onClick = onToggleScripture,
        )
    }
    AbOverflowMenu(contentDescription = null) { close ->
        AbMenuItem(
            text = strings.openResultsInWindow,
            onClick = { close(); onOpenInWindow() },
            icon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
        )
    }

    if (chooserOpen) {
        AbMultiSelectDialog(
            title = strings.chooseTranslations,
            options = candidates,
            selectedIds = selectedIds,
            idOf = { it.id },
            labelOf = { it.abbreviation },
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { ids -> chooserOpen = false; onSelectTranslations(ids) },
            onDismiss = { chooserOpen = false },
        )
    }
}

/**
 * The SWORD/Bible result rows as `LazyColumn` items — one [BibleResultCard] per verse reference,
 * keyed by reference name, so the same rows can be listed either in [SearchResultsScreen] or in the
 * reading view's search sheet.
 *
 * @param expanded which reference names are expanded; owned by the caller (the host hoists it so
 *   expansion survives a re-search). A `SnapshotStateMap` is what makes toggling recompose.
 * @param labelSingleMatchTranslation F27: label a single-match card with its matched translation.
 *   Only meaningful when more than one translation is being searched — the caller decides.
 */
fun LazyListScope.bibleResultRows(
    rows: List<SwordResultRow>,
    expanded: MutableMap<String, Boolean>,
    labelSingleMatchTranslation: Boolean,
    onSelect: (referenceName: String, translationId: String?) -> Unit,
) {
    items(rows, key = { it.referenceName }) { row ->
        BibleResultCard(
            row = row,
            expanded = expanded[row.referenceName] == true,
            onToggleExpand = { expanded[row.referenceName] = !(expanded[row.referenceName] == true) },
            onSelect = onSelect,
            labelSingleMatchTranslation = labelSingleMatchTranslation,
        )
    }
}
