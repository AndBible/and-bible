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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedcore.search.TranslationMatchVd
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbTopAppBar
import net.bible.sharedui.strings.LocalStrings

/**
 * SWORD multi-translation search results. Pure state-in / callbacks-out: the host owns the search
 * and window navigation. Each row is a verse reference; when the same verse matched in more than one
 * translation the card expands to a [FlowRow] of translation chips plus per-translation previews.
 * A single-match card is flat and directly clickable. Highlighted query terms come through the
 * host-built [net.bible.sharedcore.search.StyledText] previews rendered via [styledTextToAnnotatedString].
 *
 * @param initiallyExpanded reference names whose card starts expanded (deterministic golden capture;
 *   empty in production, where the user drives expansion).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    title: String,
    loading: Boolean,
    rows: List<SwordResultRow>,
    scriptureToggleVisible: Boolean,
    scriptureShown: Boolean,
    onToggleScripture: () -> Unit,
    onOpenInWindow: () -> Unit,
    onSelect: (referenceName: String, translationId: String?) -> Unit,
    onNavigateUp: () -> Unit,
    initiallyExpanded: Set<String> = emptySet(),
) {
    val strings = LocalStrings.current
    val expanded = remember { mutableStateMapOf<String, Boolean>().apply { initiallyExpanded.forEach { put(it, true) } } }

    AbScaffold(
        topBar = {
            AbTopAppBar(
                title = { Text(title) },
                onNavigateUp = onNavigateUp,
                actions = {
                    if (scriptureToggleVisible) {
                        IconButton(onClick = onToggleScripture) {
                            Icon(
                                imageVector = if (scriptureShown) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Book,
                                contentDescription = if (scriptureShown) strings.deuterocanonical else strings.bible,
                            )
                        }
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(strings.openResultsInWindow) },
                            onClick = { menuOpen = false; onOpenInWindow() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> AbLoadingIndicator(
                Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            )
            rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // No dedicated "no results" string exists yet; reuse the adjacent `search` key
                // (rendered centered) rather than adding a resource, per task brief.
                Text(strings.search, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.referenceName }) { row ->
                    ResultCard(
                        row = row,
                        expanded = expanded[row.referenceName] == true,
                        onToggleExpand = { expanded[row.referenceName] = !(expanded[row.referenceName] == true) },
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    row: SwordResultRow,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: (referenceName: String, translationId: String?) -> Unit,
) {
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
                            contentDescription = null,
                        )
                    }
                }
            }
            Text(
                styledTextToAnnotatedString(row.primaryPreview),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (multi && expanded) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.matches.forEach { match ->
                        AssistChip(
                            onClick = { onSelect(row.referenceName, match.translationId) },
                            label = { Text(match.abbreviation) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                row.matches.forEach { match: TranslationMatchVd ->
                    Text(
                        match.abbreviation,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        styledTextToAnnotatedString(match.preview),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
