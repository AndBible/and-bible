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

package net.bible.sharedui.workspaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedcore.workspaces.WorkspaceRowVd
import net.bible.sharedui.theme.LocalDisplayColorMode

/**
 * The workspace QUICK switch list (spec §4.4): colour dot + name, current row bold and inert.
 * No reorder handle, no row overflow, no create — the full selector owns all of that and is one
 * footer row away ([net.bible.sharedui.components.AbQuickSheetFooterRow]).
 *
 * Row vertical padding is 6dp, not the shell's usual 12-14dp, and this is load-bearing rather than
 * cosmetic: the host (Task 7) wraps this list's [listState] in
 * [net.bible.sharedui.components.AbSheetScrollBound] / `Modifier.abBottomFade`, whose 24dp fade
 * window is only visible where it lands on drawn content (`AbBottomFade.kt:47-52`). A single-line
 * `bodyLarge` row's visible glyph run is shorter than its 24dp line box (~6dp of leading split above
 * and below), so the blank run between two rows' text is approximately `2 * verticalPadding + 6dp`.
 * At the brief's original 14dp that is ~34dp — wider than the 24dp window, so the affordance could
 * land entirely on bare surface and disappear at some scroll positions (the same mistake Task 2's
 * `AbQuickSheetGoldenTest.rows()` made with its 12dp padding, fixed there by a denser fixture). At
 * 6dp the blank run is ~18dp, safely under 24dp, so the fade is guaranteed to straddle text at every
 * scroll offset.
 */
@Composable
fun WorkspaceQuickContent(
    rows: List<WorkspaceRowVd>,
    onSelect: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colorMode = LocalDisplayColorMode.current
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(rows, key = { it.id }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (row.isCurrent) Modifier else Modifier.clickable { onSelect(row.id) })
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The per-workspace color is a decorative-but-meaningful identifier, not a scheme
                // color, so it isn't grayscaled by AbTheme automatically (CategoryPalette idiom):
                // grays out in BW, stays colored in COLOR_EINK. Construction copied verbatim from
                // `WorkspaceSelectorScreen.kt:183-187` so the two screens tint identically.
                Surface(
                    color = Color(accentArgbFor(row.colorArgb, colorMode)),
                    shape = CircleShape,
                    modifier = Modifier.size(16.dp),
                ) {}
                Spacer(Modifier.width(16.dp))
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (row.isCurrent) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
