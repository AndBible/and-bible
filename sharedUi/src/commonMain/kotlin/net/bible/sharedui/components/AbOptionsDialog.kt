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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
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
    checkboxLabel: String? = null,
    onSelectWithCheckbox: ((String, Boolean) -> Unit)? = null,
) {
    var checked by remember { mutableStateOf(false) }
    AbAlertDialog(
        onDismissRequest = { if (cancellable) onDismissRequest() },
        properties = DialogProperties(dismissOnBackPress = cancellable, dismissOnClickOutside = cancellable),
        modifier = if (checkboxLabel != null) Modifier.widthIn(min = 320.dp, max = 480.dp).fillMaxWidth() else Modifier,
        title = if (title != null) { { Text(title) } } else null,
        text = {
            // The message and the answers scroll together: at an accessibility font size a long message otherwise pushes
            // the answers out of the dialog, unreachable.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (message != null) AbHtmlText(message)
                if (checkboxLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = checked, role = Role.Checkbox, onValueChange = { checked = it },
                        ).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(checkboxLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (checkboxLabel == null) {
                    options.forEach { option ->
                        TextButton(onClick = { onSelect(option.value) }, modifier = Modifier.fillMaxWidth()) {
                            Text(option.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (checkboxLabel != null) {
                FlowRow(horizontalArrangement = Arrangement.End) {
                    options.asReversed().forEach { option ->
                        TextButton(onClick = {
                            if (onSelectWithCheckbox != null) onSelectWithCheckbox(option.value, checked)
                            else onSelect(option.value)
                        }) { Text(option.label) }
                    }
                }
            }
        },
        dismissButton = if (dismissText != null) { { TextButton(onClick = onDismissRequest) { Text(dismissText) } } } else null,
    )
}
