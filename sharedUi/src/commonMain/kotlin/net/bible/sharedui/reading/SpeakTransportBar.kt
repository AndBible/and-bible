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

package net.bible.sharedui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.speak.SpeakBookmarkRowVd
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedui.components.AbActionIcon
import net.bible.sharedui.components.AbListChoiceDialog
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless Speak transport bar — Compose equivalent of the classic `speak_transport_widget.xml`
 * (status line + speed slider stacked above the transport button row). Hosted by the reading view;
 * button callbacks and the speed setter are all host-owned seams driven by `SpeakTransportController`.
 */
@Composable
fun SpeakTransportBar(
    state: SpeakTransportVd,
    onPlayPause: () -> Unit, onStop: () -> Unit, onRewind: () -> Unit, onForward: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onBookmark: () -> Unit, onConfig: () -> Unit,
    onSpeed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (state.statusText.isNotBlank()) {
            Text(
                state.statusText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        AbSliderRow(
            label = strings.speak, value = state.speedPercent, onValueChange = onSpeed,
            valueRange = 10f..300f, valueLabel = "${state.speedPercent}%",
            valueLabelFor = { "$it%" },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.bookmarkButtonVisible) {
                AbActionIcon(Icons.Filled.Bookmark, strings.bookmarks, onBookmark)
            }
            AbActionIcon(Icons.Filled.FastRewind, strings.rewind, onRewind)
            AbActionIcon(Icons.Filled.SkipPrevious, strings.speakPrevious, onPrev)
            AbActionIcon(Icons.Filled.Stop, strings.stop, onStop)
            AbActionIcon(
                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                strings.speak, onPlayPause,
            )
            AbActionIcon(Icons.Filled.SkipNext, strings.speakNext, onNext)
            AbActionIcon(Icons.Filled.FastForward, strings.forward, onForward)
            AbActionIcon(Icons.Filled.Settings, strings.speak, onConfig)
        }
    }
}

/** Speak-from-bookmark chooser — single-select list dialog over the current window's bookmarks. */
@Composable
fun ChooseSpeakBookmarkDialog(
    rows: List<SpeakBookmarkRowVd>, onChoose: (String) -> Unit, onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AbListChoiceDialog(
        title = strings.speakBookmarksMenuTitle,
        choices = rows.map { SettingsItem.Choice(it.id, it.label) },
        selectedValue = "",
        onSelect = onChoose,
        onDismiss = onDismiss,
    )
}
