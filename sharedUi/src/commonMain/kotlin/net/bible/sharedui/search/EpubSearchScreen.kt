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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.strings.LocalStrings

/**
 * EPUB (general-book) search form: a query field, a single 4-way word-mode segmented picker
 * (all-words / any-word / phrase / raw FTS5), and a submit button; an overflow action links the
 * FTS5 query-syntax help. Simpler than the SWORD [SearchScreen] — no translations selector and no
 * bible-section group. Pure state-in / callbacks-out; the host owns the search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubSearchScreen(
    title: String,
    query: String,
    mode: EpubSearchMode,
    onQueryChange: (String) -> Unit,
    onMode: (EpubSearchMode) -> Unit,
    onSubmit: () -> Unit,
    onHelp: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current

    AbScaffold(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                AbMenuItem(
                    text = strings.helpFts5,
                    onClick = { close(); onHelp() },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            AbSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = strings.search,
                onImeSearch = onSubmit,
            )

            EpubSearchSettings(mode = mode, onMode = onMode)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(strings.search) }
        }
    }
}
