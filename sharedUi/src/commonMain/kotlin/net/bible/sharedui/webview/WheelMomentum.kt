package net.bible.sharedui.webview

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/** What produced a wheel/trackpad scroll gesture, as far as the platform can tell. */
enum class WheelGestureKind {
    /** No platform signal (the Compose events alone). Treated like [CONTINUOUS] for gliding, never accelerated. */
    UNKNOWN,

    /** A trackpad or Magic Mouse swipe: glides on after the fingers lift. */
    CONTINUOUS,

    /** Mouse wheel notches: no glide, but fast spins accelerate. */
    DISCRETE,
}

/** A platform report about the scroll gesture behind the Compose `Scroll` events; see [WheelGestureSignals]. */
sealed interface WheelGestureSignal {
    /** Fingers touched the trackpad / a wheel started turning: any glide stops. */
    data class Began(val kind: WheelGestureKind) : WheelGestureSignal

    /**
     * The gesture ended (fingers lifted) at [timeMs] (same clock as the pointer events' `uptimeMillis`).
     * [platformVelocityX]/[platformVelocityY] (points/s, offset direction) are the platform's own estimate, logged for
     * tuning only.
     */
    data class Ended(
        val kind: WheelGestureKind,
        val timeMs: Long,
        val platformVelocityX: Double,
        val platformVelocityY: Double,
    ) : WheelGestureSignal

    /** The platform cancelled the gesture: forget it without gliding. */
    data object Cancelled : WheelGestureSignal
}

/**
 * Process-wide channel from the platform's scroll gesture observer (iOS: `IosWheelGestureObserver`) to every
 * [wheelScrolls] element. Compose's `Scroll` events carry neither the gesture phase nor its kind, so the platform
 * reports them here; only the element that received the gesture's movement acts on [WheelGestureSignal.Ended].
 * Without any platform signal [wheelScrolls] still glides: it takes a zero-delta `Scroll` event after movement (CMP's
 * gesture-end event) as the end.
 */
object WheelGestureSignals {
    private val flow = MutableSharedFlow<WheelGestureSignal>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val signals: SharedFlow<WheelGestureSignal> = flow

    /** The kind of the gesture in progress (last [WheelGestureSignal.Began]); [WheelGestureKind.UNKNOWN] without a platform observer. */
    var currentKind: WheelGestureKind = WheelGestureKind.UNKNOWN
        private set

    fun emit(signal: WheelGestureSignal) {
        if (signal is WheelGestureSignal.Began) currentKind = signal.kind
        flow.tryEmit(signal)
    }
}

/**
 * A glide after a trackpad flick, decelerating like `UIScrollView` with `DecelerationRate.normal`: the speed is
 * multiplied by [RATE] every millisecond. Velocities are points/s in content-offset direction (positive: the offset
 * grows, as in [WheelScrollTarget.scrollBy]).
 */
class WheelFling(val velocityX: Double, val velocityY: Double) {
    /** Milliseconds until the speed falls under [STOP_VELOCITY]. */
    val durationMs: Double = run {
        val speed = hypot(velocityX, velocityY)
        if (speed <= STOP_VELOCITY) 0.0 else ln(STOP_VELOCITY / speed) / LN_RATE
    }

    /** Offset moved [elapsedMs] after the release (x, y), in points; constant after [durationMs]. */
    fun offsetAt(elapsedMs: Double): Pair<Double, Double> {
        val t = elapsedMs.coerceIn(0.0, durationMs)
        // Integral of v * RATE^t over [0, t], v in points/ms.
        val factor = (RATE.pow(t) - 1.0) / LN_RATE / 1000.0
        return velocityX * factor to velocityY * factor
    }

    companion object {
        /** `UIScrollView.DecelerationRate.normal`. */
        const val RATE = 0.998
        private val LN_RATE = ln(RATE)

        /** Points/s under which the glide stops (about a quarter point per frame). */
        const val STOP_VELOCITY = 15.0
    }
}

/**
 * Per-element state of the wheel/trackpad scrolling of [wheelScrolls]: tracks the gesture's recent movement for the
 * release velocity ([onGestureEnded] gives the [WheelFling] to run, or null) and accelerates fast mouse-wheel spins
 * ([onMove]). Times are milliseconds on one clock (pointer `uptimeMillis`); deltas are points in offset direction.
 *
 * Why: CMP turns Mac/iPad wheel and trackpad scrolls into Compose `Scroll` events that stop when the fingers lift (no
 * momentum phase) and keep the WKWebView's own UIScrollView out of the gesture, so its native deceleration never runs.
 */
class WheelMomentum {
    private class Move(val timeMs: Long, val dx: Double, val dy: Double)

    private val moves = ArrayDeque<Move>()
    private var lastWheelMs = Long.MIN_VALUE
    private var wheelGain = 1.0

