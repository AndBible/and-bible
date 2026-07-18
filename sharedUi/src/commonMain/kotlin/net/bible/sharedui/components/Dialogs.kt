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

package net.bible.sharedui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Cap on the message body's height, so a long message scrolls instead of growing the dialog past
 *  the screen (and, critically, instead of pushing/overlapping the confirm/dismiss buttons — see
 *  [AbConfirmDialog]). Mirrors `AbInfoDialog.maxBodyHeight`. */
private val maxMessageHeight = 420.dp

/** A simple error dialog: a message + a single dismiss button. Height-bounded/scrollable like
 *  [AbConfirmDialog] (same regression risk: an unbounded body can push the dismiss button off the
 *  visible dialog on a real phone-sized window). */
@Composable
fun AbErrorDialog(message: String, confirmText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(confirmText) } },
        text = {
            Text(
                text = message,
                modifier = Modifier
                    .heightIn(max = maxMessageHeight)
                    .verticalScroll(rememberScrollState()),
            )
        },
    )
}

/**
 * A confirm/cancel dialog: optional title, a message, a confirm and a cancel button. The message is
 * height-bounded and scrolls internally (mirrors `AbInfoDialog`'s `maxBodyHeight`/`verticalScroll`
 * treatment) — without this, a long message (e.g. the AI accept-disclaimer text, F31) can grow the
 * dialog's content past the actual window height on a real phone, overlapping or hiding the
 * confirm/dismiss buttons entirely. The cap only engages once content exceeds it, so short-message
 * callers (the ~13 others in this codebase) are visually unchanged.
 */
@Composable
fun AbConfirmDialog(
    title: String?,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = if (title != null) { { Text(title) } } else null,
        text = {
            Text(
                text = message,
                modifier = Modifier
                    .heightIn(max = maxMessageHeight)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
