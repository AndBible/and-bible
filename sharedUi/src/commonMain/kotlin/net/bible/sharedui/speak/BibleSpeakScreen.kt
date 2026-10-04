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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.bible.sharedcore.speak.SpeakPlaybackVd
import net.bible.sharedui.components.AbHelpMenuIcon
import net.bible.sharedui.components.AbSettingsCategoryHeader
import net.bible.sharedui.components.AbSettingsRow
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings

/**
 * The Speak settings list — the first page of the Speak bottom sheet (round 13a). Settings-shaped:
 * the same row primitives, 16/12dp geometry and section headers as every other settings surface,
 * with an icon on every row.
 *
 * A `*Content` composable, not a screen: it carries no scaffold and no sheet, so a Roborazzi golden
 * can capture it in a plain `Column` (an open `ModalBottomSheet` hangs the capture). Its host bounds
 * the height; this composable scrolls inside whatever it is given.
 */
@Composable
fun SpeakSettingsContent(
    playback: SpeakPlaybackVd,
    onSpeedChange: (Int) -> Unit,
    onSpeakChapterChanges: (Boolean) -> Unit,
    onSpeakTitles: (Boolean) -> Unit,
    onSpeakFootnotes: (Boolean) -> Unit,
    onOpenRepeatRange: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onSystemTtsSettings: () -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Hoistable scroll state (round 14b §7.b). Defaulted, and that default is load-bearing: this
     * composable is captured standalone by `BibleSpeakGoldenTest` and rendered by the Speak sheet,
     * and only the sheet needs the state — it reads `canScrollForward` to drive
     * `Modifier.abBottomFade`, the affordance that tells the user there is more below the clip.
     * Making the parameter required would have changed every caller for the benefit of one.
     */
    scrollState: ScrollState = rememberScrollState(),
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth().verticalScroll(scrollState)) {
        AbSettingsCategoryHeader(strings.playbackSettingsTitle)
        AbSliderRow(
            label = strings.speakSpeedTitle,
            value = playback.speedPercent,
            onValueChange = onSpeedChange,
            valueRange = 0f..300f,   // mirrors classic SeekBar android:max="300"
            valueLabel = "${playback.speedPercent} %",
            // The whole point of this argument: without it AbSliderRow falls back to the static
            // valueLabel and the readout freezes mid-drag until the release round-trips.
            valueLabelFor = { "$it %" },
            leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null) },
        )
        AbSwitchRow(
            strings.confChangeChapter, playback.speakChapterChanges, onSpeakChapterChanges,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
        )
        AbSwitchRow(
            strings.confChangeTitle, playback.speakTitles, onSpeakTitles,
            leadingIcon = { Icon(Icons.Filled.Title, contentDescription = null) },
        )
        AbSwitchRow(
            strings.confSpeakFootnotes, playback.speakFootnotes, onSpeakFootnotes,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
        )

        AbSettingsCategoryHeader(strings.repeatPassage)
        AbSettingsRow(
            title = strings.setRepeatPassageRange,
            summary = playback.repeatRangeName ?: strings.speakVerseRangeToRepeat,
            enabled = true,
            onClick = onOpenRepeatRange,
            leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )

        AbSettingsCategoryHeader(strings.speakSleepTimerTitle)
        AbSettingsRow(
            title = strings.confSpeakSleepTimer,
            summary = playback.sleepTimerMinutes
                .takeIf { it > 0 }
                ?.let { strings.speakSleepTimerMinutes(it) },
            enabled = true,
            onClick = onOpenSleepTimer,
            leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )

        AbSettingsCategoryHeader(strings.speakOtherSettings)
        AbSettingsRow(
            title = strings.speakAdvancedSettings, summary = null, enabled = true,
            onClick = onOpenAdvanced,
            leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
        AbSettingsRow(
            title = strings.systemSpeakSettings, summary = null, enabled = true,
            onClick = onSystemTtsSettings,
            leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) },
        )
        AbSettingsRow(
            title = strings.helpLabel, summary = null, enabled = true,
            onClick = onHelp,
            leadingIcon = AbHelpMenuIcon,
        )
    }
}
