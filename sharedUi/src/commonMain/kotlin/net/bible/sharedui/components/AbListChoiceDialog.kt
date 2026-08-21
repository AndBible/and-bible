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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.strings.LocalStrings

/** Renders [choices] as a scrollable column of radio-button rows (theme-aware, uses the ambient
 *  M3 colour scheme). Selecting a row invokes [onSelect] with the choice's stable value; the
 *  content itself never dismisses anything — that is the caller's job. Renders gracefully with
 *  no choices (an empty column).
 *
 *  CAUTION: [modifier] lands INSIDE this composable's own `verticalScroll`
 *  (`Modifier.verticalScroll(rememberScrollState()).then(modifier)`), so a height bound passed
 *  through it (e.g. `Modifier.heightIn(max = ...)`) is measured by `verticalScroll`'s child with
 *  `maxHeight = Infinity` — it clamps the reported size of the inner [Column], not the scroll
 *  viewport, so the scroll range collapses to 0 and content past the bound becomes permanently
 *  unreachable rather than merely scroll-capped. To bound how much space this composable may use,
 *  wrap the CALL SITE in an ancestor `Box`/`Column` with the height constraint instead — that
 *  constraint reaches `verticalScroll` as a real, finite `maxHeight`, which is what it needs to
 *  compute a working scroll range. (`GenericSettingsEditorSheet` does this.) */
@Composable
fun AbListChoiceContent(
    choices: List<SettingsItem.Choice>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).then(modifier),
    ) {
        choices.forEach { choice ->
            val selected = choice.value == selectedValue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = {
                            onSelect(choice.value)
                        },
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(16.dp))
                Text(choice.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A single-choice list dialog: an M3 [AlertDialog] presenting [choices] as a scrollable column of
 * radio-button rows. Selecting a row invokes [onSelect] with the choice's stable value and then
 * dismisses. Theme-aware (uses the ambient M3 colour scheme). Renders gracefully with no choices —
 * an empty dialog with only the cancel button (the settings framework never sends an empty list in
 * practice, since consuming screens intercept such rows, but this must not crash).
 */
@Composable
fun AbListChoiceDialog(
    title: String,
    choices: List<SettingsItem.Choice>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AbListChoiceContent(
                choices = choices,
                selectedValue = selectedValue,
                onSelect = { onSelect(it); onDismiss() },
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}
