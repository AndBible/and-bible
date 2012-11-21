package net.bible.android.view.util;

import net.bible.service.device.ScreenSettings;
import android.app.Activity;

public class UiUtils {

	@SuppressWarnings("unused")
	private static final String TAG = "UiUtils"; 
	

    public static void applyTheme(Activity activity) {
    	ScreenSettings.updateNightModeValue();
        if (ScreenSettings.isNightMode()) {
        	activity.setTheme(android.R.style.Theme);
        } else {
        	activity.setTheme(android.R.style.Theme_Light);
        }
    }
  
}
