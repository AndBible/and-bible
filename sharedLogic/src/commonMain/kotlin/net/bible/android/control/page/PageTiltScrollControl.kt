/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.page

import net.bible.sharedcore.log.Log
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The platform's device-tilt source. [start] begins delivering the device pitch in degrees (already
 * corrected for the screen rotation, so that tilting the top of the device away from the reader is
 * the same sign on every platform) to [onSample]; [stop] ends delivery. Both must be idempotent.
 */
interface TiltSensor {
    /** False when the device has no suitable motion sensor; tilt scroll is then never started. */
    val isAvailable: Boolean get() = true

    fun start(onSample: (pitch: Float) -> Unit)

    fun stop()
}

/** A sensor that never exists: the default for contexts that do not use tilt scroll. */
object NoTiltSensor : TiltSensor {
    override val isAvailable: Boolean get() = false
    override fun start(onSample: (pitch: Float) -> Unit) {}
    override fun stop() {}
}

/**
 * Manage the logic behind tilt-to-scroll. The platform supplies pitch samples through a [TiltSensor];
 * everything else (home pitch, tolerance, speed table) is platform neutral.
 *
 * @param sensor where the pitch comes from.
 * @param isEnabledInSettings the workspace setting `enableTiltToScroll`.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class PageTiltScrollControl(
    private val sensor: TiltSensor = NoTiltSensor,
    private val isEnabledInSettings: () -> Boolean = { true },
) {
    var isTiltScrollEnabled = false
        private set

    // the pitch at which a user views the text stationary
    // this changes dynamically when the screen is touched
    // both angles are degrees
    private var mNoScrollViewingPitch = -38
    private var mNoScrollViewingPitchCalculated = false
    @Volatile private var mSensorsTriggered = false

    // last pitch delivered by the sensor, in whole degrees; null before the first sample
    @Volatile private var mLastNormalisedPitchValue: Int? = null

    class TiltScrollInfo {
        var scrollPixels = 0
        var forward = false
        var delayToNextScroll = 0
        fun reset(): TiltScrollInfo {
            scrollPixels = 0
            forward = true
            delayToNextScroll = TIME_TO_POLL_WHEN_NOT_SCROLLING
            return this
        }

        companion object {
            var TIME_TO_POLL_WHEN_NOT_SCROLLING = 500
        }
    }

    // should not need more than one because the request come in one at a time
    private val tiltScrollInfoSingleton = TiltScrollInfo()

    // speedUp if tilt screen beyond a certain amount
    val tiltScrollInfo: TiltScrollInfo
        get() {
            val tiltScrollInfo = tiltScrollInfoSingleton.reset()
            var delayToNextScroll = BASE_TIME_BETWEEN_SCROLLS

            val normalisedPitch = mLastNormalisedPitchValue ?: INVALID_STATE
            if (normalisedPitch != INVALID_STATE) {
                val devianceFromViewingAngle = getDevianceFromStaticViewingAngle(normalisedPitch)
                if (devianceFromViewingAngle > NO_SCROLL_VIEWING_TOLERANCE) {
                    tiltScrollInfo.forward = normalisedPitch < mNoScrollViewingPitch
                    // speedUp if tilt screen beyond a certain amount
                    if (tiltScrollInfo.forward) {
                        delayToNextScroll = getDelayToNextScroll(devianceFromViewingAngle - NO_SCROLL_VIEWING_TOLERANCE - NO_SPEED_INCREASE_VIEWING_TOLERANCE - 1)
                        // speedup could be done by increasing scroll amount but that leads to a jumpy screen
                        tiltScrollInfo.scrollPixels = 1
                    } else { // TURNED OFF UPSCROLL
                        delayToNextScroll = BASE_TIME_BETWEEN_SCROLLS
                        tiltScrollInfo.scrollPixels = 0
                    }
                }
            }
            if (isTiltScrollEnabled) {
                tiltScrollInfo.delayToNextScroll = max(MIN_TIME_BETWEEN_SCROLLS, delayToNextScroll)
            }
            return tiltScrollInfo
        }

    /** True when the device has the tilt sensor; the workspace setting is checked separately (see the `enabled` check in [start]/[step]). */
    val isTiltSensingPossible: Boolean get() = sensor.isAvailable

    /** start or stop tilt to scroll functionality; returns true when the state changed
     */
    fun enableTiltScroll(enable: Boolean): Boolean {
        val enabled = isEnabledInSettings()
        return if (!enabled || !sensor.isAvailable) {
            false
        } else if (isTiltScrollEnabled != enable) {
            isTiltScrollEnabled = enable
            if (enable) {
                sensor.start(::onSample)
            } else {
                sensor.stop()
            }
            true
        } else {
            false
        }
    }

    /** The workspace setting `enableTiltToScroll`. */
    val isAllowedBySettings: Boolean get() = isEnabledInSettings()

    /** Stop sensing unconditionally (also when the setting has been switched off meanwhile). */
    fun pause() {
        if (isTiltScrollEnabled) {
            isTiltScrollEnabled = false
            sensor.stop()
        }
    }

    private fun onSample(pitch: Float) {
        if (!isTiltScrollEnabled) return
        // A degenerate rotation vector yields NaN/infinite angles; roundToInt would throw on the sensor thread.
        if (!pitch.isFinite()) return
        mLastNormalisedPitchValue = pitch.roundToInt()
        mSensorsTriggered = true
    }

    /** called when user touches screen to reset home position
     */
    fun recalculateViewingPosition() {
        mNoScrollViewingPitchCalculated = false
        mSensorsTriggered = false
    }

    /** find angle between no-scroll-angle and current pitch
     */
    private fun getDevianceFromStaticViewingAngle(normalisedPitch: Int): Int {
        if (!mNoScrollViewingPitchCalculated) {
            // assume user's viewing pitch is the current one
            mNoScrollViewingPitch = normalisedPitch
            // pitch can be 0 before the sensors have fired
            if (mSensorsTriggered) {
                mNoScrollViewingPitchCalculated = true
            }
        }
        return abs(normalisedPitch - mNoScrollViewingPitch)
    }

    /** Get delay between scrolls for specified tilt
     *
     * negative tilts will return min delay
     * 0-num elts in timeBetweenScrolls array will return associated period from array
     * larger tilts will return max period from array
     */
    private fun getDelayToNextScroll(tilt: Int): Int {
        // speed changes with every degree of tilt; ensure we have a positive number
        val t = max(tilt, 0)
        return if (t < timeBetweenScrolls.size) timeBetweenScrolls[t] else timeBetweenScrolls[timeBetweenScrolls.size - 1]
    }

    companion object {
        private const val NO_SCROLL_VIEWING_TOLERANCE = 2 //3;
        private const val NO_SPEED_INCREASE_VIEWING_TOLERANCE = 2
        private const val INVALID_STATE = -9999
        // this is decreased (subtracted from) to speed up scrolling
        private const val BASE_TIME_BETWEEN_SCROLLS = 48 //70(jerky) 40((fast);
        private const val MIN_TIME_BETWEEN_SCROLLS = 4
        /**
         * Time between scroll = Periodic time = 1/frequency
         * Scroll speed = frequency*wavelength // wavelength = 1 pixel so can ignore wavelength
         * => speed = 1/Time between each scroll event
         * If we use regular changes in periodic time then initial changes in tilt have little affect on speed
         * but when tilt is greater small changes in tilt cause large changes in speed
         * Therefore the table below is used to even out speed changes.
         *
         * degrees	speed	Periodic time (ms)
         * 0		0.02	50
         * 5		0.04	25
         * 10		0.06	16.6666666666667
         * 15		0.08	12.5
         * 20		0.1		10
         * 25		0.12	8.33333333333333
         * 30		0.14	7.14285714285714
         * 35		0.16	6.25
         * 40		0.18	5.55555555555556
         * 45		0.2	5
         */
        private const val MIN_DEGREES_OFFSET = 0
        private const val MAX_DEGREES_OFFSET = 45
        private const val MIN_SPEED = 0.02f
        private const val MAX_SPEED = 0.2f

        /** map degrees tilt to time between 1 pixel scrolls to save time at runtime */
        private val timeBetweenScrolls: Array<Int> by lazy {
            val degreeRange = MAX_DEGREES_OFFSET - MIN_DEGREES_OFFSET.toFloat()
            val speedRange = MAX_SPEED - MIN_SPEED
            (MIN_DEGREES_OFFSET..MAX_DEGREES_OFFSET).map { deg ->
                val speed = MIN_SPEED + deg / degreeRange * speedRange
                (1 / speed).roundToInt()
            }.toTypedArray()
        }
    }
}

