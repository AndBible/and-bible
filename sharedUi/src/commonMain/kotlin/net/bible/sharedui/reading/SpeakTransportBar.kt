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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * decision). Hosted by the reading view **only** (round 13a T4 deleted the two Compose speak
 * screens, so this bar's only host always wants the settings cog); button callbacks are all
 * host-owned seams driven by `SpeakTransportController`.
 */
@Composable
fun SpeakTransportBar(
    state: SpeakTransportVd,
    onPlayPause: () -> Unit, onStop: () -> Unit, onRewind: () -> Unit, onForward: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, onBookmark: () -> Unit, onConfig: () -> Unit,
    /**
     * Round 12b §3: consume the bottom navigation-bar inset. `true` only when this bar is the
     * bottom-most one in the reading view (`agentLogOwnsNavBarInset`'s complement).
     *
     * `ime` is excluded because `MainBibleActivity.applyImePadding()` already pads the container
     * this whole Compose tree is installed into by `max(systemBars.bottom, ime.bottom)` whenever the
     * keyboard is up, and `windowInsetsPadding` is not consumption-aware — without the exclusion a
     * bar visible over an open keyboard would double-reserve and float a navigation-bar height above
     * it.
     */
    applyNavBarInset: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    // Round 13a: the agent panel's container verbatim (AgentLogPanel.kt:171-188) — the two panels
    // stack directly on top of each other in the reading view, so one idiom reads as one family.
    // No explicit `color`: Surface resolves surface + surfaceColorAtElevation(3.dp), a ROLE and not
    // a hue, so it greys correctly in the BW/e-ink modes. No border: corners plus shadow already
    // separate the bar from the text, which is why the agent panel dropped classic's 1dp divider.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        // The nav-bar inset goes on the INNER Column, not the Surface: the tinted surface and its
        // corners then bleed into the nav-bar strip while the content clears it. `ime` is excluded
        // because MainBibleActivity.applyImePadding() already pads this container when the keyboard
        // is up, and windowInsetsPadding is not consumption-aware.
        Column(
            Modifier.then(
                if (applyNavBarInset) {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                } else Modifier
            )
        ) {
            // Rendered unconditionally (even when statusText is blank), NOT gated by isNotBlank(): classic's
            // statusText is android:lines="1" (speak_transport_widget.xml:27-40, with a placeholder
            // android:text="test") so it ALWAYS occupies exactly one line and the widget's height never
            // changes with playback state. Gating this Text would make the bar's height jump whenever
            // speech starts/stops (A/B batch 2 F4 fix wave). An empty string still reserves its line
            // height, so the bar stays a constant height either way.
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
