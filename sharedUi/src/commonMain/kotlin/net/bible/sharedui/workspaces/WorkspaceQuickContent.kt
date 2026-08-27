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
import androidx.compose.foundation.layout.heightIn
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
 * Row height is governed by `Modifier.heightIn(min = 48.dp)`, a hard floor for the Material touch
 * target — NOT by tightening the 12dp vertical padding to fit under
 * [net.bible.sharedui.components.AbBottomFade]'s 24dp fade window. An earlier version of this row
 * shrank the padding to 6dp for exactly that reason and was corrected in fix round 1: a 32dp tap
 * target is an accessibility defect, and an occasionally-invisible scroll fade is a missing nicety
 * — those two do not trade off against each other.
 *
 * Consequently, the gap between two rows' visible text (~28dp: 12dp padding either side of the
 * ~24dp `bodyLarge` line box, minus the ~6dp of leading the glyphs don't fill) is WIDER than the
 * fade's 24dp window, so once this list is wrapped in `AbSheetScrollBound` /
 * `Modifier.abBottomFade` (the host, Task 7), the fade can land entirely on bare surface and go
 * invisible at some scroll positions. That is the documented behaviour of `abBottomFade`
 * (`AbBottomFade.kt:47-52` — "over bare surface it paints nothing at all... a caller whose content
 * has taller gaps than that must pass a larger height, or the fade will be invisible at some
 * scroll positions"), and it is deliberately accepted here rather than deforming the row — the
 * same call already made for History's `TwoLineListItem`. If the affordance is ever wanted for
 * these single-line lists, thread an optional `fadeHeight: Dp = AbBottomFadeHeight` through
 * `AbSheetScrollBound` into `abBottomFade` and pass ~32dp; do not shrink rows to chase it.
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
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
