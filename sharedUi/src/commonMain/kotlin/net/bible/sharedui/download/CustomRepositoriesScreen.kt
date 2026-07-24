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
 * Wiki page documenting custom-repository manifest format (classic `customRepositoriesWikiUrl`,
 * `net.bible.android.view.activity.download.CustomRepositories`). Shared by [CustomRepositoriesScreen]
 * and [CustomRepositoryEditorScreen] as the help dialogs' "read more" link target.
 */
internal const val customRepositoriesWikiUrl = "https://github.com/AndBible/and-bible/wiki/Custom-repositories"

/**
 * Stateless port of classic `CustomRepositories`: a list of user-added custom Sword/MyBible
 * repositories. Row tap navigates to the editor pre-filled with that repository (host concern);
 * [onCreate] navigates to a blank editor; [onUp] finishes the screen.
 *
 * The help icon opens this composable's own [AbInfoDialog] (mirrors classic's `help()` custom
 * `AlertDialog`, body built from `custom_repositories_help0`/`help2` + a clickable wiki-page link)
 * -- self-contained, no host wiring needed for the dialog to work. [onHelp] is still invoked so the
 * host can observe the event (parity with other `onHelp`-taking screens in this codebase, e.g.
 * `WorkspaceSelectorScreen`).
 */
@Composable
fun CustomRepositoriesScreen(
    state: CustomRepoListState,
    onRowClick: (Long) -> Unit,
    onCreate: () -> Unit,
    onHelp: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    // Test-only hook (same `initiallyXxxOpen` pattern as e.g. `PromptEditScreen`/`AbOverflowMenu`) so
    // a golden test can capture the help dialog open without simulating a click on the help icon.
    initiallyHelpDialogOpen: Boolean = false,
) {
    val strings = LocalStrings.current
    var showHelp by remember { mutableStateOf(initiallyHelpDialogOpen) }

    AbScaffold(
        title = strings.customRepositories,
        onNavigateUp = onUp,
        actions = {
            AbActionIcon(Icons.Filled.Add, contentDescription = strings.newItem, onClick = onCreate)
            AbActionIcon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = strings.help) {
                showHelp = true
                onHelp()
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
            body = "${strings.customRepositoriesHelp0}\n\n${strings.customRepositoriesHelp2(strings.wikiPage)}",
            onDismiss = { showHelp = false },
            readMoreLabel = strings.wikiPage,
            readMoreUrl = customRepositoriesWikiUrl,
        )
    }
}
