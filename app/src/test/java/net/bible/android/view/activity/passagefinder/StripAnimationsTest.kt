/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the frame-driven animation primitives that replaced Compose's
 * `animateFloatAsState` and `animateDpAsState`.
 *
 * The frame-rate independence tests are the important ones: these animators are stepped
 * from `onDraw` with whatever time actually elapsed, so on a device that drops frames —
 * an e-ink reader, say — a naive integrator would land somewhere different from a device
 * that does not.
 */
class StripAnimationsTest {

    /** Runs [animator] to rest at 60fps, returning the number of frames it took. */
    private fun runToRest(advance: (Float) -> Boolean, maxFrames: Int = 600): Int {
        var frames = 0
        while (advance(1f / 60f) && frames < maxFrames) frames++
        return frames
    }

    // ---- Spring1D ------------------------------------------------------------------

    @Test
    fun `spring settles exactly on its target`() {
        val spring = Spring1D(0f)
        spring.target = 240f
        val frames = runToRest(spring::advance)
        assertTrue("spring should settle in well under a second", frames < 60)
        assertEquals(240f, spring.value)
        assertFalse(spring.isAnimating)
    }

    @Test
    fun `critically damped spring never overshoots`() {
        // The strips hide and reveal with this spring. Overshoot would show as a strip
        // springing past its full height and bouncing back, which the Compose
        // configuration (DampingRatioNoBouncy) explicitly avoided.
        val spring = Spring1D(0f)
        spring.target = 100f
        repeat(300) {
            spring.advance(1f / 60f)
            assertTrue("value ${spring.value} overshot the target", spring.value <= 100.001f)
        }
    }

    @Test
    fun `spring reaches the same place regardless of frame pacing`() {
        val smooth = Spring1D(0f).apply { target = 200f }
        val janky = Spring1D(0f).apply { target = 200f }
        // Half a second of animation, delivered as 30 even frames or as 10 slow ones.
        repeat(30) { smooth.advance(1f / 60f) }
        repeat(10) { janky.advance(1f / 20f) }
        assertEquals(
            "frame pacing must not change where the spring is after the same elapsed time",
            smooth.value.toDouble(), janky.value.toDouble(), 1.0,
        )
    }

    @Test
    fun `spring retargeted mid-flight carries its velocity`() {
        val spring = Spring1D(0f)
        spring.target = 100f
        repeat(5) { spring.advance(1f / 60f) }
        val midValue = spring.value
        assertTrue("spring should have moved", midValue > 0f)
        spring.target = 0f
        runToRest(spring::advance)
        assertEquals(0f, spring.value)
    }

    @Test
    fun `snapping spring jumps without animating`() {
        val spring = Spring1D(0f)
        spring.snapping = true
        spring.target = 80f
        assertFalse(spring.isAnimating)
        assertFalse("a snapping spring needs no further frames", spring.advance(1f / 60f))
        assertEquals(80f, spring.value)
    }

    @Test
    fun `snapTo moves immediately and leaves nothing in flight`() {
        val spring = Spring1D(0f)
        spring.target = 100f
        repeat(3) { spring.advance(1f / 60f) }
        spring.snapTo(42f)
        assertEquals(42f, spring.value)
        assertFalse(spring.isAnimating)
    }

    // ---- Tween1D -------------------------------------------------------------------

    @Test
    fun `tween reaches its target after the stated duration`() {
        val tween = Tween1D(0f, durationMs = 300)
        tween.animateTo(1f)
        // 18 frames at 60fps is 300ms.
        repeat(18) { tween.advance(1f / 60f) }
        assertEquals(1f, tween.value)
        assertFalse(tween.isAnimating)
    }

    @Test
    fun `tween is monotonic and stays within its endpoints`() {
        val tween = Tween1D(0f, durationMs = 300)
        tween.animateTo(1f)
        var previous = 0f
        repeat(18) {
            tween.advance(1f / 60f)
            assertTrue("tween went backwards", tween.value >= previous - 1e-5f)
            assertTrue("tween left its range", tween.value in 0f..1f)
            previous = tween.value
        }
    }

    @Test
    fun `tween is frame-rate independent`() {
        val smooth = Tween1D(0f, durationMs = 300)
        val janky = Tween1D(0f, durationMs = 300)
        smooth.animateTo(1f)
        janky.animateTo(1f)
        repeat(9) { smooth.advance(1f / 60f) }   // 150ms
        repeat(3) { janky.advance(1f / 20f) }    // 150ms
        assertEquals(smooth.value.toDouble(), janky.value.toDouble(), 1e-4)
    }

