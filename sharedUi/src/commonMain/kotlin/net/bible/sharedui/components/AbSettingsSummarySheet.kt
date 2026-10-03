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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A compact read-only **summary row** that opens a [ModalBottomSheet] holding the full settings.
 *
 * Renders [summary] (`bodyMedium`) with a trailing "tune" affordance; the whole row is clickable and
 * opens the sheet, whose body is [sheetContent] (`close` dismisses the sheet). This keeps a tall
 * always-visible settings block off the screen: the caller shows only a one-line summary of the
 * current selections, and edits happen in the roomier sheet where long labels render untruncated.
 *
 * Fully portable (commonMain, no Android APIs) so it compiles for iOS too.
 *
 * @param initiallyOpen open the sheet on first composition (deterministic golden capture only;
 *   mirrors the existing `initiallyChooserOpen`/`initiallyExpanded` patterns).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbSettingsSummarySheet(
    summary: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    sheetContent: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(initiallyOpen) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
    }

    if (open) {
        // skipPartiallyExpanded (round 14b §7.a / spec D4) — uniform with every other sheet.
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val close = { open = false }
        AbModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                sheetContent(close)
            }
        }
    }
}
