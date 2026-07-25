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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.effectiveWeights
import net.bible.sharedcore.window.separatorDrag
import net.bible.sharedcore.window.separatorIsActive

/** Classic `window_separator_width` (`res/values/dimens.xml:32`): the only space the seam occupies in flow. */
private val SEPARATOR_THICKNESS = 4.dp

/**
 * Classic `window_separator_touch_expansion_width` (`res/values/dimens.xml:33`): the transparent
 * grab strip laid over each adjacent pane's inner edge, so the seam stays 4dp wide but is easy to
 * hit with a finger — classic's `touchDelegateView1/2` (`SplitBibleArea.kt:334-359`).
 */
private val SEPARATOR_TOUCH_EXPANSION = 10.dp

/**
 * Transient (not-yet-committed) drag state for one separator, held at the [SplitContent] level
 * since it owns the panes' [Modifier.weight]: `index`/`index + 1` are the two adjacent panes being
 * resized, `weight1`/`weight2` their current LIVE weights for this in-progress drag. Cleared to
 * `null` once the drag ends and the result is reported via `onSeparatorCommitted`.
 */
private data class ActiveDrag(val index: Int, val weight1: Float, val weight2: Float)

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
 * reports [onWindowActivated].
 *
 * A drag on the separator between two adjacent panes is accumulated locally in pixels and
 * converted to a live weight pair via `separatorDrag` on every drag step (see [ActiveDrag]), so the
 * two adjacent panes resize LIVE as the user drags — no jump on release. Only the dragged pair's
 * panes read the live weights; every other pane keeps its `effectiveWeights` value throughout. The
 * live pair is committed to the model (and `drag` cleared) only once the drag ends, reported via
 * [onSeparatorCommitted] — the caller (SSOT) applies it back into [layout]. When no drag is active,
 * rendering is identical to the pre-live-drag behaviour (plain `effectiveWeights`).
 *
 * [paneOverlay], when non-null, is composed inside every visible pane's `Box` (after [pane]), with
 * that `Box`'s [BoxScope] as its receiver so the caller can anchor it via `Modifier.align(...)` —
 * e.g. the floating ☰ window button, which every pane needs regardless of split orientation. `null`
 * by default, in which case nothing extra is drawn and rendering is byte-identical to before this
 * slot existed.
 *
 * [bottomOverlay], when non-null, is composed as a SIBLING of the panes container (the
 * `Row`/`Column` above), inside the outer `BoxWithConstraints` — i.e. it floats over every pane
 * rather than living inside any one of them, so it survives orientation/pane-count changes
 * unaffected. Used for a single bottom-centre overlay shared across the whole split, e.g. the
 * fullscreen bible-reference overlay. `null` by default, in which case nothing extra is drawn.
 */