    @Test
    fun `retargeting a tween eases from where it currently is`() {
        val tween = Tween1D(0f, durationMs = 300)
        tween.animateTo(1f)
        repeat(6) { tween.advance(1f / 60f) }
        val mid = tween.value
        assertTrue(mid > 0f && mid < 1f)
        tween.animateTo(0f)
        // The reversal must start from the current value, not snap back to the origin.
        tween.advance(1f / 60f)
        assertTrue("reversal should start near the current value", tween.value < mid)
        runToRest(tween::advance)
        assertEquals(0f, tween.value)
    }

    @Test
    fun `snapping tween jumps straight to the target`() {
        val tween = Tween1D(0f, durationMs = 300)
        tween.snapping = true
        tween.animateTo(1f)
        assertEquals(1f, tween.value)
        assertFalse(tween.isAnimating)
    }

    @Test
    fun `a zero duration tween completes instantly`() {
        val tween = Tween1D(0f, durationMs = 0)
        tween.animateTo(1f)
        assertEquals(1f, tween.value)
        assertFalse(tween.isAnimating)
    }

    // ---- Easing --------------------------------------------------------------------

    @Test
    fun `fastOutSlowIn is pinned at both ends and monotonic between them`() {
        assertEquals(0f, fastOutSlowIn(0f))
        assertEquals(1f, fastOutSlowIn(1f))
        var previous = 0f
        for (step in 0..100) {
            val value = fastOutSlowIn(step / 100f)
            assertTrue("easing went backwards at $step", value >= previous - 1e-4f)
            assertTrue(value in 0f..1f)
            previous = value
        }
    }

    @Test
    fun `fastOutSlowIn has the Material curve's shape`() {
        // Both control points sit on the axis ends (y = 0 then y = 1), so the curve eases
        // gently out of the start, races through the middle, and creeps into the target.
        // Reference values for cubic-bezier(0.4, 0, 0.2, 1).
        assertTrue("should ease in at the very start", fastOutSlowIn(0.05f) < 0.05f)
        assertEquals("midpoint of the Material curve", 0.78, fastOutSlowIn(0.5f).toDouble(), 0.03)
        assertEquals("three quarters through", 0.96, fastOutSlowIn(0.75f).toDouble(), 0.03)
        assertTrue(
            "most of the travel must happen before the end",
            fastOutSlowIn(0.75f) > 0.9f,
        )
    }

    @Test
    fun `fastOutSlowIn clamps out-of-range input`() {
        assertEquals(0f, fastOutSlowIn(-1f))
        assertEquals(1f, fastOutSlowIn(2f))
    }

    @Test
    fun `bellFalloff peaks at the centre and vanishes at the lens edge`() {
        assertEquals(1f, bellFalloff(0f, 10f))
        assertEquals("nothing may stick out past the lens", 0f, bellFalloff(1f, 10f))
        assertEquals(0f, bellFalloff(1.5f, 10f))
        // Symmetric, so a spine to the left of centre matches its mirror to the right.
        assertEquals(bellFalloff(0.3f, 10f), bellFalloff(-0.3f, 10f))
    }

    @Test
    fun `bellFalloff is flat on top and steep on the flanks`() {
        // This is what distinguishes a bell from the squared linear ramp it replaced: the
        // ramp sheds width immediately and evenly, so the strip read as a triangle.
        val nearCentre = bellFalloff(0f, 10f) - bellFalloff(0.1f, 10f)
        val midFlank = bellFalloff(0.3f, 10f) - bellFalloff(0.4f, 10f)
        assertTrue(
            "the bell should lose far less over its first tenth than across its flank",
            nearCentre < midFlank,
        )
    }

    @Test
    fun `a tighter falloff concentrates magnification near the centre`() {
        // Raising the falloff is the knob for fitting more books on the strip.
        assertTrue(bellFalloff(0.4f, 16f) < bellFalloff(0.4f, 10f))
        assertTrue(bellFalloff(0.4f, 10f) < bellFalloff(0.4f, 4f))
        // The peak itself is unaffected, so the centred spine keeps its full size.
        assertEquals(1f, bellFalloff(0f, 16f))
    }

    @Test
    fun `bellFalloff decreases monotonically`() {
        var previous = Float.MAX_VALUE
        for (step in 0..100) {
            val value = bellFalloff(step / 100f, 10f)
            assertTrue("bell rose again at $step", value <= previous + 1e-6f)
            previous = value
        }
    }

    @Test
    fun `lerp interpolates and extrapolates linearly`() {
        assertEquals(0f, lerp(0f, 10f, 0f))
        assertEquals(10f, lerp(0f, 10f, 1f))
        assertEquals(5f, lerp(0f, 10f, 0.5f))
        assertTrue(abs(lerp(4f, 8f, 0.25f) - 5f) < 1e-5f)
    }
}
