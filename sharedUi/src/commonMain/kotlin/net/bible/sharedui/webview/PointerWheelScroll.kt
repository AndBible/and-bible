package net.bible.sharedui.webview

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A native scroll view that [wheelScrolls] can move. [scrollBy] gets the pointer position in the element ([xPoints],
 * [yPoints]) and the movement in points (positive: content moves up/left, i.e. the offset grows), and returns whether
 * the event belongs to this target (false: leave it unconsumed). Implementations must have value equality: the
 * pointer-input coroutine is keyed on the target.
 */
fun interface WheelScrollTarget {
    fun scrollBy(xPoints: Double, yPoints: Double, dxPoints: Double, dyPoints: Double): Boolean

    /** Whether a finger is dragging the native view right now: a running glide stops instead of fighting it. */
    fun isTouchActive(): Boolean = false

    /** A gesture-level diagnostic line (one per gesture end), for tuning on a device. */
    fun diagnostic(message: String) {}
}

/**
 * Feeds the Compose `Scroll` pointer events (mouse wheel, trackpad) that land on this element to [target], converted
 * back to points with [PointerWheelScroll.deltaPoints], and consumes them. Fast mouse-wheel spins accelerate, and a
 * trackpad flick glides on after the fingers lift, decelerating like a `UIScrollView` ([WheelMomentum], [WheelFling]):
 * the glide frames go through [target] too, so the receiver logic (inner scrollable boxes first) stays the same. The
 * gesture end comes from [WheelGestureSignals] (the platform's observer) or, while no observer has reported, from
 * CMP's zero-delta `Scroll` event that closes a pan. A new gesture, new movement, a press or a finger on the native view
 * ([WheelScrollTarget.isTouchActive]) stops a glide.
 *
 * Why this exists: on iOS (Mac "Designed for iPad", iPad with a pointer) Compose Multiplatform's input overlay
 * answers every non-touch `UIEvent` (scroll, hover) itself in `OverlayInputView.hitTest` (checked 1.11.1 to
 * 1.13.0-beta01), so wheel and trackpad scrolls never reach a `UIKitView` placed below the canvas: Compose turns them
 * into its own `Scroll` pointer events instead, without phase, velocity or momentum. Compose's hit test picks the
 * receiver, so a sheet or menu drawn over the pane keeps its own scrolls. Touch never produces `Scroll` events.
 */
fun Modifier.wheelScrolls(target: WheelScrollTarget): Modifier = pointerInput(target) {
    coroutineScope {
        val momentum = WheelMomentum()
        var glide: Job? = null
        var lastX = 0.0
        var lastY = 0.0

        fun stopGlide() {
            glide?.cancel()
            glide = null
        }

        fun end(endMs: Long, kind: WheelGestureKind, platformVy: Double?) {
            if (!momentum.hasMovement) return
            val fling = momentum.onGestureEnded(endMs, kind)
            target.diagnostic(describeGestureEnd(kind, fling, platformVy, momentum.lastWheelGain))
            if (fling == null) return
            stopGlide()
            val x = lastX
            val y = lastY
            glide = launch {
                var startMs = -1L
                var doneX = 0.0
                var doneY = 0.0
                while (true) {
                    val frameMs = withFrameMillis { it }
                    if (startMs < 0) startMs = frameMs
                    if (target.isTouchActive()) break
                    val elapsed = (frameMs - startMs).toDouble()
                    val (ox, oy) = fling.offsetAt(elapsed)
                    val dx = ox - doneX
                    val dy = oy - doneY
                    doneX = ox
                    doneY = oy
                    if (dx != 0.0 || dy != 0.0) target.scrollBy(x, y, dx, dy)
                    if (elapsed >= fling.durationMs) break
                }
            }
        }

        launch {
            WheelGestureSignals.signals.collect { signal ->
                when (signal) {
                    is WheelGestureSignal.Began -> {
                        stopGlide()
                        momentum.onGestureBegan()
                    }
                    is WheelGestureSignal.Ended -> end(signal.timeMs, signal.kind, signal.platformVelocityY)
                    WheelGestureSignal.Cancelled -> momentum.onGestureBegan()
                }
            }
        }

        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press) {
                    stopGlide()
                    continue
                }
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val dx = PointerWheelScroll.deltaPoints(change.scrollDelta.x, density)
                val dy = PointerWheelScroll.deltaPoints(change.scrollDelta.y, density)
                val kind = WheelGestureSignals.currentKind
                if (dx == 0.0 && dy == 0.0) {
                    // CMP sends the pan's began and ended as zero-delta events, but resting fingers can produce one
                    // too. Only without a platform observer (kind still unknown) is it taken as the end.
                    if (kind == WheelGestureKind.UNKNOWN) end(change.uptimeMillis, kind, null)
                    continue
                }
                stopGlide()
                // Positions are plain pixels of this element; points = px / density.
                val x = change.position.x.toDouble() / density
                val y = change.position.y.toDouble() / density
                val (sx, sy) = momentum.onMove(change.uptimeMillis, dx, dy, kind)
                if (target.scrollBy(x, y, sx, sy)) {
                    change.consume()
                    lastX = x
                    lastY = y
                } else {
                    momentum.onGestureBegan() // not ours: nothing to glide
                }
            }
        }
    }
}

/** Arithmetic for scrolling a native view from Compose `Scroll` pointer events; see [wheelScrolls]. */
object PointerWheelScroll {
    /**
     * CMP iOS reports `scrollDelta = pan delta (points) * density * 0.01` (`SCROLL_DELTA_MULTIPLIER` in
     * `ComposeSceneMediator.ios.kt`); this undoes it. Positive means the content moves up (offset grows).
     */
    fun deltaPoints(scrollDelta: Float, density: Float): Double =
        if (density <= 0f) 0.0 else scrollDelta.toDouble() / COMPOSE_SCROLL_DELTA_MULTIPLIER / density

    /**
     * The new content offset along one axis after moving [current] by [delta], kept inside
     * `[-insetStart, contentLength - viewportLength + insetEnd]` (UIScrollView's own limits with its adjusted insets).
     */
    fun scrolledOffset(
        current: Double,
        delta: Double,
        contentLength: Double,
        viewportLength: Double,
        insetStart: Double,
        insetEnd: Double,
    ): Double {
        val min = -insetStart
        val max = maxOf(min, contentLength - viewportLength + insetEnd)
        return (current + delta).coerceIn(min, max)
    }

    private const val COMPOSE_SCROLL_DELTA_MULTIPLIER = 0.01
}
