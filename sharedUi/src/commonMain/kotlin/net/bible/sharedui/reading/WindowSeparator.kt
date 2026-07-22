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

package net.bible.sharedui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draggable divider between two panes in the native Compose split (see the reading-view
 * design spec §2.3). [isVertical] mirrors the split direction: `true` = panes stacked
 * vertically, so the separator is a full-width horizontal strip; `false` = panes side-by-side,
 * so the separator is a full-height vertical strip.
 *
 * Only the pixel drag delta is reported via [onDragBy] (and drag completion via [onDragEnd]) —
 * the delta-to-weight math (`separatorDrag`/`WeightDelta`) and the SSOT commit live one layer up
 * (`:sharedCore`'s split-geometry helpers, applied by the caller), keeping this composable a
 * dumb, iOS-clean, stateless divider.
 *
 * The draggable gesture is attached to the FULL [modifier] bounds — a fat invisible touch
 * target (the caller sizes those bounds generously, e.g. `thickness` plus ~20dp of margin on
 * each side, per the design spec) — while only a thin [thickness] bar, centered within those
 * bounds, is actually painted (in the theme's outline color). This makes the divider easy to
 * grab with a finger without widening the visible seam between panes.
 */
@Composable
fun WindowSeparator(
    isVertical: Boolean,
    onDragBy: (deltaPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
) {
    val orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal
    val barModifier = if (isVertical) Modifier.fillMaxWidth().height(thickness)
                       else Modifier.fillMaxHeight().width(thickness)
    Box(
        modifier = modifier
            .draggable(
                orientation = orientation,
                state = rememberDraggableState { delta -> onDragBy(delta) },
                onDragStopped = { onDragEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(barModifier.background(MaterialTheme.colorScheme.outlineVariant))
    }
}
