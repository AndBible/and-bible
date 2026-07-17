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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A generic, portable multiselect chooser: a titled [AlertDialog] with one checkbox row per option
 * (label = [labelOf]), pre-checked from [selectedIds]. Confirm reports the currently-checked ids via
 * [onConfirm]; Cancel/dismiss reports nothing. Self-contained (commonMain, no Android APIs).
 *
 * @param options the selectable items.
 * @param idOf     stable identity for an option (also the value reported to [onConfirm]).
 * @param labelOf  the human-readable row label.
 */
@Composable
fun <T> AbMultiSelectDialog(
    title: String,
    options: List<T>,
    selectedIds: List<String>,
    idOf: (T) -> String,
    labelOf: (T) -> String,
    confirmText: String,
    dismissText: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Local working copy of the checked set; committed to the host only on Confirm.
    val checked = remember(options, selectedIds) {
        mutableStateListOf<String>().apply { addAll(selectedIds) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(options, key = { idOf(it) }) { option ->
                    val id = idOf(option)
                    val isChecked = checked.contains(id)
                    val toggle = {
                        if (isChecked) checked.remove(id) else checked.add(id)
                        Unit
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = { toggle() })
                        Spacer(Modifier.width(8.dp))
                        Text(labelOf(option))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(checked.toList()) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
