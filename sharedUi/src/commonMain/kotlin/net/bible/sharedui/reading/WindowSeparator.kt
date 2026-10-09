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

import net.bible.sharedui.theme.isPureMonochrome
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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

// Z-late epilogue: the classic resources this file cites by name no longer exist -- they were
// deleted once the Compose reading view replaced what used them. The citations stay as the
// provenance of the numbers below, which is the whole reason they are recorded.
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
 * The draggable gesture is attached to the FULL [modifier] bounds, and a thin [thickness] bar,
 * centered within those bounds, is painted. The production caller (`SplitContent`'s `Separator`)
 * sizes [modifier] to exactly [thickness] (4dp) — this composable no longer carries a fat
 * invisible touch target of its own. That fat grab area now lives one layer up, as a separate
 * transparent `DragStrip` composed inside each of the two adjacent panes (`SplitContent.kt`),
 * driving this same [onDragBy]/[onDragEnd] pair through its own instance of the drag pipeline —
 * see the design spec §5 ("F4 — Separator: 4dp in flow, drag area invisible again") for why the
 * grab area moved out of this composable's own bounds.
 *
 * Three visual states, matching classic `Separator.kt`'s `separator`/`separator_active`/
 * `separator_drag` drawables, remapped to M3 theme roles so BW/COLOR_EINK degrade automatically
 * (`AbTheme`'s `displayColorMode`) instead of needing dedicated drawables per mode: idle (neither
 * [isActive] nor [isDragging]) paints `outlineVariant`, [isActive] (this separator is adjacent to
 * the active window — see `:sharedCore`'s `separatorIsActive`) paints `primary`, and [isDragging]
 * (this separator is currently being dragged) paints `tertiary` and takes priority over [isActive].
 */
@Composable
fun WindowSeparator(
    isVertical: Boolean,
    onDragBy: (deltaPx: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    isActive: Boolean = false,
    isDragging: Boolean = false,
) {
    val mono = isPureMonochrome()
    val barThickness = if (mono && isDragging) thickness * 2 else thickness
    val orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal
    val barModifier = if (mono && isDragging) {
        if (isVertical) Modifier.fillMaxWidth().requiredHeight(barThickness)
        else Modifier.fillMaxHeight().requiredWidth(barThickness)
    } else if (isVertical) Modifier.fillMaxWidth().height(barThickness)
                       else Modifier.fillMaxHeight().width(barThickness)
    val barColor = if (mono) MaterialTheme.colorScheme.onSurface else when {
        isDragging -> MaterialTheme.colorScheme.tertiary
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = modifier
            .draggable(
                orientation = orientation,
                state = rememberDraggableState { delta -> onDragBy(delta) },
                onDragStopped = { onDragEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(barModifier.background(barColor))
    }
}
