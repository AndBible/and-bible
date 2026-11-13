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
package net.bible.sharedui.reading.host

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed

/**
 * One band of the leading edge strip, [DrawerEdgeExclusionMaxHeight] tall and starting at
 * [DrawerEdgeExclusionTopFraction] of the node's height, is exempted from the system back gesture.
 * Android ignores exclusions beyond 200dp per edge, so the whole-height strip cannot be excluded: with
 * gesture navigation the edge swipe opens the menu in this band and is the system back gesture
 * elsewhere. With 3-button navigation there is no edge gesture and the exclusion has no effect.
 */
internal actual fun Modifier.drawerEdgeGestureExclusion(enabled: Boolean, rtl: Boolean): Modifier =
    if (!enabled) this else composed {
        val density = LocalDensity.current
        systemGestureExclusion { coordinates ->
            with(density) {
                val width = coordinates.size.width.toFloat()
                val height = coordinates.size.height.toFloat()
                val edge = DrawerEdgeSwipeWidth.toPx()
                val top = height * DrawerEdgeExclusionTopFraction
                val bottom = (top + DrawerEdgeExclusionMaxHeight.toPx()).coerceAtMost(height)
                if (rtl) Rect(width - edge, top, width, bottom) else Rect(0f, top, edge, bottom)
            }
        }
    }
