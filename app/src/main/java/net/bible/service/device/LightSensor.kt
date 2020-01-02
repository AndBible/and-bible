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
package net.bible.service.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import net.bible.android.BibleApplication.Companion.application

/** Light Sensor interface
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class LightSensor(val callBack: (newValue: Float) -> Unit) {
    private var mReading = NO_READING_YET
    private var mMonitoring = false
    /** return reading or null if no light sensor
     */
    val reading: Int
        get() {
            if (!mMonitoring) {
                ensureMonitoringLightLevel()
            }
            Log.d(TAG, "Light Sensor:$mReading")
            return Math.round(mReading)
        }

    @Synchronized
    private fun ensureMonitoringLightLevel() {
        if (!mMonitoring) {
            if (isLightSensor) {
                val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val oSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
                sm.registerListener(myLightListener, oSensor, SensorManager.SENSOR_DELAY_UI)
                // wait for first event
                try {
                    Thread.sleep(100)
                } catch (ie: InterruptedException) {
                    Log.e(TAG, "Interrupted getting light signal", ie)
                }
            }
            mMonitoring = true
        }
    }

    val myLightListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            if (sensorEvent.sensor.type == Sensor.TYPE_LIGHT) {
                mReading = sensorEvent.values[0]
				Log.d(TAG, "Reading: $mReading")
				callBack(mReading)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /**
     * Returns true if at least one Orientation sensor is available
     */
    val isLightSensor: Boolean
        get() {
            Log.d(TAG, "check for a light sensor")
            var isLightSensor = false
            val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
            if (sm != null) {
                val sensors = sm.getSensorList(Sensor.TYPE_LIGHT)
                isLightSensor = sensors.size > 0
            }
            Log.d(TAG, "Finished check for a light sensor")
            return isLightSensor
        }

    companion object {
        const val NO_READING_YET = -1919f
        private const val TAG = "LightSensor"
    }
}
