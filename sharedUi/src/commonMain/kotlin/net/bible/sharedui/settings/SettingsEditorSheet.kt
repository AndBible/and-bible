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

package net.bible.sharedui.settings

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedui.strings.LocalStrings

/**
 * The settings editor sheet shell. Renders nothing when [page] is null, so a host can render it
 * unconditionally — the same self-hiding contract `SearchSettingsSheet` uses, which is what lets the
 * reading view keep it as an unconditional sibling overlay.
 *
 * [onDismiss] is wired to the sheet's single `onDismissRequest`. Material3 gives one callback for
 * back, scrim tap and swipe-down and they cannot be told apart, and `BackHandler` cannot live in
 * commonMain — so a host maps it to `SettingsEditorStack.pop()`, which steps back one page and
 * closes at depth 1. That is deliberate: DO NOT "fix" it into a whole-sheet dismiss, or the page
 * stack becomes unreachable by back. [onClose] is the header ✕, which always closes outright.
 *
 * ROBORAZZI: never capture this composable with a non-null [page]. Forcing a `ModalBottomSheet` open
 * in a capture hangs Roborazzi and takes the whole `:app` suite with it. Golden the page's `*Content`
 * composable directly inside a plain `Column` instead — see `SettingsEditorSheetGoldenTest`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEditorSheet(
    page: SettingsEditorPage?,
    title: String,
    showBack: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (page == null) return
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
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
            content()
        }
    }
}

/** A sheet page's confirm/cancel pair, right-aligned — the sheet analogue of AlertDialog's button row. */
@Composable
fun SheetConfirmRow(
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(cancelLabel) }
        TextButton(onClick = onConfirm) { Text(confirmLabel) }
    }
}
