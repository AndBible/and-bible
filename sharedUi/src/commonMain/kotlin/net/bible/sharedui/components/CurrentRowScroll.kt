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

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.bible.sharedui.theme.LocalDisableAnimations
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoBorder
import kotlinx.coroutines.flow.first

/**
 * Marks the current row of a list: a tinted container plus a 4dp accent bar at the leading edge.
 * Pure monochrome (e-ink) gets no tint (no hue) but a 2dp ink outline and an ink bar instead.
 * Shared by the full workspace selector and the workspace quick sheet.
 */
@Composable
fun Modifier.currentRowHighlight(current: Boolean): Modifier {
    if (!current) return this
    val mono = isPureMonochrome()
    val scheme = MaterialTheme.colorScheme
    val barColor = if (mono) scheme.onSurface else scheme.primary
    val tint = if (mono) Color.Transparent else scheme.secondaryContainer
    return this
        .background(tint)
        .monoBorder(width = 2.dp)
        .drawBehind {
            val bar = 4.dp.toPx()
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
            drawRect(barColor, topLeft = Offset(x, 0f), size = Size(bar, size.height))
        }
}

/**
 * Scrolls [listState] so the item at [targetIndex] is FULLY visible (a half-visible row, e.g. under a sheet's
 * bottom edge, does not count). The index may be unknown (negative) at first, e.g. while the rows still load.
 * The list's viewport and content keep changing while a sheet opens and grows, so the check is repeated on each
 * layout until the item is fully in view once (a few attempts at most), then it never scrolls again: the user's
 * own scrolling is left alone. Animated unless "Disable animations" is on, then it jumps (an animated scroll
 * inside a one-frame sheet slide would race the sheet's own motion).
 */
@Composable
fun ScrollToItemOnce(listState: LazyListState, targetIndex: Int) {
    val disableAnimations = LocalDisableAnimations.current
    LaunchedEffect(targetIndex >= 0) {
        if (targetIndex < 0) return@LaunchedEffect
        var attempts = 0
        snapshotFlow { listState.layoutInfo }
            .first { info ->
                if (info.viewportSize.height == 0) return@first false // not measured yet
                val row = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                if (row != null && row.offset >= info.viewportStartOffset &&
                    row.offset + row.size <= info.viewportEndOffset
                ) return@first true
                if (++attempts > 5) return@first true
                if (disableAnimations) listState.scrollToItem(targetIndex)
                else listState.animateScrollToItem(targetIndex)
                false
            }
    }
}
