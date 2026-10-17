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

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Frame-driven animation primitives for the self-drawn passage finder.
 *
 * These stand in for the `animateFloatAsState` / `animateDpAsState` values the Compose
 * implementation used, reproducing the same motion so the widget keeps its established
 * feel. Each holds a current value, is advanced once per frame with the real elapsed
 * time, and reports whether it still needs further frames — which is what drives the
 * view's invalidation loop.
 *
 * All of them honour the "no animations" accessibility setting by snapping: set
 * [Spring1D.snapping] / [Tween1D.snapping] and the value jumps to its target.
 */

/**
 * Critically damped spring, matching Compose's
 * `spring(dampingRatio = DampingRatioNoBouncy, stiffness = …)`.
 *
 * Compose models a unit mass, so the undamped angular frequency is `sqrt(stiffness)`
 * and critical damping gives the closed form
 * `x(t) = (x0 + (v0 + ω·x0)·t)·e^(−ω·t)` on the displacement from the target. Using the
 * closed form rather than numeric integration keeps the motion identical regardless of
 * frame pacing, so a dropped frame cannot change where the animation ends up.
 */
class Spring1D(
    initialValue: Float = 0f,
    /** Compose `Spring.StiffnessX` value; 300 matches the strip show/hide springs. */
    var stiffness: Float = 300f,
) {
    var value: Float = initialValue
        private set

    var target: Float = initialValue

    var snapping: Boolean = false

    private var velocity: Float = 0f

    /** True while the value has not yet settled on [target]. */
    val isAnimating: Boolean
        get() = !snapping && (abs(value - target) > VALUE_EPSILON || abs(velocity) > VELOCITY_EPSILON)

    /** Jumps immediately to [newTarget] with no motion. */
    fun snapTo(newTarget: Float) {
        target = newTarget
        value = newTarget
        velocity = 0f
    }

    /** Advances the spring by [dtSeconds]. Returns true if more frames are needed. */
    fun advance(dtSeconds: Float): Boolean {
        if (snapping) {
            value = target
            velocity = 0f
            return false
        }
        if (!isAnimating) {
            value = target
            velocity = 0f
            return false
        }
        val omega = sqrt(stiffness)
        val x0 = value - target
        val v0 = velocity
        val decay = exp(-omega * dtSeconds)
        // Critically damped closed form and its derivative.
        val c = v0 + omega * x0
        val x = (x0 + c * dtSeconds) * decay
        velocity = (c - omega * (x0 + c * dtSeconds)) * decay
        value = target + x
        if (abs(x) <= VALUE_EPSILON && abs(velocity) <= VELOCITY_EPSILON) {
            value = target
            velocity = 0f
            return false
        }
        return true
    }

    private companion object {
        /** Sub-pixel, so settling is invisible. */
        const val VALUE_EPSILON = 0.05f
        const val VELOCITY_EPSILON = 0.5f
    }
}

/**
 * Fixed-duration interpolated value, standing in for Compose's `tween`.
 *
 * Compose's default tween easing is FastOutSlowIn, a cubic Bézier with control points
 * (0.4, 0) and (0.2, 1); [fastOutSlowIn] evaluates that curve directly rather than
 * pulling in `PathInterpolator`, so the class stays free of framework dependencies and
 * unit-testable.
 */
class Tween1D(
    initialValue: Float = 0f,
    /** Animation length in milliseconds. */
    var durationMs: Int = 300,
) {
    var value: Float = initialValue
        private set

    var snapping: Boolean = false

    private var startValue: Float = initialValue
    private var endValue: Float = initialValue
    private var elapsedMs: Float = 0f
    private var running: Boolean = false

    val target: Float get() = endValue

    val isAnimating: Boolean get() = running && !snapping

    /** Retargets the tween, easing from wherever the value currently sits. */
    fun animateTo(newTarget: Float) {
        if (newTarget == endValue && (running || value == newTarget)) return
        if (snapping || durationMs <= 0) {
            snapTo(newTarget)
            return
        }
        startValue = value
        endValue = newTarget
        elapsedMs = 0f
        running = true
    }

    /** Jumps immediately to [newTarget] with no motion. */
    fun snapTo(newTarget: Float) {
        startValue = newTarget
        endValue = newTarget
        value = newTarget
        elapsedMs = 0f
        running = false
    }

    /** Advances the tween by [dtSeconds]. Returns true if more frames are needed. */
    fun advance(dtSeconds: Float): Boolean {
        if (snapping) {
            value = endValue
            running = false
            return false
        }
        if (!running) return false
        elapsedMs += dtSeconds * 1000f
        val t = (elapsedMs / durationMs).coerceIn(0f, 1f)
        value = startValue + (endValue - startValue) * fastOutSlowIn(t)
        if (t >= 1f) {
            value = endValue
            running = false
            return false
        }
        return true
    }
}

/**
 * Material's FastOutSlowIn easing: the cubic Bézier through (0,0), (0.4,0), (0.2,1), (1,1).
 *
 * The curve is parametric, so the x component is inverted by bisection — cheap at this
 * precision and called only a handful of times per frame.
 */
fun fastOutSlowIn(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f) return t
    var lo = 0f
    var hi = 1f
    var mid = t
    repeat(BISECTION_STEPS) {
        mid = (lo + hi) / 2f
        val x = bezier(mid, X1, X2)
        if (x < t) lo = mid else hi = mid
    }
    return bezier(mid, Y1, Y2)
}

/** Cubic Bézier with implicit endpoints 0 and 1. */
private fun bezier(t: Float, p1: Float, p2: Float): Float {
    val inv = 1f - t
    return 3f * inv * inv * t * p1 + 3f * inv * t * t * p2 + t * t * t
}

private const val X1 = 0.4f
private const val Y1 = 0f
private const val X2 = 0.2f
private const val Y2 = 1f

/** Eight halvings give ~0.4% accuracy on the parameter — well below one frame of motion. */
private const val BISECTION_STEPS = 8

/** Linear interpolation from [a] to [b]. */
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Gaussian bell falloff, 1 at the centre and tapering to nothing at the lens edge.
 *
 * [normalisedDistance] is distance from the lens centre as a fraction of the lens radius.
 * [falloff] controls how tightly the bell is drawn in: larger values concentrate the
 * magnification into fewer items either side of the centre, which leaves more items at
 * their base size and so fits more of them on screen.
 *
 * This replaced a plain squared linear ramp on the book strip. The ramp reached its peak
 * with a corner and shed width steadily across the whole lens, which read as a triangle
 * rather than a bell; a Gaussian is flat at the top and steep on the flanks, so the
 * centre few spines look deliberately picked out and the rest stay compact.
 */
fun bellFalloff(normalisedDistance: Float, falloff: Float): Float {
    val d = abs(normalisedDistance)
    // Beyond the lens the value is already vanishingly small; clamping keeps distant
    // items exactly at their base size instead of a hair above it.
    if (d >= 1f) return 0f
    return exp(-falloff * d * d)
}
