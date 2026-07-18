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
import net.bible.sharedui.strings.LocalStrings

/**
 * Scrollable, height-bounded informational dialog: a title + a plain-text body + a single dismiss
 * button. Used for help text, disclaimers, and tool descriptions (F28/F30/F34/F37) where the body
 * may be long — the body is capped at [maxBodyHeight] and scrolls internally instead of growing the
 * dialog past the screen. Any HTML in the source text must be flattened to plain text by the caller
 * before it reaches [body]; this composable renders it verbatim (newlines preserved).
 */
@Composable
fun AbInfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    confirmLabel: String = LocalStrings.current.okay,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                modifier = Modifier
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(confirmLabel) } },
    )
}

/** Cap on the scrollable body's height, so a long help/disclaimer text scrolls instead of growing
 *  the dialog beyond the screen. */
private val maxBodyHeight = 420.dp
