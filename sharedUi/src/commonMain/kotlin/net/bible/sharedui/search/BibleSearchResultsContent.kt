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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.BibleOption
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedcore.search.TranslationMatchVd
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.UncappedFontScale
import net.bible.sharedui.strings.LocalStrings

/**
 * The SWORD/Bible result-list **actions**, with no layout node of its own: the translation-selector
 * chip, the scripture/deuterocanonical toggle and the overflow menu, emitted as plain siblings so
 * the hosting `Row` (a top app bar's `actions`, or [SearchSheetContent]'s header) lays them out
 * exactly as if they were written inline.
 *
 * Shared by the reading sheet header and its translation chooser dialog.
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
    openResultInLinksWindow: Boolean = false,
    onOpenResultInLinksWindowChange: (Boolean) -> Unit = {},
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
    SearchResultTargetActions(openResultInLinksWindow, onOpenResultInLinksWindowChange, onOpenInWindow)

    if (chooserOpen) {
        // Declared inside the top bar's actions, so it would inherit the bar's capped font scale: a dialog keeps the system scale.
        UncappedFontScale {
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
}

@Composable
fun SearchResultTargetActions(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenInWindow: (() -> Unit)? = null,
) {
    AbOverflowMenu(contentDescription = null) { close ->
        SearchResultTargetMenuRows(checked, onCheckedChange, onOpenInWindow, close)
    }
}

/** Menu rows shared with screenshot tests without opening a popup. */
@Composable
fun SearchResultTargetMenuRows(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenInWindow: (() -> Unit)?,
    close: () -> Unit,
) {
    val strings = LocalStrings.current
    if (onOpenInWindow != null) {
        AbMenuItem(
            text = strings.openResultsInWindow,
            onClick = { close(); onOpenInWindow() },
            icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
        )
    }
    AbMenuItem(
        text = strings.openResultInLinksWindow,
        onClick = { close(); onCheckedChange(!checked) },
        checkable = true,
        checked = checked,
    )
}

/**
 * The SWORD/Bible result rows as `LazyColumn` items — one [BibleResultCard] per verse reference,
 * keyed by reference name in the reading view's search sheet.
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

/** A verse card with collapsed translation chips and an optional per-translation breakdown. */
@Composable
internal fun BibleResultCard(
    row: SwordResultRow,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: (referenceName: String, translationId: String?) -> Unit,
    labelSingleMatchTranslation: Boolean = false,
) {
    val strings = LocalStrings.current
    val multi = row.matches.size > 1
    val cardModifier = Modifier.fillMaxWidth().let {
        if (!multi) it.clickable { onSelect(row.referenceName, row.matches.firstOrNull()?.translationId) } else it
    }
    Card(cardModifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.referenceName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (multi) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) strings.collapseRow else strings.expandRow,
                        )
                    }
                }
            }
            if (multi && expanded) {
                // F20: expanded = per-translation breakdown only (no primary duplication).
                Spacer(Modifier.height(4.dp))
                row.matches.forEach { match: TranslationMatchVd ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(row.referenceName, match.translationId) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(match.abbreviation, style = MaterialTheme.typography.labelSmall)
                        Text(
                            styledTextToAnnotatedString(match.preview),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                // Collapsed (single OR multi): one primary preview.
                if (!multi && labelSingleMatchTranslation) {
                    // F27: single-match card gets the matched-translation label (chips are multi-only).
                    row.matches.firstOrNull()?.let { match ->
                        Text(match.abbreviation, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                    }
                }
                Text(
                    styledTextToAnnotatedString(row.primaryPreview),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (multi) {
                    // F19: show which translations matched even while collapsed.
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.matches.forEach { match ->
                            AssistChip(
                                onClick = { onSelect(row.referenceName, match.translationId) },
                                label = { Text(match.abbreviation) },
                            )
                        }
                    }
                }
            }
        }
    }
}
