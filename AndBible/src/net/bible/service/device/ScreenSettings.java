package net.bible.service.device;

import android.content.SharedPreferences;
import net.bible.service.common.CommonUtils;

public class ScreenSettings {

	private static final String NIGHT_MODE_PREF = "night_mode_pref2";
	private static final String NIGHT_MODE = "true";
	private static final String NOT_NIGHT_MODE = "false";
	private static final String AUTO_NIGHT_MODE = "automatic";

	private static LightSensor mLightSensor = new LightSensor();		
	
	private static final int MAX_DARK_READING = 50;
	
	public static boolean isNightMode() {
		boolean isNightMode = false;
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			String nightModePref = preferences.getString(NIGHT_MODE_PREF, NOT_NIGHT_MODE);
			
			if (AUTO_NIGHT_MODE.equals(nightModePref)) {
				int lightReading = mLightSensor.getReading();
				// -1 == no sensor
				isNightMode = (lightReading!=-1 && lightReading<=MAX_DARK_READING);
			} else {
				isNightMode = NIGHT_MODE.equals(nightModePref);
			}
		}
		
		return isNightMode;
	}
}
