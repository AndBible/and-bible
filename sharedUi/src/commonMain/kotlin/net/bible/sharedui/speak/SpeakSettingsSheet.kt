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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.speak.SpeakSheetPage
import net.bible.sharedui.components.abBottomFade
import net.bible.sharedui.strings.LocalStrings

/**
 * The Speak bottom sheet's shell. Renders nothing when [page] is null, so a host can render it
 * unconditionally — the self-hiding contract the reading view needs for a sibling overlay.
 *
 * [onDismiss] is wired to `onDismissRequest`; a host maps it to `SpeakSheetStack.pop()`, which steps
 * back one page and closes at depth 1 (Material3 cannot tell back/scrim/swipe apart, and
 * `BackHandler` cannot live in commonMain). That is deliberate: DO NOT "fix" it into a whole-sheet
 * dismiss, or the page stack becomes unreachable by back. [onClose] is the header ✕, which always
 * closes outright.
 *
 * ROBORAZZI: never capture this composable with a non-null [page] — an open `ModalBottomSheet` hangs
 * the capture and takes the whole `:app` suite with it. Golden each page's `*Content` composable in
 * a plain `Column` instead.
 *
 * Round 14b §7.b: this shell owns the bounded scroll region's state and hands it to [content] as a
 * second parameter, so it can read `canScrollForward` itself and fade the bottom edge while content
 * remains below the clip. A page whose body does not scroll simply ignores the parameter — its
 * `maxValue` stays 0, `canScrollForward` stays false, and the fade paints nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakSettingsSheet(
    page: SpeakSheetPage?,
    depth: Int,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(SpeakSheetPage, ScrollState) -> Unit,
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
    // skipPartiallyExpanded (round 14b §7.a): the sheet opens directly at its content height. The
    // 400dp inner cap below plus a partially-expanded sheet demanded two different gestures — drag
    // the sheet, then scroll the content — and signalled neither, which is exactly the reported
    // "you cannot tell there is more below".
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // A dismiss gesture hides the sheet BEFORE onDismissRequest runs, and the Speak sheet has one
    // branch (the grid page unwinding its own back-stack) that deliberately does not change `page`.
    // Without this, that gesture would leave the sheet composed and invisible with no reachable ✕.
    //
    // Keyed on `sheetState.isVisible`, NOT on `page`: the grid's BOOK/CHAPTER/VERSE step lives
    // inside the PickVerse page, so a grid-internal pop leaves `page` identical and a `page` key
    // would never re-run for exactly the case this effect exists to cover. Visibility is the one
    // signal that always changes, and a genuine close pops to a null `page`, which returns above
    // before this effect can re-show anything.
    //
    // Re-verified under round 14b's `skipPartiallyExpanded = true` (§7.a): this effect keys on
    // VISIBILITY, which the expansion mode does not touch, and `show()` now animates to Expanded —
    // the state the sheet already opens in. Still device-pass-only; it has no automated coverage.
    LaunchedEffect(sheetState.isVisible) { if (!sheetState.isVisible) sheetState.show() }
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
            //
            // Round 14b §7.b: the clip is exactly what made a half-cut row read as the end of the
            // list, so the clip is where the fade goes. `remember(page)`, not `rememberScrollState()`
            // — this shell survives a page change (only `page` changes, the composition does not
            // leave), so ONE state would carry the Settings page's scroll offset into the Advanced
            // page and open it part-way down. A key per page resets it.
            //
            // `visible` is a LAMBDA and that is load-bearing: it is read in the draw phase, so the
            // fade is right on the very first frame. A Boolean would be evaluated during composition
            // while `maxValue` is still 0 — see `abBottomFade`'s kdoc.
            val scrollState = remember(page) { ScrollState(initial = 0) }
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .abBottomFade(color = BottomSheetDefaults.ContainerColor) {
                        scrollState.canScrollForward
                    }
            ) {
                Column { content(page, scrollState) }
            }
        }
    }
}
