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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedcore.settings.SettingsItem

/**
 * Three or more answers to one question (spec §6.2, class F "answers"). A list of ACTIONS belongs in
 * `AbActionSheet` instead (spec §7). [message] is HTML.
 */
@Composable
fun AbOptionsDialog(
    title: String?,
    message: String?,
    options: List<SettingsItem.Choice>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    cancellable: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (cancellable) onDismissRequest() },
        properties = DialogProperties(dismissOnBackPress = cancellable, dismissOnClickOutside = cancellable),
        title = if (title != null) { { Text(title) } } else null,
        text = {
            Column {
                if (message != null) AbHtmlText(message)
                options.forEach { option ->
                    TextButton(onClick = { onSelect(option.value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = if (dismissText != null) { { TextButton(onClick = onDismissRequest) { Text(dismissText) } } } else null,
    )
}