@Composable
fun SplitContent(
    layout: WindowLayoutState,
    onWindowActivated: (String) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    pane: @Composable (windowId: String) -> Unit,
    modifier: Modifier = Modifier,
    paneOverlay: (@Composable BoxScope.(windowId: String) -> Unit)? = null,
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
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

        // Transient live-drag override; null when no separator is currently being dragged, in
        // which case rendering below is byte-identical to the pre-live-drag behaviour.
        var drag by remember { mutableStateOf<ActiveDrag?>(null) }
        val paneWeight = { i: Int ->
            val d = drag
            when {
                d != null && i == d.index -> d.weight1
                d != null && i == d.index + 1 -> d.weight2
                else -> weights[i]
            }
        }

        if (isHorizontal) {
            Row(Modifier.fillMaxSize()) {
                windows.forEachIndexed { index, w ->
                    key(w.id) {
                        Box(
                            Modifier
                                .weight(paneWeight(index))
                                .fillMaxSize()
                                .pointerInput(w.id) { detectTapGestures { onWindowActivated(w.id) } },
                        ) {
                            pane(w.id)
                            paneOverlay?.invoke(this, w.id)
                            if (index > 0) DragStrip(
                                windows = windows, weights = weights, index = index - 1,
                                isHorizontalSplit = true, atStartEdge = true,
                                averageExtentPx = { maxWidthPx / windows.size },
                                onDragChange = { drag = it }, onSeparatorCommitted = onSeparatorCommitted,
                            )
                            if (index < windows.lastIndex) DragStrip(
                                windows = windows, weights = weights, index = index,
                                isHorizontalSplit = true, atStartEdge = false,
                                averageExtentPx = { maxWidthPx / windows.size },
                                onDragChange = { drag = it }, onSeparatorCommitted = onSeparatorCommitted,
                            )
                        }
                    }
                    if (index < windows.lastIndex) {
                        Separator(
                            windows = windows,
                            weights = weights,
                            index = index,
                            isVertical = false,
                            isActive = separatorIsActive(layout.activeWindowId, windows[index].id, windows[index + 1].id),
                            isDragging = drag?.index == index,
                            averageExtentPx = { maxWidthPx / windows.size },
                            onDragChange = { drag = it },
                            onSeparatorCommitted = onSeparatorCommitted,
                            modifier = Modifier.fillMaxHeight().width(SEPARATOR_THICKNESS),
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
                                .weight(paneWeight(index))
                                .fillMaxSize()
                                .pointerInput(w.id) { detectTapGestures { onWindowActivated(w.id) } },
                        ) {
                            pane(w.id)
                            paneOverlay?.invoke(this, w.id)
                            if (index > 0) DragStrip(
                                windows = windows, weights = weights, index = index - 1,
                                isHorizontalSplit = false, atStartEdge = true,
                                averageExtentPx = { maxHeightPx / windows.size },
                                onDragChange = { drag = it }, onSeparatorCommitted = onSeparatorCommitted,
                            )
                            if (index < windows.lastIndex) DragStrip(
                                windows = windows, weights = weights, index = index,
                                isHorizontalSplit = false, atStartEdge = false,
                                averageExtentPx = { maxHeightPx / windows.size },
                                onDragChange = { drag = it }, onSeparatorCommitted = onSeparatorCommitted,
                            )
                        }
                    }
                    if (index < windows.lastIndex) {
                        Separator(
                            windows = windows,
                            weights = weights,
                            index = index,
                            isVertical = true,
                            isActive = separatorIsActive(layout.activeWindowId, windows[index].id, windows[index + 1].id),
                            isDragging = drag?.index == index,
                            averageExtentPx = { maxHeightPx / windows.size },
                            onDragChange = { drag = it },
                            onSeparatorCommitted = onSeparatorCommitted,
                            modifier = Modifier.fillMaxWidth().height(SEPARATOR_THICKNESS),
                        )
                    }
                }
            }
        }
        bottomOverlay?.invoke(this)
    }
}

/** The two gesture callbacks a separator drag needs, produced by [rememberSeparatorDragHandlers]. */
private data class DragHandlers(val onDragBy: (Float) -> Unit, val onDragEnd: () -> Unit)

/**
 * The accumulate → `separatorDrag` → live-weight → commit pipeline for the separator between
 * `windows[index]` and `windows[index + 1]`, shared by the painted bar ([Separator]) and the two
 * transparent grab strips ([DragStrip]) so all three drive one identical calculation.
 *
 * Each call site keeps its OWN accumulator, which is correct: a single gesture is delivered to a
 * single composable, and each resets to 0f on drag end.
 */
@Composable
private fun rememberSeparatorDragHandlers(
    windows: List<WindowSnapshot>,
    weights: List<Float>,
    index: Int,
    averageExtentPx: () -> Float,
    onDragChange: (ActiveDrag?) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
): DragHandlers {
    var accumulated by remember(windows[index].id, windows[index + 1].id) { mutableFloatStateOf(0f) }
    val startWeight1 = weights[index]
    val startWeight2 = weights[index + 1]
    return DragHandlers(
        onDragBy = { delta ->
            accumulated += delta
            val live = separatorDrag(accumulated, averageExtentPx(), startWeight1, startWeight2)
            onDragChange(ActiveDrag(index, live.weight1, live.weight2))
        },
        onDragEnd = {
            val live = separatorDrag(accumulated, averageExtentPx(), startWeight1, startWeight2)
            onSeparatorCommitted(windows[index].id, live.weight1, windows[index + 1].id, live.weight2)
            accumulated = 0f
            onDragChange(null)
        },
    )
}

