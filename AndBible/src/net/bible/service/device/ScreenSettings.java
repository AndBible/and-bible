package net.bible.service.device;

import android.content.SharedPreferences;
import net.bible.service.common.CommonUtils;

public class ScreenSettings {

	LightSensor mLightSensor = new LightSensor();		
	
	private static final int MAX_DARK_READING = 50;
	
	public boolean isNightMode() {
		boolean isNightMode = false;
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			if (preferences.getBoolean("night_mode_pref", false)) {
				int lightReading = mLightSensor.getReading();
				// -1 == no sensor
				isNightMode = (lightReading!=-1 && lightReading<=MAX_DARK_READING);
			}
		}
		
		return isNightMode;
	}
}
