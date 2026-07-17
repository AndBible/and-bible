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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.components.AbScaffold
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Main Speak settings screen (classic BibleSpeakActivity). Playback earcons + speed + sleep-timer +
 * repeat-passage; an "Advanced settings" footer row navigates to the advanced screen (that entry was
 * an overflow item in classic — surfaced here for discoverability while keeping the two screens
 * separate). System-TTS + help stay in the overflow. The in-Activity transport widget is intentionally
 * not ported (see plan Global Constraints).
 */
@Composable
fun BibleSpeakScreen(
    playback: SpeakPlaybackVd,
    onSpeedChange: (Int) -> Unit,
    onSpeakChapterChanges: (Boolean) -> Unit,
    onSpeakTitles: (Boolean) -> Unit,
    onSpeakFootnotes: (Boolean) -> Unit,
    onSleepTimerToggle: (Boolean) -> Unit,
    onToggleRepeatRange: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onSystemTtsSettings: () -> Unit,
    onHelp: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val strings = LocalStrings.current
    AbScaffold(
        title = strings.speakTitle,
        onNavigateUp = onNavigateUp,
        actions = {
            AbOverflowMenu(contentDescription = null) { close ->
                DropdownMenuItem(text = { Text(strings.systemSpeakSettings) }, onClick = { close(); onSystemTtsSettings() })
                DropdownMenuItem(text = { Text(strings.helpLabel) }, onClick = { close(); onHelp() })
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(strings.playbackSettingsTitle)
            Text(
                strings.speakAndPlayEarconsTitle,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            AbSwitchRow(strings.confChangeChapter, playback.speakChapterChanges, onSpeakChapterChanges)
            AbSwitchRow(strings.confChangeTitle, playback.speakTitles, onSpeakTitles)
            AbSwitchRow(strings.confSpeakFootnotes, playback.speakFootnotes, onSpeakFootnotes)

            AbSliderRow(
                label = strings.speakSpeedTitle,
                value = playback.speedPercent,
                onValueChange = onSpeedChange,
                valueRange = 0f..300f,   // mirrors classic SeekBar android:max="300"
                valueLabel = "${playback.speedPercent} %",
            )

            SectionHeader(strings.repeatPassage)
            AbSwitchRow(
                label = playback.repeatRangeName ?: strings.setRepeatPassageRange,
                checked = playback.repeatRangeName != null,
                onCheckedChange = { onToggleRepeatRange() },
            )

            SectionHeader(strings.speakSleepTimerTitle)
            AbSwitchRow(
                label = if (playback.sleepTimerMinutes > 0) strings.sleepTimerSet(playback.sleepTimerMinutes)
                        else strings.confSpeakSleepTimer,
                checked = playback.sleepTimerMinutes > 0,
                onCheckedChange = onSleepTimerToggle,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAdvanced).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.speakAdvancedSettings,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)
