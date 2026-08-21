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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.speak.SpeakSheetPage
import net.bible.sharedui.strings.LocalStrings

/**
 * The Speak bottom sheet's shell. Renders nothing when [page] is null, so a host can render it
 * unconditionally — the self-hiding contract the reading view needs for a sibling overlay.
 *
 * [onDismiss] is wired to `onDismissRequest`; a host maps it to `SpeakSheetStack.pop()`, which steps
 * back one page and closes at depth 1 (Material3 cannot tell back/scrim/swipe apart, and
 * `BackHandler` cannot live in commonMain). [onClose] is the header ✕, which always closes outright.
 *
 * ROBORAZZI: never capture this composable with a non-null [page] — an open `ModalBottomSheet` hangs
 * the capture and takes the whole `:app` suite with it. Golden each page's `*Content` composable in
 * a plain `Column` instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakSettingsSheet(
    page: SpeakSheetPage?,
    depth: Int,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(SpeakSheetPage) -> Unit,
) {
    if (page == null) return
    val strings = LocalStrings.current
    val title = when (page) {
        SpeakSheetPage.Settings -> strings.speakTitle
        SpeakSheetPage.Advanced -> strings.speakAdvancedSettings
        SpeakSheetPage.RepeatRange -> strings.repeatPassage
        is SpeakSheetPage.PickVerse ->
            if (page.end) strings.speakEndingOfPassage else strings.speakBeginningOfPassage
        SpeakSheetPage.SleepTimer -> strings.speakSleepTimerTitle
    }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (depth > 1) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.settingsEditorBack)
                    }
                } else {
                    Spacer(Modifier.padding(horizontal = 12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = strings.settingsEditorClose)
                }
            }
            // Every scrolling body must be bounded from HERE, not from its own modifier: a sheet's
            // content column is height-unbounded and an unbounded lazy list crashes. 400dp is round
            // 12c's shipped cap (SettingsEditorSheet.kt:252) — the only value here with device
            // evidence behind it — and the book grid lands on the same 40dp cell floor at 400dp as
            // at 440dp, so nothing about the grid changes. A larger cap would overflow the sheet in
            // landscape (window height ~360dp) and clip a scrolling list's bottom out of reach.
            Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column { content(page) }
            }
        }
    }
}
