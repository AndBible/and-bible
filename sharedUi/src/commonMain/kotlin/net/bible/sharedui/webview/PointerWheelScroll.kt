package net.bible.sharedui.webview

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A native scroll view that [wheelScrolls] can move. [scrollBy] gets the pointer position in the element ([xPoints],
 * [yPoints]) and the movement in points (positive: content moves up/left, i.e. the offset grows), and returns whether
 * the event belongs to this target (false: leave it unconsumed). Implementations must have value equality: the
 * pointer-input coroutine is keyed on the target.
 */
fun interface WheelScrollTarget {
    fun scrollBy(xPoints: Double, yPoints: Double, dxPoints: Double, dyPoints: Double): Boolean
}

/**
 * Feeds the Compose `Scroll` pointer events (mouse wheel, trackpad) that land on this element to [target], converted
 * back to points with [PointerWheelScroll.deltaPoints], and consumes them.
 *
 * Why this exists: on iOS (Mac "Designed for iPad", iPad with a pointer) Compose Multiplatform's input overlay
 * answers every non-touch `UIEvent` (scroll, hover) itself in `OverlayInputView.hitTest` (checked 1.11.1 to
 * 1.13.0-beta01), so wheel and trackpad scrolls never reach a `UIKitView` placed below the canvas: Compose turns them
 * into its own `Scroll` pointer events instead. Compose's hit test picks the receiver, so a sheet or menu drawn over
 * the pane keeps its own scrolls. Touch never produces `Scroll` events.
 */
fun Modifier.wheelScrolls(target: WheelScrollTarget): Modifier = pointerInput(target) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.isConsumed) continue
            val dx = PointerWheelScroll.deltaPoints(change.scrollDelta.x, density)
            val dy = PointerWheelScroll.deltaPoints(change.scrollDelta.y, density)
            if (dx == 0.0 && dy == 0.0) continue
            // Positions are plain pixels of this element; points = px / density.
            val x = change.position.x.toDouble() / density
            val y = change.position.y.toDouble() / density
            if (target.scrollBy(x, y, dx, dy)) change.consume()
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
