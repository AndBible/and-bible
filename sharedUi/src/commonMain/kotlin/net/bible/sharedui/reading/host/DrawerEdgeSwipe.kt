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

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Width of the leading-edge strip that drags the main menu open: classic `DrawerLayout`'s 20dp edge size. */
internal val DrawerEdgeSwipeWidth: Dp = 20.dp

/**
 * Android allows at most 200dp of system-gesture exclusion per screen edge; the rest of a larger
 * request is ignored. See [drawerEdgeGestureExclusion].
 */
internal val DrawerEdgeExclusionMaxHeight: Dp = 200.dp

/** Where the excluded band starts, as a fraction of the reading area's height (upper part, below the toolbar). */
internal const val DrawerEdgeExclusionTopFraction = 0.2f

/** A release faster than this (inward or outward) decides open/closed regardless of how far the drawer came. */
internal val DrawerEdgeSettleVelocity: Dp = 400.dp

/**
 * On Android with gesture navigation, exempts a band of the leading edge strip from the system back
 * gesture so the edge swipe reaches the app there. Android caps exclusions at
 * [DrawerEdgeExclusionMaxHeight] per edge, so outside the band the system back gesture keeps the edge.
 * No-op on iOS and desktop.
 */
internal expect fun Modifier.drawerEdgeGestureExclusion(enabled: Boolean, rtl: Boolean): Modifier

/** Whether [x] (px, in the strip owner's coordinates) lies in the leading [edgePx] of a [width]-wide area. */
internal fun isInLeadingEdge(x: Float, width: Int, edgePx: Float, rtl: Boolean): Boolean =
    if (rtl) x >= width - edgePx else x <= edgePx

/**
 * The settle decision at the end of an edge drag: a fling ([inwardVelocity], px/s, positive towards
 * the open position) beyond [velocityThreshold] decides by its direction; otherwise the drawer opens
 * when it was dragged at least half open ([fraction] of its width).
 */
internal fun edgeSwipeSettlesOpen(fraction: Float, inwardVelocity: Float, velocityThreshold: Float): Boolean = when {
    inwardVelocity >= velocityThreshold -> true
    inwardVelocity <= -velocityThreshold -> false
    else -> fraction >= 0.5f
}

/** Callbacks of [drawerEdgeSwipe]; distances and velocities are px, positive towards the open position. */
internal interface DrawerEdgeSwipeCallbacks {
    /** The drag passed the touch slop inwards; it is now the drawer's. */
    fun onDragStart()

    /** The finger is [distance] px inwards from where the drag started (after the slop). */
    fun onDrag(distance: Float)

    /** The finger lifted with [inwardVelocity] px/s, or the gesture was cancelled (velocity 0). */
    fun onDragEnd(inwardVelocity: Float)
}

/**
 * Detects a horizontal drag that starts in the leading [DrawerEdgeSwipeWidth] of this node and moves
 * inwards (mirrored for [rtl]), and hands it to [callbacks].
 *
 * It watches the [PointerEventPass.Initial] pass of an ANCESTOR of the reading panes, so it sees the
 * events before the panes (and their WebViews, which take horizontal drags themselves) do, and
 * consumes nothing until the drag passes the touch slop inwards: taps on the hamburger button or near
 * the left margin of the text still reach their targets, a vertical scroll starting at the edge stays
 * a scroll, and a horizontal swipe in the text (chapter change) outside the strip is never seen here.
 * Once it is the drawer's, every change is consumed so the panes get a cancel, as classic
 * `DrawerLayout`'s intercept did. Mouse input is ignored: on desktop a mouse drag at the margin is a
 * text selection.
 */
