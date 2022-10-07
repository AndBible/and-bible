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

package net.bible.service.device

import net.bible.android.BibleApplication
import net.bible.service.common.CommonUtils

import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import net.bible.android.control.event.ABEventBus

/** Manage screen related functions
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object ScreenSettings {
    private val lightSensor: LightSensor = LightSensor { reading ->
        if(autoNightMode) {
            val oldValue = lastNightMode
            if(reading <= MAX_DARK_READING) {
                lastNightMode = true
            } else if(reading > MIN_LIGHT_READING) {
                lastNightMode = false
            }
            if(oldValue != lastNightMode) {
                ABEventBus.post(NightModeChanged())
            }
        }
    }

    class NightModeChanged()

    private const val MAX_DARK_READING = 5
    private const val DARK_READING_THRESHOLD = 15
    private const val MIN_LIGHT_READING = 50

	val preferences get() = CommonUtils.realSharedPreferences

    val isScreenOn: Boolean
        get() {
            val pm = BibleApplication.application.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isScreenOn
        }

    const val systemModeAvailable = true

    private val autoNightMode	get() =
        autoModeAvailable && preferences.getString("night_mode_pref3", "manual") == "automatic"
    val manualMode: Boolean get() =
        preferences.getString("night_mode_pref3", "manual") == "manual"
    val systemMode: Boolean get() =
        systemModeAvailable && preferences.getString("night_mode_pref3", "manual") == "system"

    val autoModeAvailable = lightSensor.isLightSensor

    fun checkMonitoring() {
        if(autoNightMode) {
            lightSensor.reading
        }
    }

    fun refreshNightMode(): Boolean {
        lastNightMode = if(autoNightMode) {
            lightSensor.reading < DARK_READING_THRESHOLD
        } else
            nightMode
        return lastNightMode
    }

    private var lastNightMode: Boolean = refreshNightMode()

	val nightMode: Boolean get() =
        when {
            autoNightMode -> lastNightMode
            systemMode -> when(config.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> false
            }
            else // manual mode
            -> preferences.getBoolean("night_mode_pref", false)
        }

    fun setLastNightMode(value: Boolean) {
        if(autoNightMode)
            lastNightMode = value
    }

    private val config: Configuration get() = BibleApplication.application.resources.configuration
}
