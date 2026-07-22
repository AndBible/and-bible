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
package net.bible.sharedui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.startup.StartupWelcomeState
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.strings.LocalStrings

/**
 * First-run welcome screen (new-path twin of classic `StartupActivity.showFirstLayout()`).
 * Moderate M3 modernization: a welcome card, the "install from files" blurb, and the action
 * buttons shown per [StartupWelcomeState]. All actions are host seams. No top app bar (this is a
 * launcher-context screen, like the classic splash which hides the action bar).
 */
@Composable
fun StartupWelcomeScreen(
    state: StartupWelcomeState,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
    onRedownload: () -> Unit,
    onEasyStart: () -> Unit,
    onOpenHomepage: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.welcomeText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (state.progressText != null) {
            AbLoadingIndicator(modifier = Modifier.fillMaxWidth())
            Text(state.progressText!!, style = MaterialTheme.typography.bodySmall)
        }

        // Primary action: download from repository.
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text(strings.welcomeDownloadButton)
        }

        if (state.showEasyStart) {
            Text(
                state.easyStartMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onEasyStart, modifier = Modifier.fillMaxWidth()) {
                Text(strings.welcomeEasyStartButton)
            }
        }

        // Install from files (zip / MyBible / MySword / EPUB).
        Text(
            strings.welcomeImportButton,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            state.supportedFormatsText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(strings.welcomeImportButton)
        }

        if (state.showRedownload) {
            Text(
                state.redownloadMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onRedownload, modifier = Modifier.fillMaxWidth()) {
                Text(strings.welcomeRedownloadButton)
            }
        }

        if (state.showRestore) {
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text(strings.welcomeRestoreButton)
            }
        }

        Text(state.versionText, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onOpenHomepage) { Text(strings.welcomeHomepageLabel) }
        TextButton(onClick = onOpenGithub) { Text(strings.welcomeGithubLabel) }
    }
}