internal fun Modifier.drawerEdgeSwipe(
    enabled: Boolean,
    rtl: Boolean,
    callbacks: DrawerEdgeSwipeCallbacks,
): Modifier = if (!enabled) this else pointerInput(rtl, callbacks) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type == PointerType.Mouse) return@awaitEachGesture
        if (!isInLeadingEdge(down.position.x, size.width, DrawerEdgeSwipeWidth.toPx(), rtl)) return@awaitEachGesture
        val sign = if (rtl) -1f else 1f
        val slop = viewConfiguration.touchSlop
        var dx = 0f
        var dy = 0f
        // Pre-slop: watch only, consume nothing.
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            val delta = change.positionChange()
            dx += delta.x * sign
            dy += delta.y
            if (abs(dy) > slop && abs(dy) >= abs(dx)) return@awaitEachGesture
            if (dx < -slop) return@awaitEachGesture
            if (dx > slop) {
                change.consume()
                break
            }
        }
        val tracker = VelocityTracker()
        var distance = 0f
        var ended = false
        callbacks.onDragStart()
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                // Read before consume(): a consumed change reports no position change.
                val delta = change.positionChange().x * sign
                change.consume()
                if (!change.pressed) {
                    ended = true
                    callbacks.onDragEnd(tracker.calculateVelocity().x * sign)
                    break
                }
                distance += delta
                callbacks.onDrag(distance)
            }
        } finally {
            if (!ended) callbacks.onDragEnd(0f)
        }
    }
}

/**
 * Drives an M3 [DrawerState] from [drawerEdgeSwipe]: the sheet follows the finger and settles open or
 * closed by [edgeSwipeSettlesOpen].
 *
 * `DrawerState` has no public drag API (its `AnchoredDraggableState` is internal), so following the
 * finger runs one open animation whose spec ([FingerTrackingSpec]) never finishes and returns the
 * finger's position on every frame; the release then interrupts it with the drawer's own
 * [DrawerState.open] / [DrawerState.close], which animate on from where the finger left the sheet.
 *
 * With [followFinger] false (the no-animations setting) or a zero [MotionDurationScale] the drag only
 * [requestOpen]s once it passes the slop, so the drawer appears at once as it does from the toolbar button.
 */
internal class DrawerEdgeDrag(
    private val drawerState: DrawerState,
    private val scope: CoroutineScope,
    private val velocityThresholdPx: Float,
    private val followFinger: () -> Boolean,
    private val requestOpen: () -> Unit,
) : DrawerEdgeSwipeCallbacks {
    private var job: Job? = null
    private var closedOffset = Float.NaN
    private var tracking = false

    /** The finger's distance, read on every animation frame by [FingerTrackingSpec]. */
    private var distance = 0f

    override fun onDragStart() {
        val offset = drawerState.currentOffset
        // A zero motion scale (Android's animator scale 0, or the app's no-animations setting there)
        // finishes the tracking animation at once, at its target: the sheet would jump open and a slow
        // release could close it again. Treat it like the no-animations setting.
        val animationsRun = (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
        tracking = followFinger() && animationsRun && !offset.isNaN() && offset < 0f
        if (!tracking) {
            requestOpen()
            return
        }
        closedOffset = offset
        distance = 0f
        job?.cancel()
        job = scope.launch {
            @Suppress("DEPRECATION") // The only public way to move the sheet to an arbitrary offset; see the kdoc.
            drawerState.animateTo(DrawerValue.Open, FingerTrackingSpec { distance })
        }
    }

    override fun onDrag(distance: Float) {
        this.distance = distance
    }

    override fun onDragEnd(inwardVelocity: Float) {
        if (!tracking) return
        tracking = false
        val width = -closedOffset
        val fraction = (distance / width).coerceIn(0f, 1f)
        val open = edgeSwipeSettlesOpen(fraction, inwardVelocity, velocityThresholdPx)
        job?.cancel()
        job = null
        // The host's isClosed mirror turns a settled open into its drawerOpen request.
        scope.launch { if (open) drawerState.open() else drawerState.close() }
    }
}

/**
 * An animation spec that never finishes and places the value [distance] px from its start towards its
 * target, clamped to that range: the drawer sheet follows the finger for as long as the animation runs.
 */
internal class FingerTrackingSpec(private val distance: () -> Float) : FiniteAnimationSpec<Float> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Float, V>): VectorizedFiniteAnimationSpec<V> =
        object : VectorizedFiniteAnimationSpec<V> {
            // The converter is Float's, so every vector here is an AnimationVector1D.
            @Suppress("UNCHECKED_CAST")
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
                val start = (initialValue as AnimationVector1D).value
                val span = (targetValue as AnimationVector1D).value - start
                val moved = distance().coerceIn(0f, abs(span))
                return AnimationVector1D(start + if (span < 0f) -moved else moved) as V
            }

            @Suppress("UNCHECKED_CAST")
            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                AnimationVector1D(0f) as V

            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long = Long.MAX_VALUE / 2
        }
}
