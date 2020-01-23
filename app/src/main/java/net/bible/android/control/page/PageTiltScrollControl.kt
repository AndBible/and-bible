/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.control.page

import android.annotation.TargetApi
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.CommonUtils.sharedPreferences
import java.util.*

/** Manage the logic behind tilt-to-scroll
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
//Tilt-scroll is disabled on 2.1/ only enabled on 2.2+
@TargetApi(Build.VERSION_CODES.FROYO)
class PageTiltScrollControl {
    var isTiltScrollEnabled = false
        private set
    // the pitch at which a user views the text stationary
	// this changes dynamically when the screen is touched
	// both angles are degrees
    private var mNoScrollViewingPitch = -38
    private var mNoScrollViewingPitchCalculated = false
    private var mSensorsTriggered = false
    private var mLastNormalisedPitchValue = 0
    // current pitch of phone - varies dynamically
    private var mOrientationValues: FloatArray? = null
    private var mRotation = Surface.ROTATION_0
    // needed to find if screen switches to landscape and must different sensor value
    private var mDisplay: Display? = null

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
    private val tiltScrollInfoSingleton = TiltScrollInfo()// TURNED OFF UPSCROLL// speedup could be done by increasing scroll amount but that leads to a jumpy screen

    // speedUp if tilt screen beyond a certain amount
    val tiltScrollInfo: TiltScrollInfo
        get() {
            val tiltScrollInfo = tiltScrollInfoSingleton.reset()
            var delayToNextScroll = BASE_TIME_BETWEEN_SCROLLS
            if (mOrientationValues != null) {
                val normalisedPitch = getNormalisedPitch(mRotation, mOrientationValues!!)
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
            }
            if (isTiltScrollEnabled) {
                tiltScrollInfo.delayToNextScroll = Math.max(MIN_TIME_BETWEEN_SCROLLS, delayToNextScroll)
            }
            return tiltScrollInfo
        }

    /** start or stop tilt to scroll functionality
     */
    fun enableTiltScroll(enable: Boolean): Boolean {
        val enabled = mainBibleActivity.windowRepository.windowBehaviorSettings.enableTiltToScroll
        return if (!enabled || !isTiltSensingPossible) {
            false
        } else if (isTiltScrollEnabled != enable) {
            isTiltScrollEnabled = enable
            if (enable) {
                connectListeners()
            } else {
                disconnectListeners()
            }
            true
        } else {
            false
        }
    }

    /** called when user touches screen to reset home position
     */
    fun recalculateViewingPosition() {
        mNoScrollViewingPitchCalculated = false
        mSensorsTriggered = false
    }

    /** if screen rotates must switch between different values returned by orientation sensor
     */
    private fun getNormalisedPitch(rotation: Int, orientationValues: FloatArray): Int {
        var pitch = 0f
        // occasionally the viewing position was being unexpectedly reset to zero - avoid by checking for the problematic state
        if (rotation == 0 && orientationValues[1] == 0.0f && orientationValues[2] == 0.0f) {
            return if (!mNoScrollViewingPitchCalculated) {
                INVALID_STATE
            } else {
                mLastNormalisedPitchValue
            }
        }
        when (rotation) {
            Surface.ROTATION_0 -> pitch = orientationValues[1]
            Surface.ROTATION_90 -> pitch = -orientationValues[2]
            Surface.ROTATION_270 -> pitch = orientationValues[2]
            Surface.ROTATION_180 -> pitch = -orientationValues[1]
            else -> Log.e(TAG, "Invalid Scroll rotation:$rotation")
        }
        val normalisedPitch = Math.round(pitch)
        mLastNormalisedPitchValue = normalisedPitch
        return normalisedPitch
    }

    /** find angle between no-scroll-angle and current pitch
     */
    private fun getDevianceFromStaticViewingAngle(normalisedPitch: Int): Int {
        if (!mNoScrollViewingPitchCalculated) {
			//			Log.d(TAG, "Recalculating home/noscroll pitch "+normalisedPitch);
			// assume user's viewing pitch is the current one
            mNoScrollViewingPitch = normalisedPitch
            // pitch can be 0 before the sensors have fired
            if (mSensorsTriggered) {
                mNoScrollViewingPitchCalculated = true
            }
        }
        return Math.abs(normalisedPitch - mNoScrollViewingPitch)
    }

    /** Get delay between scrolls for specified tilt
     *
     * negative tilts will return min delay
     * 0-num elts in mTimeBetweenScrolls array will return associated period from array
     * larger tilts will return max period from array
     *
     * @param tilt
     * @return
     */
    private fun getDelayToNextScroll(tilt: Int): Int {
		// speed changes with every degree of tilt
		// ensure we have a positive number
		var tilt = tilt
        tilt = Math.max(tilt, 0)
        return if (tilt < mTimeBetweenScrolls.size) {
            mTimeBetweenScrolls[tilt]
        } else {
            mTimeBetweenScrolls[mTimeBetweenScrolls.size - 1]
        }
    }

    /**
     * Orientation monitor (see Professional Android 2 App Dev Meier pg 469)
     */
    private fun connectListeners() {
        mDisplay = (application.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val oSensor = sm.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        sm.registerListener(myOrientationListener, oSensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun disconnectListeners() {
        try {
            val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(myOrientationListener)
        } catch (e: IllegalArgumentException) {
			// Prevent occasional: IllegalArgumentException: Receiver not registered: android.hardware.SystemSensorManager
			// If not registered then there is no need to unregister
            Log.w(TAG, "Error disconnecting sensor listener", e)
        }
    }

    val myOrientationListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            if (sensorEvent.sensor.type == Sensor.TYPE_ORIENTATION) {
                mOrientationValues = sensorEvent.values
                mRotation = mDisplay!!.rotation
                mSensorsTriggered = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /** map degrees tilt to time between 1 pixel scrolls to save time at runtime
     */
    private fun initialiseTiltSpeedPeriods() {
        val degreeRange = MAX_DEGREES_OFFSET - MIN_DEGREES_OFFSET.toFloat()
        val speedRange = MAX_SPEED - MIN_SPEED
        val delayPeriods: MutableList<Int> = ArrayList()
        for (deg in MIN_DEGREES_OFFSET..MAX_DEGREES_OFFSET) {
            val speed = MIN_SPEED + deg / degreeRange * speedRange
            val period = 1 / speed
            delayPeriods.add(Math.round(period))
        }
        mTimeBetweenScrolls = delayPeriods.toTypedArray()
    }

    companion object {
        // must be null initially
        private var mIsOrientationSensor: Boolean? = null
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
         * Therefore the following mTimeBetweenScrollListEvery5Degrees is used to even out speed changes
         *
         * This was my starting spreadsheet from which the below array was derived.
         * The spreadsheet starts with regular changes in speed and calculates the required Periodic time
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
        // calculated to ensure even speed up of scrolling
		private lateinit var mTimeBetweenScrolls: Array<Int>
        private const val TAG = "TiltScrollControl"
        /** return true if both a sensor and android support are available to sense device tilt
         */
        val isTiltSensingPossible: Boolean
            get() = isOrientationSensor

        /**
         * Returns true if at least one Orientation sensor is available
         */
        val isOrientationSensor: Boolean
            get() {
                if (mIsOrientationSensor == null) {
                    val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    mIsOrientationSensor = run {
						val sensors = sm.getSensorList(Sensor.TYPE_ORIENTATION)
						java.lang.Boolean.valueOf(sensors.size > 0)
					}
                }
                return mIsOrientationSensor!!
            }
    }

    init {
        initialiseTiltSpeedPeriods()
    }
}
