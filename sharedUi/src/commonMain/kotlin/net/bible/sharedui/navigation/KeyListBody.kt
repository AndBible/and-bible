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
package net.bible.sharedui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.components.volumeScrollTarget
import net.bible.sharedui.theme.isPureMonochrome

/**
 * A single-line key list with current-item highlight + initial scroll: the body of the map and
 * general-book key-list quick sheet (`QuickSheets.kt`), which every map and general-book key-chooser
 * entry point opens (#4172: the full-screen choosers are gone). Public for its golden.
 *
 * [listState] is a parameter, not a private `remember`, for the sheet's sake: the quick-sheet shell
 * draws its bottom fade from a `canScrollForward` lambda it is handed, so the host has to pass the
 * body's OWN state or the fade can never appear (`AbSheetChrome.kt` — `canScrollForward` is read in
 * the draw phase).
 *
 * The `LazyColumn` sizes to CONTENT, not to `fillMaxSize` — under the sheet's
 * `heightIn(max = AbSheetContentMaxHeight)` a `fillMaxSize` here would set minHeight = maxHeight and
 * pin a five-row map list at 400dp (`WorkspaceQuickContent`'s I2 review fix records the same trap).
 */
@Composable
fun KeyListBody(
    rows: List<KeyRow>,
    currentKeyId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(currentKeyId, rows) {
        val idx = rows.indexOfFirst { it.keyId == currentKeyId }
        if (idx >= 0) listState.scrollToItem(idx)
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().volumeScrollTarget(listState)) {
        items(rows, key = { it.keyId }) { row ->
            val isCurrent = row.keyId == currentKeyId
            val highlight = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            // Ink/paper modes have no tint to tell the current row apart (#4186): the same bold text
            // and 2dp primary (= ink) outline the Bible grid sheet gives its current cell.
            val inkBorder = if (isCurrent && isPureMonochrome()) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
            } else Modifier
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { selected = isCurrent }
                    .clickable { onSelect(row.keyId) }
                    .background(highlight)
                    .then(inkBorder)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}
