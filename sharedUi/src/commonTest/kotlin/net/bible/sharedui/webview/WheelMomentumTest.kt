package net.bible.sharedui.webview

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure arithmetic of trackpad momentum and wheel acceleration; see [WheelMomentum]. */
class WheelMomentumTest {
    private fun WheelMomentum.swipe(startMs: Long, count: Int, intervalMs: Long, dy: Double, kind: WheelGestureKind = WheelGestureKind.CONTINUOUS): Long {
        var t = startMs
        repeat(count) {
            t += intervalMs
            onMove(t, 0.0, dy, kind)
        }
        return t
    }

    @Test
    fun aTrackpadFlickGlidesOnWithItsReleaseVelocity() {
        val m = WheelMomentum()
        val end = m.swipe(1000, count = 8, intervalMs = 10, dy = 20.0) // 2000 pt/s
        val fling = assertNotNull(m.onGestureEnded(end + 5, WheelGestureKind.CONTINUOUS))
        assertEquals(2000.0, fling.velocityY, 1.0)
        assertEquals(0.0, fling.velocityX)
        // UIScrollView's normal deceleration: total glide = v / 1000 * r / (1 - r) points (r = 0.998 per ms), ~998 pt.
        val total = fling.offsetAt(fling.durationMs).second
        assertTrue(abs(total - 2000.0 / 1000 * 0.998 / 0.002) < 30, "glide $total")
        assertTrue(fling.offsetAt(100.0).second > 0, "glides the same way the content moved")
    }

    @Test
    fun theGlideSlowsDownAndStops() {
        val fling = WheelFling(0.0, 1500.0)
        val first = fling.offsetAt(16.0).second
        val later = fling.offsetAt(1016.0).second - fling.offsetAt(1000.0).second
        assertTrue(later in 0.0..first / 2, "frame steps $first then $later")
        assertTrue(fling.durationMs in 1000.0..5000.0, "duration ${fling.durationMs}")
        assertEquals(fling.offsetAt(fling.durationMs).second, fling.offsetAt(fling.durationMs + 500).second)
    }

    @Test
    fun liftingAfterAPauseDoesNotGlide() {
        val m = WheelMomentum()
        val end = m.swipe(1000, count = 8, intervalMs = 10, dy = 20.0)
        assertNull(m.onGestureEnded(end + 200, WheelGestureKind.CONTINUOUS), "fingers rested before lifting")
    }

    @Test
    fun aSlowDragDoesNotGlide() {
        val m = WheelMomentum()
        val end = m.swipe(1000, count = 8, intervalMs = 16, dy = 0.5) // ~31 pt/s
        assertNull(m.onGestureEnded(end, WheelGestureKind.CONTINUOUS))
    }

    @Test
    fun aSingleMoveNeverGlides() {
        // One wheel notch delivered as began/changed/ended must not turn into a long glide.
        val m = WheelMomentum()
        m.onMove(1000, 0.0, 40.0, WheelGestureKind.UNKNOWN)
        assertNull(m.onGestureEnded(1001, WheelGestureKind.UNKNOWN))
    }

    @Test
    fun aMouseWheelNeverGlides() {
        val m = WheelMomentum()
        val end = m.swipe(1000, count = 8, intervalMs = 10, dy = 20.0, kind = WheelGestureKind.DISCRETE)
        assertNull(m.onGestureEnded(end, WheelGestureKind.DISCRETE))
    }

    @Test
    fun endingTwiceGlidesOnce() {
        // The UIKit end signal and Compose's zero-delta end event both report the same lift.
        val m = WheelMomentum()
        val end = m.swipe(1000, count = 8, intervalMs = 10, dy = 20.0)
        assertNotNull(m.onGestureEnded(end, WheelGestureKind.CONTINUOUS))
        assertNull(m.onGestureEnded(end + 1, WheelGestureKind.CONTINUOUS))
    }

    @Test
    fun aNewGestureForgetsTheOldMovement() {
        val m = WheelMomentum()
        m.swipe(1000, count = 8, intervalMs = 10, dy = 20.0)
        m.onGestureBegan()
        assertNull(m.onGestureEnded(1085, WheelGestureKind.CONTINUOUS))
    }

    @Test
    fun theReleaseVelocityUsesOnlyTheLastMovement() {
        val m = WheelMomentum()
        var t = m.swipe(1000, count = 20, intervalMs = 10, dy = 50.0) // fast start, 5000 pt/s
        t = m.swipe(t, count = 15, intervalMs = 10, dy = 5.0) // slowing down to 500 pt/s before the lift
        val fling = assertNotNull(m.onGestureEnded(t, WheelGestureKind.CONTINUOUS))
        assertEquals(500.0, fling.velocityY, 1.0)
    }

    @Test
    fun theGlideSpeedIsCapped() {
        val m = WheelMomentum()
        val end = m.swipe(1000, count = 8, intervalMs = 1, dy = 200.0)
        val fling = assertNotNull(m.onGestureEnded(end, WheelGestureKind.CONTINUOUS))
        assertEquals(WheelMomentum.MAX_VELOCITY, fling.velocityY, 1e-6)
    }

    @Test
    fun slowWheelNotchesMoveOneToOne() {
        val m = WheelMomentum()
        assertEquals(10.0, m.onMove(1000, 0.0, 10.0, WheelGestureKind.DISCRETE).second)
        assertEquals(10.0, m.onMove(1200, 0.0, 10.0, WheelGestureKind.DISCRETE).second)
    }

    @Test
    fun fastWheelSpinsAccelerate() {
        val m = WheelMomentum()
        var t = 1000L
        var last = 0.0
        repeat(10) {
            t += 12
            last = m.onMove(t, 0.0, -10.0, WheelGestureKind.DISCRETE).second
        }
        assertTrue(last < -20.0, "fast spin moved only $last per notch")
        assertTrue(last >= -10.0 * WheelMomentum.MAX_WHEEL_GAIN, "gain over the cap: $last")
        // Slowing down again drops back to one-to-one.
        assertEquals(-10.0, m.onMove(t + 300, 0.0, -10.0, WheelGestureKind.DISCRETE).second)
    }

    @Test
    fun trackpadMovementIsNotScaled() {
        // macOS already accelerates continuous (trackpad) deltas; scaling them again would double it.
        val m = WheelMomentum()
        var t = 1000L
        repeat(10) {
            t += 5
            assertEquals(30.0, m.onMove(t, 0.0, 30.0, WheelGestureKind.CONTINUOUS).second)
        }
    }
}
