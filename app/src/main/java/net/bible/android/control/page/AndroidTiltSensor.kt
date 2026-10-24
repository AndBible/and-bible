/*
 * Copyright (c) 2020-2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import net.bible.android.BibleApplication.Companion.application
import net.bible.sharedcore.log.Log
import kotlin.math.PI

/**
 * [TiltSensor] over the game rotation vector sensor (Orientation monitor, see Professional Android 2
 * App Dev Meier pg 469). Delivers the pitch in degrees for the current display rotation.
 */
class AndroidTiltSensor : TiltSensor {
    private var display: Display? = null
    private var onSample: ((Float) -> Unit)? = null
    private val rotationValues = FloatArray(4)

    override val isAvailable: Boolean get() = isTiltSensingPossible

    override fun start(onSample: (pitch: Float) -> Unit) {
        this.onSample = onSample
        display = (application.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.registerListener(listener, sm.getDefaultSensor(SENSOR_TYPE), SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        onSample = null
        try {
            val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(listener)
        } catch (e: IllegalArgumentException) {
            // Prevent occasional: IllegalArgumentException: Receiver not registered: android.hardware.SystemSensorManager
            // If not registered then there is no need to unregister
            Log.w(TAG, "Error disconnecting sensor listener", e)
        }
    }

    private val listener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            if (sensorEvent.sensor.type != SENSOR_TYPE) return
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values)
            SensorManager.getOrientation(rotationMatrix, rotationValues)
            for (i in 0..3) {
                rotationValues[i] = rotationValues[i] / (2.0f * PI.toFloat()) * 360.0f
            }
            val rotation = display!!.rotation
            // occasionally the viewing position was being unexpectedly reset to zero - skip the problematic state
            if (rotation == Surface.ROTATION_0 && rotationValues[1] == 0.0f && rotationValues[2] == 0.0f) return
            // if screen rotates must switch between different values returned by orientation sensor
            val pitch = when (rotation) {
                Surface.ROTATION_0 -> rotationValues[1]
                Surface.ROTATION_90 -> -rotationValues[2]
                Surface.ROTATION_270 -> rotationValues[2]
                Surface.ROTATION_180 -> -rotationValues[1]
                else -> {
                    Log.e(TAG, "Invalid Scroll rotation:$rotation")
                    0f
                }
            }
            onSample?.invoke(pitch)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    companion object {
        const val SENSOR_TYPE = Sensor.TYPE_GAME_ROTATION_VECTOR
        private const val TAG = "TiltScrollControl"

        // must be null initially
        private var hasSensor: Boolean? = null

        /** True if the device has the rotation sensor, i.e. both a sensor and android support are available to sense device tilt. */
        val isTiltSensingPossible: Boolean
            get() {
                hasSensor?.let { return it }
                val sm = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                return (sm.getDefaultSensor(SENSOR_TYPE) != null).also { hasSensor = it }
            }
    }
}