/**
 * Drives one [PageTiltScrollControl] from a platform loop: each [step] syncs the sensor with
 * "app active and setting on", scrolls the page by the control's current tilt, and returns the
 * milliseconds to wait before the next step. [setAppActive] is the lifecycle hook: with the app
 * inactive or backgrounded the sensor is stopped (no battery drain) and restarted, with a fresh
 * neutral pose, when it becomes active again.
 *
 * @param scroll scrolls the page; returns false when it could not move (at the end of the text).
 */
class TiltScrollDriver(
    private val control: PageTiltScrollControl,
    private val scroll: (forward: Boolean, pixels: Int) -> Boolean,
) {
    private var appActive = true
    private var isScrolling = false

    fun setAppActive(active: Boolean) {
        appActive = active
        sync()
    }

    /** Called when the user touches the page: the current pose becomes the neutral one. */
    fun recalculateViewingPosition() = control.recalculateViewingPosition()

    fun step(): Long {
        sync()
        if (!control.isTiltScrollEnabled) return PageTiltScrollControl.TiltScrollInfo.TIME_TO_POLL_WHEN_NOT_SCROLLING.toLong()
        val info = control.tiltScrollInfo
        if (info.scrollPixels != 0) {
            isScrolling = scroll(info.forward, info.scrollPixels)
        }
        return (if (isScrolling) info.delayToNextScroll else PageTiltScrollControl.TiltScrollInfo.TIME_TO_POLL_WHEN_NOT_SCROLLING).toLong()
    }

    private fun sync() {
        if (appActive && control.isAllowedBySettings) {
            if (control.enableTiltScroll(true)) control.recalculateViewingPosition()
        } else {
            control.pause()
        }
    }
}
