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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A labelled one-of-N picker laid out as a [columns]-wide grid of radio rows, with an optional
 * [preview] between the heading and the options.
 *
 * Replaces two older shapes on the label editor: a run of switches whose mutual exclusion was
 * expressed only by greying the dominated ones out (so one of the four choices — the fall-through —
 * had no affordance at all), and an `AbDropdownField` that hid the alternatives behind a tap.
 *
 * Radio rows rather than a `SingleChoiceSegmentedButtonRow` on purpose: four translated labels on
 * one line overflow a narrow screen, and `selectableGroup()` + [Role.RadioButton] is what makes a
 * screen reader announce "2 of 4" instead of four unrelated toggles.
 *
 * Rows are built by chunking, NOT by `LazyVerticalGrid`: every consumer so far sits inside a
 * `verticalScroll` column, where a lazy grid cannot measure.
 */
@Composable
fun <T> AbChoiceGroup(
    heading: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    preview: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (preview != null) {
            preview()
        }
        Column(Modifier.fillMaxWidth().selectableGroup().padding(top = 4.dp)) {
            options.chunked(columns).forEach { rowOptions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    rowOptions.forEach { option ->
                        val isSelected = option == selected
                        Row(
                            modifier = Modifier
                                // Equal-width cells so the second column starts at the same x on
                                // every row, including a final row with fewer options than columns.
                                .weight(1f)
                                .selectable(
                                    selected = isSelected,
                                    onClick = { onSelect(option) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Text(
                                optionLabel(option),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    // Pad a short final row so its cells keep the same width as the full rows'.
                    repeat(columns - rowOptions.size) {
                        Row(Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}