/**
 * One draggable separator between `windows[index]` and `windows[index + 1]`, rendered as a thin
 * [SEPARATOR_THICKNESS]-wide bar with no extra in-flow touch margin — the grab area lives instead in
 * the two transparent [DragStrip]s mounted inside the adjacent panes, both driving this same
 * separator through [rememberSeparatorDragHandlers] so the bar and the strips move as one. On every
 * [WindowSeparator.onDragBy] step, the accumulated raw drag delta (in pixels, reset once the drag
 * ends) is converted to a live weight pair via `separatorDrag`, reporting it up through
 * [onDragChange] so [SplitContent] can render both adjacent panes at their in-progress size.
 * `weights[index]`/`weights[index + 1]` are the start weights for this drag: stable for its whole
 * duration, since the model (and therefore `weights`, recomputed from [layout]) only changes once
 * [onSeparatorCommitted] fires. On drag end, commits the last live pair and clears the live override
 * (`onDragChange(null)`).
 *
 * [isActive]/[isDragging] are computed by the caller (`separatorIsActive` / `drag?.index == index`)
 * and simply forwarded to [WindowSeparator] for its three-state colour (see its kdoc).
 */
@Composable
private fun Separator(
    windows: List<WindowSnapshot>,
    weights: List<Float>,
    index: Int,
    isVertical: Boolean,
    isActive: Boolean,
    isDragging: Boolean,
    averageExtentPx: () -> Float,
    onDragChange: (ActiveDrag?) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    modifier: Modifier,
) {
    val handlers = rememberSeparatorDragHandlers(
        windows, weights, index, averageExtentPx, onDragChange, onSeparatorCommitted,
    )
    WindowSeparator(
        isVertical = isVertical,
        isActive = isActive,
        isDragging = isDragging,
        onDragBy = handlers.onDragBy,
        onDragEnd = handlers.onDragEnd,
        thickness = SEPARATOR_THICKNESS,
        modifier = modifier,
    )
}

/**
 * A transparent, `SEPARATOR_TOUCH_EXPANSION`-thick drag strip laid over one pane's inner edge,
 * driving the separator at [index] through the same [rememberSeparatorDragHandlers] pipeline as the
 * painted bar. Compose port of classic's `touchDelegateView1/2`
 * (`SplitBibleArea.kt:334-359`): the strip is composed as the LAST child of the pane's `Box`, i.e.
 * on top of the pane content, which is what gives it the pointer before the pane's own WebView —
 * exactly like classic adding the delegate view last into its `BibleFrame`.
 *
 * It paints nothing, so the visible seam stays [SEPARATOR_THICKNESS] wide with no gap: the drag
 * area no longer costs the panes any layout space (the previous 16dp in-flow grab size did, and
 * showed as a visible gap above/below the bar).
 *
 * A plain tap is not consumed by `draggable`, so the pane's own tap-to-activate handler still sees
 * it — verified on device (Task 9's checklist item), since this repo has no Compose UI-test harness.
 */
@Composable
private fun BoxScope.DragStrip(
    windows: List<WindowSnapshot>,
    weights: List<Float>,
    index: Int,
    isHorizontalSplit: Boolean,
    atStartEdge: Boolean,
    averageExtentPx: () -> Float,
    onDragChange: (ActiveDrag?) -> Unit,
    onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
) {
    val handlers = rememberSeparatorDragHandlers(
        windows, weights, index, averageExtentPx, onDragChange, onSeparatorCommitted,
    )
    val alignment = when {
        isHorizontalSplit && atStartEdge -> Alignment.CenterStart
        isHorizontalSplit -> Alignment.CenterEnd
        atStartEdge -> Alignment.TopCenter
        else -> Alignment.BottomCenter
    }
    val sizeModifier = if (isHorizontalSplit) {
        Modifier.fillMaxHeight().width(SEPARATOR_TOUCH_EXPANSION)
    } else {
        Modifier.fillMaxWidth().height(SEPARATOR_TOUCH_EXPANSION)
    }
    Box(
        Modifier
            .align(alignment)
            .then(sizeModifier)
            .draggable(
                orientation = if (isHorizontalSplit) Orientation.Horizontal else Orientation.Vertical,
                state = rememberDraggableState { delta -> handlers.onDragBy(delta) },
                onDragStopped = { handlers.onDragEnd() },
            ),
    )
}
