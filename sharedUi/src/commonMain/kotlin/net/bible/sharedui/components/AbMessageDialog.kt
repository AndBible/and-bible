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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * A message or a question whose body is HTML (spec §6.1): the replacement for every platform
 * `AlertDialog` whose message went through `htmlToSpan`. Up to three buttons — confirm, optional
 * dismiss, optional neutral — in the platform dialog's order (neutral at the start).
 *
 * [cancellable] = false is `setCancelable(false)`: back and scrim do nothing and [onDismissRequest]
 * is never called.
 */
@Composable
fun AbMessageDialog(
    title: String?,
    html: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    neutralText: String? = null,
    onNeutral: () -> Unit = {},
    cancellable: Boolean = true,
) {
    AbAlertDialog(
        onDismissRequest = { if (cancellable) onDismissRequest() },
        properties = DialogProperties(dismissOnBackPress = cancellable, dismissOnClickOutside = cancellable),
        title = if (title != null) { { Text(title) } } else null,
        text = if (html != null) {
            { AbHtmlText(html, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }
        } else null,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = if (dismissText != null || neutralText != null) {
            {
                Row {
                    if (neutralText != null) TextButton(onClick = onNeutral) { Text(neutralText) }
                    if (dismissText != null) TextButton(onClick = onDismiss) { Text(dismissText) }
                }
            }
        } else null,
    )
}
