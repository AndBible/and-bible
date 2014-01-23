package net.bible.android.view.util;

import net.bible.service.device.ScreenSettings;
import android.app.Activity;
import android.util.Log;

public class UiUtils {

	@SuppressWarnings("unused")
	private static final String TAG = "UiUtils"; 
	

    public static void applyTheme(Activity activity) {
    	ScreenSettings.updateNightModeValue();
        if (ScreenSettings.isNightMode()) {
        	Log.d(TAG, "THEME Night");
        	activity.setTheme(android.R.style.Theme_Holo);
        } else {
        	Log.d(TAG, "THEME Day");
        	activity.setTheme(android.R.style.Theme_Holo_Light_DarkActionBar);
        }
    }
  
}
