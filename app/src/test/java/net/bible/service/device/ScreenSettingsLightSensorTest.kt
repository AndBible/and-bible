/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/** Supplies a sensor before the process-wide ScreenSettings checks hardware availability. */
@Implements(SensorManager::class)
class SensorManagerWithLightSensor : ShadowSensorManager() {
    init {
        addSensor(ShadowSensor.newInstance(Sensor.TYPE_LIGHT))
    }
}

@RunWith(RobolectricTestRunner::class)
// A separate sandbox keeps the cached hardware availability and sensor listener out of other tests.
@Config(
    application = TestBibleApplication::class,
    sdk = [TEST_SDK],
    shadows = [SensorManagerWithLightSensor::class],
    instrumentedPackages = ["net.bible.service.device"],
)
class ScreenSettingsLightSensorTest {
    @Test
    fun enablingAutomaticModeStartsMonitoringWithoutRestart() {
        val prefs = CommonUtils.realSharedPreferences
        val manager = RuntimeEnvironment.getApplication().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorManager = shadowOf(manager)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)!!
        RuntimeEnvironment.setQualifiers("+notnight")
        prefs.edit().putString("night_mode_pref3", "manual").putBoolean("night_mode_pref", false).commit()
        var changes = 0
        val subscription = ScreenSettings.nightModeChanges.subscribe { changes++ }
        try {
            assertTrue(ScreenSettings.autoModeAvailable)
            assertFalse(ScreenSettings.nightMode)
            assertTrue("Manual mode must not start the sensor (#1975)", sensorManager.listeners.isEmpty())

            prefs.edit().putString("night_mode_pref3", "automatic").commit()
            // The recreated settings/reading host reads nightMode to apply its theme, in the same process.
            ScreenSettings.nightMode
            assertEquals("Enabling automatic mode must register the light listener", 1, sensorManager.listeners.size)
            val event = ShadowSensorManager.createSensorEvent(1).apply {
                this.sensor = sensor
                values[0] = 100f
            }
            sensorManager.sendSensorEventToListeners(event, sensor)
            assertFalse("A bright reading must keep day mode", ScreenSettings.nightMode)
            val changesBeforeDark = changes
            event.values[0] = 0f
            sensorManager.sendSensorEventToListeners(event, sensor)
            assertTrue("A dark reading must enable night mode without restarting", ScreenSettings.nightMode)
            assertEquals("The dark reading must notify the live UI", changesBeforeDark + 1, changes)

            event.values[0] = 100f
            sensorManager.sendSensorEventToListeners(event, sensor)
            assertFalse("A bright reading must restore day mode", ScreenSettings.nightMode)
            assertEquals("Repeated theme reads must not register duplicate listeners", 1, sensorManager.listeners.size)

            prefs.edit().putString("night_mode_pref3", "manual").commit()
            event.values[0] = 0f
            sensorManager.sendSensorEventToListeners(event, sensor)
            assertFalse("Manual mode must ignore sensor readings", ScreenSettings.nightMode)
        } finally {
            subscription.cancel()
            sensorManager.listeners.toList().forEach { manager.unregisterListener(it) }
            prefs.edit().remove("night_mode_pref").remove("night_mode_pref3").commit()
            RuntimeEnvironment.setQualifiers("+notnight")
        }
    }
}
