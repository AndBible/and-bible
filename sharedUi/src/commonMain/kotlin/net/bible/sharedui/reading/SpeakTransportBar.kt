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
import net.bible.sharedui.strings.LocalStrings

/**
 * Stateless Speak transport bar — Compose equivalent of the classic `speak_transport_widget.xml`
 * (status line stacked above the transport button row; no speed slider — A/B batch 2 F4, maintainer
 * decision). Hosted by the reading view **and both speak screens**; button callbacks are all
 * host-owned seams driven by `SpeakTransportController`. `showConfig` mirrors classic's
 * `custom:showConfig` attribute (`true` in `main_bible_view.xml:201`, default `false` in the two
 * speak layouts) — the settings-cog button is only shown where the layout asks for it.
 */
@Composable
fun SpeakTransportBar(
    state: SpeakTransportVd,
    onPlayPause: () -> Unit, onStop: () -> Unit, onRewind: () -> Unit, onForward: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onBookmark: () -> Unit, onConfig: () -> Unit,
    showConfig: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Rendered unconditionally (even when statusText is blank), NOT gated by isNotBlank(): classic's
        // statusText is android:lines="1" (speak_transport_widget.xml:27-40, with a placeholder
        // android:text="test") so it ALWAYS occupies exactly one line and the widget's height never
        // changes with playback state. Now that this bar is a Scaffold bottomBar on the speak screens,
        // gating this Text would make the reserved content padding -- and the whole scroll extent --
        // jump by a line's height whenever speech starts/stops (A/B batch 2 F4 fix wave). An empty
        // string still reserves its line height, so the bar stays a constant height either way.
        Text(
            state.statusText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.bookmarkButtonVisible) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AbActionIcon(Icons.Filled.Bookmark, strings.bookmarks, onBookmark)
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AbActionIcon(Icons.Filled.FastRewind, strings.rewind, onRewind)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AbActionIcon(Icons.Filled.SkipPrevious, strings.speakPrevious, onPrev)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AbActionIcon(Icons.Filled.Stop, strings.stop, onStop)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AbActionIcon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    strings.speak, onPlayPause,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AbActionIcon(Icons.Filled.SkipNext, strings.speakNext, onNext)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AbActionIcon(Icons.Filled.FastForward, strings.forward, onForward)
            }
            if (showConfig) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    AbActionIcon(Icons.Filled.Settings, strings.speak, onConfig)
                }
            }
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
