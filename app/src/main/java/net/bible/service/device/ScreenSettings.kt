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

import net.bible.android.BibleApplication
import net.bible.service.common.CommonUtils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.PowerManager
import net.bible.android.control.event.ABEventBus
import org.jetbrains.anko.configuration

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
                ABEventBus.getDefault().post(NightModeChanged())
            }
        }
    }

    class NightModeChanged()

    private const val MAX_DARK_READING = 5
    private const val DARK_READING_THRESHOLD = 15
    private const val MIN_LIGHT_READING = 50

	val preferences: SharedPreferences get() = CommonUtils.sharedPreferences

    val isScreenOn: Boolean
        get() {
            val pm = BibleApplication.application.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isScreenOn
        }

    val systemModeAvailable = false // Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val autoNightMode	get() =
        autoModeAvailable && preferences.getString("night_mode_pref2", "false") == "automatic"

    val autoModeAvailable = lightSensor.isLightSensor

    fun checkMonitoring() {
        lightSensor.reading
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
        if (autoNightMode)
            lastNightMode
        else if(systemModeAvailable)
            when(BibleApplication.application.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> false
            }
        else
            preferences.getString("night_mode_pref2", "false") == "true"

    fun setLastNightMode(value: Boolean) {
        if(autoNightMode)
            lastNightMode = value
    }
}