    /** Whether movement of the current gesture is waiting for its end. */
    val hasMovement: Boolean get() = moves.isNotEmpty()

    /** Last computed acceleration gain of the mouse wheel (diagnostics). */
    val lastWheelGain: Double get() = wheelGain

    /**
     * Records one movement and returns it as it should scroll (x, y): unchanged for trackpads (macOS has already
     * accelerated those), multiplied by a gain that grows with the notch rate for a [WheelGestureKind.DISCRETE] wheel.
     */
    fun onMove(timeMs: Long, dx: Double, dy: Double, kind: WheelGestureKind): Pair<Double, Double> {
        var gain = 1.0
        if (kind == WheelGestureKind.DISCRETE) {
            val interval = if (lastWheelMs == Long.MIN_VALUE) Long.MAX_VALUE else timeMs - lastWheelMs
            lastWheelMs = timeMs
            gain = wheelGainFor(interval)
            wheelGain = gain
        }
        moves.addLast(Move(timeMs, dx * gain, dy * gain))
        while (moves.size > 1 && timeMs - moves.first().timeMs > VELOCITY_WINDOW_MS) moves.removeFirst()
        return dx * gain to dy * gain
    }

    /** A new gesture began: forget the previous gesture's movement. */
    fun onGestureBegan() = moves.clear()

    /**
     * The gesture ended at [endMs]: the glide to run, or null (a wheel, a single step, too slow, or the fingers rested
     * before lifting). Consumes the movement, so a second end report of the same lift returns null.
     */
    fun onGestureEnded(endMs: Long, kind: WheelGestureKind): WheelFling? {
        val recent = moves.filter { endMs - it.timeMs <= VELOCITY_WINDOW_MS }
        moves.clear()
        if (kind == WheelGestureKind.DISCRETE || recent.size < 2) return null
        if (endMs - recent.last().timeMs > STALE_MS) return null
        val span = (recent.last().timeMs - recent.first().timeMs).toDouble()
        if (span <= 0.0) return null
        // The first move's own interval is unknown; it only marks the window's start.
        val sumX = recent.drop(1).sumOf { it.dx }
        val sumY = recent.drop(1).sumOf { it.dy }
        var vx = sumX / span * 1000.0
        var vy = sumY / span * 1000.0
        val speed = hypot(vx, vy)
        if (speed < MIN_VELOCITY) return null
        if (speed > MAX_VELOCITY) {
            vx *= MAX_VELOCITY / speed
            vy *= MAX_VELOCITY / speed
        }
        return WheelFling(vx, vy)
    }

    companion object {
        /** Release velocity comes from the movement of the last this-many milliseconds. */
        const val VELOCITY_WINDOW_MS = 100L

        /** A lift this long after the last movement is a rest, not a flick. */
        const val STALE_MS = 60L

        /** Points/s: slower releases stop dead, like UIKit's. */
        const val MIN_VELOCITY = 60.0

        /** Points/s cap of a glide. */
        const val MAX_VELOCITY = 10_000.0

        /** Most a fast wheel spin multiplies a notch. */
        const val MAX_WHEEL_GAIN = 5.0

        /** Notches at most this often (ms apart) move one-to-one. */
        const val SLOW_WHEEL_INTERVAL_MS = 80L

        /** Notches this close (ms apart) or closer get [MAX_WHEEL_GAIN]. */
        const val FAST_WHEEL_INTERVAL_MS = 10L

        /** Gain for a wheel notch [intervalMs] after the previous one: 1 when slow, rising with the notch rate. */
        fun wheelGainFor(intervalMs: Long): Double {
            if (intervalMs >= SLOW_WHEEL_INTERVAL_MS) return 1.0
            val slowRate = 1000.0 / SLOW_WHEEL_INTERVAL_MS
            val fastRate = 1000.0 / FAST_WHEEL_INTERVAL_MS
            val rate = 1000.0 / intervalMs.coerceAtLeast(1L)
            val f = ((rate - slowRate) / (fastRate - slowRate)).coerceIn(0.0, 1.0)
            return 1.0 + (MAX_WHEEL_GAIN - 1.0) * f
        }
    }
}

/** Short `MAC-SCROLL` diagnostic text of a gesture end (for tuning from the maintainer's Console log). */
internal fun describeGestureEnd(kind: WheelGestureKind, fling: WheelFling?, platformVy: Double?, wheelGain: Double): String {
    fun r(v: Double) = (v * 10).toLong() / 10.0
    val glide = fling?.let { "glide vy=${r(it.velocityY)} pt/s dist=${r(it.offsetAt(it.durationMs).second)} pt ${r(it.durationMs)} ms" } ?: "no glide"
    val uikit = platformVy?.let { " uikit vy=${r(it)}" } ?: ""
    val gain = if (kind == WheelGestureKind.DISCRETE) " gain=${r(wheelGain)}" else ""
    return "end kind=$kind $glide$uikit$gain"
}
