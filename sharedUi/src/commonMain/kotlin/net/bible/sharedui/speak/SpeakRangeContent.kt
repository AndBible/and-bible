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

package net.bible.sharedui.speak

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.speak.PickedVerse
import net.bible.sharedui.components.AbSettingsRow
import net.bible.sharedui.strings.LocalStrings

/**
 * The repeat-passage verse range page: both endpoints visible at once, each opening the passage
 * grid as the next page of the same sheet.
 *
 * The ordering failure is shown INLINE under the end row. Before round 13a it was a `ToastEvent`
 * fired after the second full-screen picker had already closed, which is why the flow read as
 * "nothing happened".
 */
@Composable
fun SpeakRangeContent(
    start: PickedVerse?,
    end: PickedVerse?,
    showOrderError: Boolean,
    canCommit: Boolean,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth()) {
        AbSettingsRow(
            title = strings.speakBeginningOfPassage,
            summary = start?.label,
            enabled = true,
            onClick = onPickStart,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
        AbSettingsRow(
            title = strings.speakEndingOfPassage,
            summary = end?.label,
            enabled = true,
            onClick = onPickEnd,
            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
        if (showOrderError) {
            Text(
                strings.speakEndingVerseMustBeLater,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Delete, contentDescription = strings.deleteLabel)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(strings.cancel) }
            TextButton(onClick = onConfirm, enabled = canCommit) { Text(strings.okay) }
        }
    }
}
