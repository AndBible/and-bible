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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedui.components.AbHelpMenuIcon
import net.bible.sharedui.components.AbLinkRouting
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbMessageDialog
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.strings.LocalStrings

/** Classic `EpubSearch.help()`'s FTS5 query-syntax link, fixed for every locale (URLs are never
 *  translated) -- shared by [EpubSearchScreen]'s own help dialog. */
private const val FTS5_QUERY_SYNTAX_URL = "https://www.sqlite.org/fts5.html#full_text_query_syntax"

/**
 * EPUB (general-book) search form: a query field, a single 4-way word-mode segmented picker
 * (all-words / any-word / phrase / raw FTS5), and a submit button; an overflow action links the
 * FTS5 query-syntax help. Simpler than the SWORD [SearchScreen] — no translations selector and no
 * bible-section group. Pure state-in / callbacks-out; the host owns the search.
 *
 * Platform-dialog removal Task 15: the FTS5 help ([helpOpen], driven by
 * [net.bible.sharedcore.search.EpubSearchFormController.helpOpen]) used to be a platform
 * `AlertDialog` with a `LinkMovementMethod`-enabled message view (classic `EpubSearch.help()`); it
 * is now this screen's own `AbMessageDialog`, built entirely from existing `Strings.kt` entries (no
 * host formatting needed, unlike App settings' discrete help). [askBeforeOpeningLink]/[onOpenExternal]
 * route the inline wiki link through `AbLinkRouting`, since this destination's ambient composition
 * locals are the bare platform ones otherwise (C1 fix: was a single `onOpenLink` calling
 * `CommonUtils.openLink`, which cannot ask through a `Dialog` window it is drawn inside of).
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
    helpOpen: Boolean = false,
    onDismissHelp: () -> Unit = {},
    askBeforeOpeningLink: Boolean = false,
    onOpenExternal: (String) -> Unit = {},
) {
    val strings = LocalStrings.current

    if (helpOpen) {
        AbLinkRouting(askFirst = askBeforeOpeningLink, onOpenExternal = onOpenExternal) {
            AbMessageDialog(
                title = strings.search,
                html = "${strings.helpSearchEpub}<br><br>${strings.helpSearchDetails(
                    "<a href=\"$FTS5_QUERY_SYNTAX_URL\">${strings.helpFts5}</a>",
                )}",
                confirmText = strings.okay,
                onConfirm = onDismissHelp,
                onDismissRequest = onDismissHelp,
            )
        }
    }

    AbScaffold(
        title = title,
        onNavigateUp = onNavigateUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                AbMenuItem(
                    text = strings.helpFts5,
                    onClick = { close(); onHelp() },
                    icon = AbHelpMenuIcon,
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
