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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.components.dialog.AbAnswerCard
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.abDialogDetailStyle
import net.bible.sharedui.theme.LocalDisableAnimations

/**
 * Three or more answers to one question (spec §6.2, class F "answers"), on [AbDialogShell]. A list of ACTIONS belongs
 * in `AbActionSheet` instead (spec §7). [message] is HTML, shown as the detail under the lead ([title]).
 *
 * Each answer is an [AbAnswerCard] in the scrolling middle area, so every answer stays reachable at any font scale.
 * With [checkboxLabel] the dialog keeps a checkbox row and the answers become end-aligned buttons in the button row
 * (Confirm role, Destructive for a destructive answer); there are no cards then.
 */
@Composable
fun AbOptionsDialog(
    title: String?,
    message: String?,
    options: List<DialogAnswer>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String? = null,
    cancellable: Boolean = true,
    checkboxLabel: String? = null,
    onSelectWithCheckbox: ((String, Boolean) -> Unit)? = null,
) {
    var checked by remember { mutableStateOf(false) }
    AbDialogShell(
        onDismissRequest = { if (cancellable) onDismissRequest() },
        properties = DialogProperties(dismissOnBackPress = cancellable, dismissOnClickOutside = cancellable),
        lead = title?.let { t -> { Text(t) } },
        content = {
            if (message != null) AbHtmlText(message, style = abDialogDetailStyle())
            if (checkboxLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = checked, role = Role.Checkbox, onValueChange = { checked = it },
                    ).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Recreate the control at its target state when desktop motion is disabled.
                    key(if (LocalDisableAnimations.current) checked else Unit) {
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                    Text(checkboxLabel, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Column(
                    Modifier.padding(top = if (message != null) 12.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { option -> AbAnswerCard(option, onClick = { onSelect(option.value) }) }
                }
            }
        },
        buttons = {
            if (dismissText != null) AbDialogButton(dismissText, DialogButtonRole.Cancel, onDismissRequest)
            if (checkboxLabel != null) {
                options.asReversed().forEach { option ->
                    AbDialogButton(
                        option.label,
                        if (option.destructive) DialogButtonRole.Destructive else DialogButtonRole.Confirm,
                        onClick = {
                            if (onSelectWithCheckbox != null) onSelectWithCheckbox(option.value, checked)
                            else onSelect(option.value)
                        },
                    )
                }
            }
        },
    )
}
