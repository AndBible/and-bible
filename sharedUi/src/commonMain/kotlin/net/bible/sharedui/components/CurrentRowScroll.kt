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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Scrolls [listState] once so the item at [targetIndex] is visible (no scroll if it already is). The index may
 * be unknown (negative) at first, e.g. while the rows still load; the scroll happens when it first appears.
 * Animated unless "Disable animations" is on, then it jumps (an animated scroll inside a one-frame sheet slide
 * would otherwise race the sheet's own motion).
 */
@Composable
fun ScrollToItemOnce(listState: LazyListState, targetIndex: Int) {
    val disableAnimations = LocalDisableAnimations.current
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(targetIndex >= 0) {
        if (scrolled || targetIndex < 0) return@LaunchedEffect
        scrolled = true
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }) return@LaunchedEffect
        if (disableAnimations) listState.scrollToItem(targetIndex)
        else listState.animateScrollToItem(targetIndex)
    }
}
