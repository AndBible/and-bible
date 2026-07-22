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

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.effectiveWeights
import net.bible.sharedcore.window.separatorDrag

/** Comfortable fixed cross-axis touch target for the drag handle; the visible bar stays thin (see [WindowSeparator]). */
private val SEPARATOR_GRAB_SIZE = 16.dp

/**
 * Lays out the visible windows of [layout] into weighted panes, separated by a draggable
 * [WindowSeparator], choosing orientation from the available space (see the reading-view design
 * spec §2). `isHorizontal = (maxWidth > maxHeight) != layout.reverseSplitMode` — landscape splits
 * side-by-side panes unless `reverseSplitMode` flips it (and vice versa in portrait).
 *
 * The container is always a single `Row` (horizontal split) or `Column` (vertical split) — never
 * restructured by pane count — so a hosted `AndroidView`/`BibleView` pane survives recomposition
 * when weights change or a separator drags. Each pane fills [Modifier.weight] of
 * `effectiveWeights(layout.windows)` (a lone window gets weight `1f`); tapping anywhere on a pane
 * reports [onWindowActivated]. A drag on the separator between two adjacent panes is accumulated
 * locally in pixels and converted to a new weight pair via `separatorDrag` only once the drag ends,
 * then reported via [onSeparatorCommitted] — the caller (SSOT) applies it back into [layout].
 */
@Composable
fun SplitContent(
    layout: WindowLayoutState,
    onWindowActivated: (String) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    pane: @Composable (windowId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windows = layout.windows.filter { it.isVisible }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val isHorizontal = (maxWidth > maxHeight) != layout.reverseSplitMode
        val weights = effectiveWeights(windows)
        // Captured here (BoxWithConstraintsScope is the only implicit receiver in scope) so the
        // averageExtentPx lambdas below — defined inside the nested Row/Column scope — don't need
        // to resolve maxWidth/maxHeight through an ambiguous nested-receiver chain.
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        if (isHorizontal) {
            Row(Modifier.fillMaxSize()) {
                windows.forEachIndexed { index, w ->
                    key(w.id) {
                        Box(
                            Modifier
                                .weight(weights[index])
                                .fillMaxSize()
                                .pointerInput(w.id) { detectTapGestures { onWindowActivated(w.id) } },
                        ) { pane(w.id) }
                    }
                    if (index < windows.lastIndex) {
                        Separator(
                            windows = windows,
                            weights = weights,
                            index = index,
                            isVertical = false,
                            averageExtentPx = { maxWidthPx / windows.size },
                            onSeparatorCommitted = onSeparatorCommitted,
                            modifier = Modifier.fillMaxHeight().width(SEPARATOR_GRAB_SIZE),
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                windows.forEachIndexed { index, w ->
                    key(w.id) {
                        Box(
                            Modifier
                                .weight(weights[index])
                                .fillMaxSize()
                                .pointerInput(w.id) { detectTapGestures { onWindowActivated(w.id) } },
                        ) { pane(w.id) }
                    }
                    if (index < windows.lastIndex) {
                        Separator(
                            windows = windows,
                            weights = weights,
                            index = index,
                            isVertical = true,
                            averageExtentPx = { maxHeightPx / windows.size },
                            onSeparatorCommitted = onSeparatorCommitted,
                            modifier = Modifier.fillMaxWidth().height(SEPARATOR_GRAB_SIZE),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Separator(
    windows: List<WindowSnapshot>,
    weights: List<Float>,
    index: Int,
    isVertical: Boolean,
    averageExtentPx: () -> Float,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    modifier: Modifier,
) {
    var accumulated by remember(windows[index].id, windows[index + 1].id) { mutableFloatStateOf(0f) }
    WindowSeparator(
        isVertical = isVertical,
        onDragBy = { delta -> accumulated += delta },
        onDragEnd = {
            val delta = separatorDrag(accumulated, averageExtentPx(), weights[index], weights[index + 1])
            onSeparatorCommitted(windows[index].id, delta.weight1, windows[index + 1].id, delta.weight2)
            accumulated = 0f
        },
        modifier = modifier,
    )
}
