package net.bible.android.view.util;

import net.bible.android.BibleApplication;
import android.app.Activity;
import android.content.SharedPreferences;

public class UiUtils {

	private static final String TAG = "UiUtils"; 
	

    public static void applyTheme(Activity activity) {
//        SharedPreferences preferences = getSharedPreferences();
//
//        if (preferences.getBoolean("night_mode_pref", false)) {
//        	activity.setTheme(android.R.style.Theme);
//        } else {
//        	activity.setTheme(android.R.style.Theme_Light);
//        }
    }
    
    public static SharedPreferences getSharedPreferences() {
    	return BibleApplication.getApplication().getApplicationContext().getSharedPreferences("net.bible.android.activity_preferences", 0);
    }
  
}
