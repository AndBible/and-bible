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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.effectiveWeights
import net.bible.sharedcore.window.separatorDrag
import net.bible.sharedcore.window.separatorIsActive
import net.bible.sharedcore.window.splitIsHorizontal

// Z-late epilogue: the classic resources this file cites by name no longer exist -- they were
// deleted once the Compose reading view replaced what used them. The citations stay as the
// provenance of the numbers below, which is the whole reason they are recorded.
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
 *
 * [railOverlay], when non-null, is composed as a SIBLING of the panes container (like
 * [bottomOverlay]) but is the caller's to align — the window-tab rail aligns it `BottomEnd`,
 * mirroring classic `restoreButtonsContainer`'s `bottom`+`end`-only constraints
 * (`res/layout/split_bible_area.xml:31-38`), so the rail FLOATS over the panes instead of taking a
 * layout band from them. It is a separate slot from [bottomOverlay], not because the two would
 * conflict — both are ordinary caller-aligned `@Composable BoxScope.() -> Unit` slots, and each
 * overlay aligns itself independently, so there is no shared alignment or z-order to compete over —
 * but so [SplitContent] itself guarantees their relative stacking order rather than leaving it to
 * caller discipline: composed last, [railOverlay] always draws above [bottomOverlay]'s bottom-CENTRE
 * fullscreen bible-reference overlay where the two meet. Two distinctly-named slots also read more
 * clearly at the call site than one lambda expected to compose two unrelated pieces of chrome.
 *
 * [paneBackground] gives one pane its background colour, `null` for none — the default, which leaves
 * the pane a bare `Box` exactly as before this parameter existed, so no existing caller or golden
 * changes.
 *
 * A/B batch 4a F5: classic's `BibleFrame` paints each pane in the reader background colour
 * (`BibleFrame.kt:138`, `setBackgroundColor(bibleView.backgroundColor)`); the Compose pane painted
 * nothing, so any frame in which the hosted WebView had not drawn yet showed the window's default
 * white. Creating a window makes that flash cover the WHOLE screen rather than one pane, because
 * `WindowControl.restoreWindow` minimises the other unpinned windows first, leaving the brand-new
 * empty pane as the only visible one. This also closes the reader-background parity gap recorded in
 * the port's deferred list (the defect `and-bible-ios` PR #368 fixed on iOS).
 *
 * A lambda rather than a single colour because two panes can carry different day/night reader
 * backgrounds.
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
    railOverlay: (@Composable BoxScope.() -> Unit)? = null,
    paneBackground: (windowId: String) -> Color? = { null },
) {
    val windows = layout.windows.filter { it.isVisible }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Captured here (BoxWithConstraintsScope is the only implicit receiver in scope) so the
        // averageExtentPx lambdas below — defined inside the nested Row/Column scope — don't need
        // to resolve maxWidth/maxHeight through an ambiguous nested-receiver chain.
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        // A/B F6-B1: the orientation must not follow the keyboard -- see rememberSplitIsHorizontal.
        // F65: the latch resets when the WINDOW's orientation changes (a rotation), which the keyboard
        // never causes on API 30+ (ADJUST_NOTHING leaves the window its full size).
        val windowSize = LocalWindowInfo.current.containerSize
        val isHorizontal = rememberSplitIsHorizontal(
            widthPx = maxWidthPx,
            heightPx = maxHeightPx,
            reverseSplitMode = layout.reverseSplitMode,
            imeVisible = WindowInsets.ime.getBottom(density) > 0,
            resetKey = windowSize.width > windowSize.height,
        )
        val weights = effectiveWeights(windows)

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

        val axisExtentPx = if (isHorizontal) maxWidthPx else maxHeightPx
        // F64 fix round 1: one `movableContentOf` instance per visible window id, so the pane
        // subtree MOVES rather than disposes-and-recreates when SplitAxisContainer's `if` selects
        // the other branch. See rememberPaneContents' kdoc for why the textual hoist alone (round
        // 0) did not fix this: two `content(...)` call POSITIONS -- one inside Row, one inside
        // Column -- are still two places in the composition tree even though they run identical
        // source text.
        val paneContents = rememberPaneContents(windows.mapTo(mutableSetOf()) { it.id })
        SplitAxisContainer(isHorizontal) { paneModifier, separatorModifier ->
            windows.forEachIndexed { index, w ->
                key(w.id) {
                    paneContents.getValue(w.id)(
                        PaneRenderArgs(
                            w = w,
                            index = index,
                            windows = windows,
                            weights = weights,
                            isHorizontal = isHorizontal,
                            paneModifier = paneModifier(paneWeight(index)),
                            averageExtentPx = { axisExtentPx / windows.size },
                            onWindowActivated = onWindowActivated,
                            onDragChange = { drag = it },
                            onSeparatorCommitted = onSeparatorCommitted,
                            pane = pane,
                            paneOverlay = paneOverlay,
                            paneBackground = paneBackground,
                        ),
                    )
                }
                if (index < windows.lastIndex) {
                    Separator(
                        windows = windows,
                        weights = weights,
                        index = index,
                        isVertical = !isHorizontal,
                        isActive = separatorIsActive(layout.activeWindowId, windows[index].id, windows[index + 1].id),
                        isDragging = drag?.index == index,
                        averageExtentPx = { axisExtentPx / windows.size },
                        onDragChange = { drag = it },
                        onSeparatorCommitted = onSeparatorCommitted,
                        modifier = separatorModifier,
                    )
                }
            }
        }
        bottomOverlay?.invoke(this)
        railOverlay?.invoke(this)
    }
}

