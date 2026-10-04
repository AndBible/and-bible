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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.components.AbSwitchRow
import net.bible.sharedui.strings.LocalStrings

/**
 * Task 26 (platform-dialog removal, run 3): the verse-share quick sheet's shell, replacing the
 * classic `ShareWidget` `AlertDialog` (`ShareVersesOptions.buildText`'s kdoc has the porting
 * notes). All of the actual content — preview, share/copy, switches — lives in
 * [ShareVersesSheetContent]; this function only wraps it in [AbQuickSheet].
 *
 * [options] and [input] are both supplied by the host: [options] is the persisted toggle state
 * (`ComposeReadingViewHost` loads/saves it under the same pref keys `ShareWidget` used), [input] is
 * captured once per opening (`ReadingQuickSheet.Share.input`). Every switch calls [onOptionsChange]
 * with a COPY of [options] — the host is what actually persists it; this composable never touches
 * prefs itself, the same split every other quick sheet in this file's siblings uses.
 *
 * ROBORAZZI: never capture this composable — an open `ModalBottomSheet` hangs the capture and
 * takes the whole `:app` suite with it. Capture [ShareVersesSheetContent] instead.
 */
@Composable
fun ShareVersesSheet(
    open: Boolean,
    title: String,
    options: ShareVersesOptions,
    onOptionsChange: (ShareVersesOptions) -> Unit,
    input: ShareVersesInput,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = remember(input) { ScrollState(initial = 0) }
    AbQuickSheet(
        open = open,
        title = title,
        onDismiss = onDismiss,
        canScrollForward = { scrollState.canScrollForward },
    ) {
        ShareVersesSheetContent(
            options = options,
            onOptionsChange = onOptionsChange,
            input = input,
            onShare = onShare,
            onCopy = onCopy,
            scrollState = scrollState,
        )
    }
}

/**
 * [ShareVersesSheet]'s body — stateless and free of the sheet container, which is what makes it
 * capturable, and directly testable without ever composing a `ModalBottomSheet` (the Robolectric
 * trap every quick-sheet test in this port avoids — see [ShareVersesSheet]'s kdoc).
 *
 * The share/copy buttons live HERE, not in an [AbQuickSheet] `footer`, specifically so a plain
 * Robolectric/compose-ui-test host can assert "tapping Share calls [onShare] with the CURRENT
 * preview text" without opening a sheet at all.
 */
@Composable
fun ShareVersesSheetContent(
    options: ShareVersesOptions,
    onOptionsChange: (ShareVersesOptions) -> Unit,
    input: ShareVersesInput,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val strings = LocalStrings.current
    // Recomputed on every option change so the preview always reflects the CURRENT switches — no
    // JSword lookup happens here, `buildText` is pure string assembly over the already-resolved
    // `input` (Task 26).
    val previewText = remember(options, input) { options.buildText(input) }
    Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 16.dp)) {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onCopy(previewText) }) { Text(strings.copyLabel) }
            TextButton(onClick = { onShare(previewText) }) { Text(strings.shareLabel) }
        }
        HorizontalDivider()
        AbSwitchRow(
            label = strings.shareShowReference,
            checked = options.showReference,
            onCheckedChange = { onOptionsChange(options.copy(showReference = it)) },
        )
        AbSwitchRow(
            label = strings.shareAbbreviateReference,
            checked = options.abbreviateReference,
            onCheckedChange = { onOptionsChange(options.copy(abbreviateReference = it)) },
            enabled = options.showReference,
        )
        AbSwitchRow(
            label = strings.shareShowVersion,
            checked = options.showVersion,
            onCheckedChange = { onOptionsChange(options.copy(showVersion = it)) },
            enabled = options.showReference,
        )
        AbSwitchRow(
            label = strings.shareShowReferenceAtFront,
            checked = options.showReferenceAtFront,
            onCheckedChange = { onOptionsChange(options.copy(showReferenceAtFront = it)) },
            enabled = options.showReference,
        )
        AbSwitchRow(
            label = strings.shareShowVerseNumbers,
            checked = options.showVerseNumbers,
            onCheckedChange = { onOptionsChange(options.copy(showVerseNumbers = it)) },
        )
        AbSwitchRow(
            label = strings.shareShowQuotes,
            checked = options.showQuotes,
            onCheckedChange = { onOptionsChange(options.copy(showQuotes = it)) },
        )
        AbSwitchRow(
            label = strings.shareSeparateVersesWithNewlines,
            checked = options.separateVersesWithNewlines,
            onCheckedChange = { onOptionsChange(options.copy(separateVersesWithNewlines = it)) },
        )
        // GONE-when-`!hasRange` in the classic widget (a single, whole-verse selection has no
        // "selected text only" to distinguish) — same conditional-emission shape here.
        if (input.hasRange) {
            AbSwitchRow(
                label = strings.shareShowSelectionOnly,
                checked = options.showSelectionOnly,
                onCheckedChange = { onOptionsChange(options.copy(showSelectionOnly = it)) },
            )
            AbSwitchRow(
                label = strings.shareShowEllipsis,
                checked = options.showEllipsis,
                onCheckedChange = { onOptionsChange(options.copy(showEllipsis = it)) },
                enabled = options.showSelectionOnly,
            )
        }
        // GONE-when-no-notes in the classic widget.
        if (input.notesText != null) {
            AbSwitchRow(
                label = strings.showNotesLabel,
                checked = options.showNotes,
                onCheckedChange = { onOptionsChange(options.copy(showNotes = it)) },
            )
        }
        AbSwitchRow(
            label = strings.shareAdvertiseApp,
            checked = options.advertiseApp,
            onCheckedChange = { onOptionsChange(options.copy(advertiseApp = it)) },
        )
    }
}
