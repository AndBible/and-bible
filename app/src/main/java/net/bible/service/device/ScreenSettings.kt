/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
import android.os.PowerManager
import android.util.Log

/** Manage screen related functions
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object ScreenSettings {

    val NIGHT_MODE_PREF_NO_SENSOR = "night_mode_pref"
    val NIGHT_MODE_PREF_WITH_SENSOR = "night_mode_pref2"

    private val NIGHT_MODE = "true"
    private val NOT_NIGHT_MODE = "false"
    private val AUTO_NIGHT_MODE = "automatic"

    private val mLightSensor = LightSensor()

    private val MAX_DARK_READING = 30

    var isNightMode = false
        private set

    private val TAG = "ScreenSettings"

    // may possible be no reading yet but need to have a screen colour
    // If no light change has occurred then it is most likely pitch black so allow default of black,
    // which will happen automatically because NO_READING_YET is negative
    val isNightModeChanged: Boolean
        get() {
            val origNightMode = isNightMode
			isNightMode = nightMode
            return origNightMode != isNightMode
        }

    val isScreenOn: Boolean
        get() {
            val pm = BibleApplication.application.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isScreenOn
        }

	private val autoNightMode
		get() = nightModePreferenceValue == AUTO_NIGHT_MODE

	private val nightMode get() =
		if(autoNightMode) mLightSensor.reading <= MAX_DARK_READING
		else nightModePreferenceValue == NIGHT_MODE

    /** get the preference setting - could be using either of 2 preference settings depending on presence of a light sensor
     */
	// boolean pref setting if no light meter
    private val nightModePreferenceValue: String
        get() {
            var nightModePref: String = NOT_NIGHT_MODE

            val preferences = CommonUtils.getSharedPreferences()
            if (preferences != null) {
                val preferenceKey = usedNightModePreferenceKey
                if (NIGHT_MODE_PREF_WITH_SENSOR == preferenceKey) {
                    nightModePref = preferences.getString(preferenceKey, NOT_NIGHT_MODE) as String
                } else {
                    val isNightMode = preferences.getBoolean(preferenceKey, false)
                    nightModePref = if (isNightMode) NIGHT_MODE else NOT_NIGHT_MODE
                }
            }
            return nightModePref
        }

    /** get the preference key being used/unused, dependent on light sensor availability
     */
    val usedNightModePreferenceKey: String
        get() =
			if (mLightSensor.isLightSensor) ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR
			else ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR

    val unusedNightModePreferenceKey: String
        get() =
			if (mLightSensor.isLightSensor) ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR
			else ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR
}
