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

    private var contentViewHeightPx = 0
    private var lineHeightDips = 0

    private val TAG = "ScreenSettings"

    // may possible be no reading yet but need to have a screen colour
    // If no light change has occurred then it is most likely pitch black so allow default of black,
    // which will happen automatically because NO_READING_YET is negative
    val isNightModeChanged: Boolean
        get() {
            val origNightMode = isNightMode

            val nightModePref = nightModePreferenceValue
            if (AUTO_NIGHT_MODE == nightModePref) {
                val lightReading = mLightSensor.reading
                isNightMode = lightReading <= MAX_DARK_READING
            } else {
                isNightMode = NIGHT_MODE == nightModePref
            }

            return origNightMode != isNightMode
        }

    val isScreenOn: Boolean
        get() {
            val pm = BibleApplication.application.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isScreenOn
        }

    /** get the preference setting - could be using either of 2 preference settings depending on presence of a light sensor
     */
    private// boolean pref setting if no light meter
    val nightModePreferenceValue: String
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
        get() = if (mLightSensor.isLightSensor) ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR else ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR
    val unusedNightModePreferenceKey: String
        get() = if (mLightSensor.isLightSensor) ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR else ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR

    /** get the height of the WebView that will contain the text
     */
    // content view height is not set until after the first page view so first call is normally an approximation
    // return an appropriate default if the actual content height has not been set yet
    val contentViewHeightDips: Int
        get() {
            var heightPx = 0
            if (contentViewHeightPx > 0) {
                heightPx = contentViewHeightPx
            } else {
                heightPx = BibleApplication.application.resources.displayMetrics.heightPixels
            }

            return CommonUtils.convertPxToDips(heightPx)
        }

    fun setContentViewHeightPx(contentViewHeightPx: Int) {
        ScreenSettings.contentViewHeightPx = contentViewHeightPx
    }

    /** get the height of each line in the WebView
     */
    fun getLineHeightDips(): Int {
        return lineHeightDips
    }

    fun setLineHeightDips(lineHeightDips: Int) {
        Log.d(TAG, "LineHeightPx:$lineHeightDips")
        ScreenSettings.lineHeightDips = lineHeightDips
    }
}
