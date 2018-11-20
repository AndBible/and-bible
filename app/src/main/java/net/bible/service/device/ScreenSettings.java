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

package net.bible.service.device;

import net.bible.android.BibleApplication;
import net.bible.service.common.CommonUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

/** Manage screen related functions
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ScreenSettings {

	public static final String NIGHT_MODE_PREF_NO_SENSOR = "night_mode_pref";
	public static final String NIGHT_MODE_PREF_WITH_SENSOR = "night_mode_pref2";
	
	private static final String NIGHT_MODE = "true";
	private static final String NOT_NIGHT_MODE = "false";
	private static final String AUTO_NIGHT_MODE = "automatic";

	private static LightSensor mLightSensor = new LightSensor();

	private static final int MAX_DARK_READING = 30;

	private static boolean isNightMode = false;
	
	private static int contentViewHeightPx = 0;
	private static int lineHeightDips = 0;
	
	private static final String TAG = "ScreenSettings";
	
	public static boolean isNightModeChanged() {
		boolean origNightMode = isNightMode;
		
		String nightModePref = getNightModePreferenceValue();
		if (AUTO_NIGHT_MODE.equals(nightModePref)) {
			int lightReading = mLightSensor.getReading();
			// may possible be no reading yet but need to have a screen colour
			// If no light change has occurred then it is most likely pitch black so allow default of black, 
			// which will happen automatically because NO_READING_YET is negative 
			isNightMode = lightReading<=MAX_DARK_READING;
		} else {
			isNightMode = NIGHT_MODE.equals(nightModePref);
		}
		
		return origNightMode!=isNightMode;
	}
	
	public static boolean isNightMode() {
		return isNightMode;
	}
	
	public static boolean isScreenOn() {
		PowerManager pm = (PowerManager) BibleApplication.getApplication().getSystemService(Context.POWER_SERVICE);
		return pm.isScreenOn();
	}
	
	/** get the preference setting - could be using either of 2 preference settings depending on presence of a light sensor
	 */
	private static String getNightModePreferenceValue() {
		String nightModePref = NOT_NIGHT_MODE;
		
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			String preferenceKey = getUsedNightModePreferenceKey();
			if (NIGHT_MODE_PREF_WITH_SENSOR.equals(preferenceKey)) {
				nightModePref = preferences.getString(preferenceKey, NOT_NIGHT_MODE);
			} else {
				// boolean pref setting if no light meter
				boolean isNightMode = preferences.getBoolean(preferenceKey, false);
				nightModePref = isNightMode ? NIGHT_MODE : NOT_NIGHT_MODE;
			}
		}
		return nightModePref;
	}
		
	/** get the preference key being used/unused, dependent on light sensor availability
	 */
	public static String getUsedNightModePreferenceKey() {
		return mLightSensor.isLightSensor() ? ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR : ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR;
	}			
	public static String getUnusedNightModePreferenceKey() {
		return mLightSensor.isLightSensor() ? ScreenSettings.NIGHT_MODE_PREF_NO_SENSOR : ScreenSettings.NIGHT_MODE_PREF_WITH_SENSOR;
	}

	/** get the height of the WebView that will contain the text
	 */
	public static int getContentViewHeightDips() {
		int heightPx = 0;
		// content view height is not set until after the first page view so first call is normally an approximation
		if (contentViewHeightPx > 0) {
			heightPx = contentViewHeightPx;
		} else {
			// return an appropriate default if the actual content height has not been set yet
			heightPx = BibleApplication.getApplication().getResources().getDisplayMetrics().heightPixels;
		}
		
		int heightDips = CommonUtils.convertPxToDips(heightPx);
		
		return heightDips;
	}
	public static void setContentViewHeightPx(int contentViewHeightPx) {
		ScreenSettings.contentViewHeightPx = contentViewHeightPx;
	}

	/** get the height of each line in the WebView
	 */
	public static int getLineHeightDips() {
		return lineHeightDips;
	}
	public static void setLineHeightDips(int lineHeightDips) {
		Log.d(TAG, "LineHeightPx:"+lineHeightDips);
		ScreenSettings.lineHeightDips = lineHeightDips;
	}
}
