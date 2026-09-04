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
package net.bible.sharedui.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.download.CustomRepoListState
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.TwoLineListItem
import net.bible.sharedui.strings.LocalStrings

/**
 * The manual page documenting the custom-repository manifest format. Moved off the GitHub wiki
 * 2026-08-29 (round 17e): the wiki page no longer exists and the content lives in the manual.
 *
 * Public, not internal: `:app`'s CustomRepositoryHelpUrlTest asserts this and the classic copy in
 * `net.bible.android.view.activity.download` are the same string, and `internal` is scoped to the
 * compilation module.
 */
const val customRepositoriesHelpUrl = "https://docs.andbible.org/en/latest/custom_repositories.html"

/**
 * Stateless port of classic `CustomRepositories`: a list of user-added custom Sword/MyBible
 * repositories. Row tap navigates to the editor pre-filled with that repository (host concern);
 * [onCreate] navigates to a blank editor; [onUp] finishes the screen.
 *
 * The help icon opens this composable's own [AbInfoDialog] (mirrors classic's `help()` custom
 * `AlertDialog`, body built from `custom_repositories_help0`/`help2` + a clickable wiki-page link)
 * -- fully self-contained, no host wiring needed or possible for the dialog (mirrors the AI screens'
 * convention, e.g. `PromptEditScreen`/`ToolInfoScreen`: a screen either owns its help dialog with no
 * `onHelp` param, or takes `onHelp` with no internal dialog -- never both).
 */
@Composable
fun CustomRepositoriesScreen(
    state: CustomRepoListState,
    onRowClick: (Long) -> Unit,
    onCreate: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var showHelp by remember { mutableStateOf(false) }

    AbScaffold(
        title = strings.customRepositories,
        onNavigateUp = onUp,
        actions = {
            AbActionIcon(Icons.Filled.Add, contentDescription = strings.newItem, onClick = onCreate)
            AbActionIcon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = strings.help) {
                showHelp = true
            }
        },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = strings.customRepositoriesGuidance,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(modifier = modifier.fillMaxSize().padding(padding)) {
                items(state.rows, key = { it.id }) { row ->
                    TwoLineListItem(
                        title = row.name,
                        subtitle = row.description,
                        onClick = { onRowClick(row.id) },
                    )
                }
            }
        }
    }

    if (showHelp) {
        AbInfoDialog(
            title = strings.customRepositories,
            body = "${strings.customRepositoriesHelp0}\n\n${strings.customRepositoriesHelp2(strings.helpReadMoreLink)}",
            onDismiss = { showHelp = false },
            readMoreLabel = strings.helpReadMoreLink,
            readMoreUrl = customRepositoriesHelpUrl,
        )
    }
}
