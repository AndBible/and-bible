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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A settings row: label (+ optional summary) on the left, an M3 [Switch] on the right. The whole
 *  row is clickable and toggles the switch (larger touch target than the thumb alone). When
 *  [enabled] is false the row dims and stops responding to clicks (matches the other interactive
 *  settings rows in [net.bible.sharedui.settings.AbSettingsScreen]).
 *
 *  [onLongClick] defaults to `null` (Batch 12d-A Task 3's long-press-revert seam): when null the row
 *  keeps its original [toggleable] modifier (byte-identical); when non-null it switches to
 *  [combinedClickable] so a long press is available alongside the normal toggle tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { if (enabled) onCheckedChange(!checked) },
                        onLongClick = onLongClick,
                        enabled = enabled,
                        role = Role.Switch,
                    )
                } else {
                    Modifier.toggleable(value = checked, onValueChange = onCheckedChange, enabled = enabled, role = Role.Switch)
                },
            )
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private const val DISABLED_ALPHA = 0.38f

/** A settings row: label + a right-aligned value readout on the top line, an M3 [Slider] below.
 *  [value]/[onValueChange] are Int; the slider rounds. [valueLabel] is the pre-formatted readout
 *  for the current [value] (e.g. "150 %").
 *
 *  The slider tracks the finger via LOCAL drag state, so [onValueChange] fires only ONCE per drag
 *  gesture — on release — mirroring classic `SeekBarPreference` (a per-tick callback here would run
 *  a Room write + a full settings rebuild on every increment). [onValueChangeFinished] fires after
 *  the release persist, for any extra host-side finish handling.
 *
 *  While dragging, the readout updates live from the local value using [valueLabelFor] if provided;
 *  callers without a formatter keep showing the static [valueLabel] (which refreshes once the
 *  persisted [value] comes back). */
@Composable
fun AbSliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChangeFinished: (() -> Unit)? = null,
    valueLabelFor: ((Int) -> String)? = null,
) {
    // Re-seed the local drag position whenever the persisted [value] changes (e.g. after a release
    // persist round-trips a fresh snapshot, or an external reset).
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val displayLabel = valueLabelFor?.invoke(dragValue.roundToInt()) ?: valueLabel
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(displayLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            valueRange = valueRange,
            onValueChangeFinished = {
                onValueChange(dragValue.roundToInt())
                onValueChangeFinished?.invoke()
            },
        )
    }
}