/**
 * The split's axis, and nothing else. `Modifier.weight` is `RowScope`/`ColumnScope`-specific, so the
 * scope-bound call is handed to the caller as a lambda while the pane subtree itself stays outside
 * the `if`, keeping this the only place `Row` vs `Column`/orientation-dependent modifiers differ.
 *
 * This `if`/`else` is STILL two call positions for whatever `content` composes (round 0's mistake was
 * believing a single textual call site here was enough to stop an orientation flip disposing the pane
 * subtree — it is not: `Row`'s branch and `Column`'s branch are different parents in the composition
 * tree regardless of how many times the shared source text is written). The actual fix for that is
 * [rememberPaneContents] (`movableContentOf`), which SplitContent's pane loop uses precisely because
 * this container cannot, by itself, keep the panes' identity across the two branches (F64).
 */
@Composable
private fun SplitAxisContainer(
    isHorizontal: Boolean,
    content: @Composable (paneModifier: (Float) -> Modifier, separatorModifier: Modifier) -> Unit,
) {
    if (isHorizontal) {
        Row(Modifier.fillMaxSize()) {
            content(
                { w -> Modifier.weight(w).fillMaxSize() },
                Modifier.fillMaxHeight().width(SEPARATOR_THICKNESS),
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            content(
                { w -> Modifier.weight(w).fillMaxSize() },
                Modifier.fillMaxWidth().height(SEPARATOR_THICKNESS),
            )
        }
    }
}

/**
 * Everything one pane's subtree needs to render THIS frame, bundled into a single value so it can
 * be threaded through [movableContentOf] as its one parameter.
 *
 * F64 fix round 1: `movableContentOf` freezes the LAMBDA it wraps at the moment it is created (see
 * [rememberPaneContents]) -- any value that lambda's body reads by closing over an outer `val`/`var`
 * stays fixed to whatever it was back then, even though the pane keeps rendering every frame after.
 * So every value [PaneBody] needs (live drag weights, the current axis, the latest callbacks, ...)
 * MUST arrive through this parameter, freshly built by [SplitContent] on every call, never through a
 * closure captured once.
 */
private data class PaneRenderArgs(
    val w: WindowSnapshot,
    val index: Int,
    val windows: List<WindowSnapshot>,
    val weights: List<Float>,
    val isHorizontal: Boolean,
    val paneModifier: Modifier,
    val averageExtentPx: () -> Float,
    val onWindowActivated: (String) -> Unit,
    val onDragChange: (ActiveDrag?) -> Unit,
    val onSeparatorCommitted: (id1: String, w1: Float, id2: String, w2: Float) -> Unit,
    val pane: @Composable (windowId: String) -> Unit,
    val paneOverlay: (@Composable BoxScope.(windowId: String) -> Unit)?,
    val paneBackground: (windowId: String) -> Color?,
)

/**
 * One pane's whole subtree (the weighted `Box`, [pane] itself, [paneOverlay], and both [DragStrip]s)
 * as a single composable, so it can be wrapped whole by [movableContentOf] in [rememberPaneContents].
 * `pane` and `key(w.id)` are bound to local `val`s (not read via `args.pane`/`args.w.id`) so the
 * unqualified calls stay literally `pane(w.id)`/`key(w.id)` for `SplitContentOneCallSiteGuardTest`'s
 * regex, which is kept as a cheap textual sanity check but is no longer the proof of F64 --
 * `SplitContentOrientationFlipMountGuardTest` is: it drives a real orientation flip and asserts
 * neither pane is disposed, which the regex cannot see (a source-text count of one is unchanged
 * whether that one call site sits in one place or behind an `if`/`else`).
 */
@Composable
private fun PaneBody(args: PaneRenderArgs) {
    val w = args.w
    val pane = args.pane
    Box(
        args.paneModifier
            // A pane must never paint outside itself. Compose does NOT clip children to their
            // bounds by default, and the hosted WebView is an Android View that can be laid out
            // larger than the pane for a frame while the split settles — which drew the reader
            // background over the panes above it AND over the toolbar/system bar when a window was
            // created (A/B batch 4a F5, the symptom the pane background alone did not fix).
            .clipToBounds()
            // Before the tap handler so the fill covers the whole pane.
            .then(args.paneBackground(w.id)?.let { Modifier.background(it) } ?: Modifier)
            .pointerInput(w.id) { detectTapGestures { args.onWindowActivated(w.id) } },
    ) {
        pane(w.id)
        args.paneOverlay?.invoke(this, w.id)
        if (args.index > 0) DragStrip(
            windows = args.windows, weights = args.weights, index = args.index - 1,
            isHorizontalSplit = args.isHorizontal, atStartEdge = true,
            averageExtentPx = args.averageExtentPx,
            onDragChange = args.onDragChange, onSeparatorCommitted = args.onSeparatorCommitted,
        )
        if (args.index < args.windows.lastIndex) DragStrip(
            windows = args.windows, weights = args.weights, index = args.index,
            isHorizontalSplit = args.isHorizontal, atStartEdge = false,
            averageExtentPx = args.averageExtentPx,
            onDragChange = args.onDragChange, onSeparatorCommitted = args.onSeparatorCommitted,
        )
    }
}

/**
 * One [movableContentOf] instance per visible window id, cached across recompositions -- and
 * crucially across an orientation flip -- so [PaneBody] (and the [PaneRenderArgs.pane] it hosts,
 * an `AndroidView`-wrapped `BibleView` in the real app) is MOVED rather than disposed and recreated
 * when `SplitAxisContainer`'s `if (isHorizontal)` starts selecting the other branch.
 *
 * `key(w.id)` alone cannot do this: it only preserves identity within the SAME parent slot, and
 * `Row`'s branch and `Column`'s branch are different parents in the composition tree, even when both
 * call identical source text (round 0's mistake). `movableContentOf` is the mechanism Compose itself
 * provides for exactly this "same logical child, different structural parent this frame" case (the
 * same pattern used to move a list between differently-oriented adaptive layouts).
 *
 * Window ids no longer present are dropped from the map so that pane's composition disposes
 * normally (closing a window) rather than leaking a `movableContentOf` instance forever.
 */
@Composable
private fun rememberPaneContents(windowIds: Set<String>): Map<String, @Composable (PaneRenderArgs) -> Unit> {
    val contents = remember { mutableMapOf<String, @Composable (PaneRenderArgs) -> Unit>() }
    contents.keys.retainAll(windowIds)
    windowIds.forEach { id ->
        contents.getOrPut(id) { movableContentOf { args: PaneRenderArgs -> PaneBody(args) } }
    }
    return contents
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
 * it — this repo has no Compose UI-test harness, so the tap-vs-drag disambiguation is deferred to
 * the device A/B checklist (Task 9's item), not yet verified on hardware.
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

/**
 * [splitIsHorizontal] with its latch: the last committed answer is held while the IME is visible
 * (A/B F6-B1 -- the keyboard's shrink of the measured height must not flip the split), and the latch
 * is DROPPED whenever [resetKey] changes.
 *
 * F65: the latch used to be a plain `remember { }`. That was only correct on classic, where
 * `MainBibleActivity`'s `configChanges` omits `orientation` so a rotation recreates the Activity and the
 * latch with it. `NavHostComposeActivity` DECLARES `orientation` (`AndroidManifest.xml`), so the
 * composition survives a rotation and the latch carried the pre-rotation answer across it: rotate with
 * the keyboard up and the split stayed in the old orientation until the keyboard closed. [SplitContent]
 * passes the window's own orientation as [resetKey] -- something a rotation changes and the keyboard
 * does not.
 *
 * Recorded in a SideEffect rather than assigned during composition, because writing snapshot state
 * during composition is exactly the pattern Compose warns about.
 */
@Composable
fun rememberSplitIsHorizontal(
    widthPx: Float,
    heightPx: Float,
    reverseSplitMode: Boolean,
    imeVisible: Boolean,
    resetKey: Any?,
): Boolean {
    var latchedHorizontal by remember(resetKey) { mutableStateOf<Boolean?>(null) }
    val isHorizontal = splitIsHorizontal(
        widthPx = widthPx,
        heightPx = heightPx,
        reverseSplitMode = reverseSplitMode,
        imeVisible = imeVisible,
        previous = latchedHorizontal,
    )
    SideEffect { latchedHorizontal = isHorizontal }
    return isHorizontal
}
