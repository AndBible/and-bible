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
import android.content.SharedPreferences
import android.os.PowerManager

/** Manage screen related functions
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object ScreenSettings {
    private val mLightSensor = LightSensor()
    private val MAX_DARK_READING = 30

    var isNightMode = false
        private set

	val preferences: SharedPreferences = CommonUtils.getSharedPreferences()

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

	val autoNightMode	get() = preferences.getBoolean("auto_night_mode_pref", false)

	val autoModeAvailable: Boolean get() = mLightSensor.isLightSensor

	private val nightMode get() =
		if(autoNightMode) mLightSensor.reading <= MAX_DARK_READING
		else preferences.getBoolean("night_mode_pref", false)

}
