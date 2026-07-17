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

package net.bible.sharedui.speak

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.speak.AdvancedSpeakVd
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings

/** Advanced (rarely-changed) Speak settings — classic SpeakSettingsActivity. Kept a separate screen
 *  from the main Speak screen so the common playback controls aren't cluttered by these. */
@Composable
fun AdvancedSpeakSettingsScreen(
    advanced: AdvancedSpeakVd,
    onSynchronize: (Boolean) -> Unit,
    onReplaceDivineName: (Boolean) -> Unit,
    onAutoBookmark: (Boolean) -> Unit,
    onRestoreSettingsFromBookmarks: (Boolean) -> Unit,
    onHelp: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(
        title = strings.speakSettingsTitle,
        onNavigateUp = onNavigateUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); onHelp() })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                strings.speakSettingsTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            AbSwitchRow(strings.confSpeakSynchronize, advanced.synchronize, onSynchronize)
            AbSwitchRow(strings.confReplaceDivinename, advanced.replaceDivineName, onReplaceDivineName)

            Text(
                strings.speakBookmarkingSettingsTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            )
            AbSwitchRow(strings.confSpeakAutoBookmark, advanced.autoBookmark, onAutoBookmark)
            AbSwitchRow(strings.confSavePlaybackSettingsToBookmarks, advanced.restoreSettingsFromBookmarks, onRestoreSettingsFromBookmarks)
        }
    }
}
