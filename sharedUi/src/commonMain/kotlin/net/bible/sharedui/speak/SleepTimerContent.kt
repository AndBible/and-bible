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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 *
 * [onPick] carries `closeAfter`, which is what tells the host a CHIP was tapped (spec §6.7: a chip
 * commits and closes the page, Custom… stays open so the value can be adjusted). The host cannot
 * infer it from the minute value: a custom slider released on exactly 30 is indistinguishable from
 * the 30 chip by number alone, and treating it as a chip both collapsed the slider and closed the
 * page mid-adjustment.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerContent(
    selection: SleepTimerSelection,
    customMinutes: Int,
    onPick: (minutes: Int, closeAfter: Boolean) -> Unit,
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
                // `&& !customOpen`: from an off timer, tapping Custom… leaves `selection` at Off
                // until the first release writes a value, so without this both chips read as checked.
                selected = selection is SleepTimerSelection.Off && !customOpen,
                onClick = { customOpen = false; onPick(0, true) },
                label = { Text(strings.speakSleepTimerOff) },
            )
            SLEEP_TIMER_PRESETS.forEach { minutes ->
                FilterChip(
                    selected = selection == SleepTimerSelection.Preset(minutes),
                    onClick = { customOpen = false; onPick(minutes, true) },
                    // The unit lives in the caption above, so the chip label is a bare number by
                    // design — which TalkBack would read as just "30". The semantics label restores
                    // it without changing a pixel.
                    label = {
                        Text(
                            minutes.toString(),
                            modifier = Modifier.semantics {
                                contentDescription = strings.speakSleepTimerMinutes(minutes)
                            },
                        )
                    },
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
                // AbSliderRow fires this once per gesture, on release — so this IS the commit, and
                // `closeAfter = false` keeps the page open for another adjustment.
                onValueChange = { sliderMinutes = it; onPick(it, false) },
                valueRange = SLEEP_TIMER_MIN.toFloat()..SLEEP_TIMER_MAX.toFloat(),
                valueLabel = strings.speakSleepTimerMinutes(sliderMinutes),
                valueLabelFor = { strings.speakSleepTimerMinutes(it) },
                leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
            )
        }
    }
}
