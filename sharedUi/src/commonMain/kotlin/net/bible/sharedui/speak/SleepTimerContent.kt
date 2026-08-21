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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.speak.SLEEP_TIMER_MAX
import net.bible.sharedcore.speak.SLEEP_TIMER_MIN
import net.bible.sharedcore.speak.SLEEP_TIMER_PRESETS
import net.bible.sharedcore.speak.SleepTimerSelection
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Sleep-timer duration. Chips commit on tap (a sheet page that writes immediately needs no OK);
 * "Custom…" reveals the 1..120 slider, which commits once per drag gesture on release.
 *
 * A stored value matching no preset selects Custom… and opens the slider on it, so a timer
 * previously set to an odd number is visible and editable rather than silently absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerContent(
    selection: SleepTimerSelection,
    customMinutes: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var customOpen by remember(selection) { mutableStateOf(selection is SleepTimerSelection.Custom) }
    var sliderMinutes by remember(customMinutes) { mutableIntStateOf(customMinutes) }
    Column(modifier.fillMaxWidth()) {
        Text(
            strings.sleepTimerTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selection is SleepTimerSelection.Off,
                onClick = { customOpen = false; onPick(0) },
                label = { Text(strings.speakSleepTimerOff) },
            )
            SLEEP_TIMER_PRESETS.forEach { minutes ->
                FilterChip(
                    selected = selection == SleepTimerSelection.Preset(minutes),
                    onClick = { customOpen = false; onPick(minutes) },
                    label = { Text(minutes.toString()) },
                )
            }
            FilterChip(
                selected = customOpen || selection is SleepTimerSelection.Custom,
                onClick = { customOpen = true },
                label = { Text(strings.speakSleepTimerCustom) },
            )
        }
        if (customOpen) {
            AbSliderRow(
                label = strings.speakSleepTimerTitle,
                value = sliderMinutes,
                onValueChange = { sliderMinutes = it; onPick(it) },
                valueRange = SLEEP_TIMER_MIN.toFloat()..SLEEP_TIMER_MAX.toFloat(),
                valueLabel = strings.speakSleepTimerMinutes(sliderMinutes),
                valueLabelFor = { strings.speakSleepTimerMinutes(it) },
                leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
            )
        }
    }
}
