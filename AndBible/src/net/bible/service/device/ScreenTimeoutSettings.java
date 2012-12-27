package net.bible.service.device;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.CommonUtils;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

/**
 * Manage local Screen timeout (Sleep) time which is different to the default/system Sleep time
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ScreenTimeoutSettings {

	public static final String SCREEN_TIMEOUT_PREF = "screen_timeout_pref";
	
	// Offset custom timeout by 27 milliseconds to ensure custom timeouts are recognisable - all standard timeouts will be whole seconds
	// This is a safety measure to allow custom timeouts to be recognised and not permanently override system timeout
	private static final int CUSTOM_TIMEOUT_OFFSET_MARKER_MILLIS = 17;

	public static final int NOT_SET = -999;
	public static final int DEFAULT_VALUE = -998;
	
	// this is accessed from different instances and so needs to be static - and can be because there is only one System Sleep value
	private static int systemTimeoutTimeMillis = NOT_SET;
	
	private static final int ONE_MINUTE_MILLIS = 60*1000;
	
	private static final String TAG = "BibleApplication";

	public void overrideScreenTimeout() {
		
    	// set/reset app/standard screen timeout values
    	CurrentActivityHolder.getInstance().addAppToBackgroundListener(new AppToBackgroundListener() {
			@Override
			public void applicationNowInBackground(AppToBackgroundEvent e) {
				restoreSystemTimeout();
			}

			@Override
			public void applicationReturnedFromBackground(AppToBackgroundEvent e) {
				// save the, possibly changed, system timeout
				saveSystemTimeout();
				
				// set And Bible screen timeout
				setScreenTimeout();
			}
		});
	}
	

	
	/**
	 * Default, 2, 10, 30 minutes
	 * @return
	 */
	public String[] getPreferenceEntries() {
        String[] entries = new String[4];
        
        entries[0] = CommonUtils.getResourceString(R.string.default_value);
        entries[1] = CommonUtils.getResourceString(R.string.x_minutes, 2);
        entries[2] = CommonUtils.getResourceString(R.string.x_minutes, 10);
        entries[3] = CommonUtils.getResourceString(R.string.x_minutes, 30);
        
        return entries;
	}

	/** Must be array of String not int[] or Integer[] because of deficiency in Android Preference handling
	 */
	public String[] getPreferenceEntryValues() {
        String[] entryValues = new String[4];
        
        entryValues[0] = Integer.toString(DEFAULT_VALUE);
        entryValues[1] = Integer.toString((2*ONE_MINUTE_MILLIS)+CUSTOM_TIMEOUT_OFFSET_MARKER_MILLIS);
        entryValues[2] = Integer.toString((10*ONE_MINUTE_MILLIS)+CUSTOM_TIMEOUT_OFFSET_MARKER_MILLIS);
        entryValues[3] = Integer.toString((30*ONE_MINUTE_MILLIS)+CUSTOM_TIMEOUT_OFFSET_MARKER_MILLIS);
        
        return entryValues;
	}
	
	public void saveSystemTimeout() {
		try {
			// save old timeout ready for restore when app goes to background
			int currentTimeoutTime = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
			
			// must avoid saving an And Bible specific custom timeout as the systemTimeout because this would cause And Bible to overwrite the default timeout value
			// we use the OFFSET_MARKER to distinguish system and custom timeouts because all system timeouts should be a whole number of seconds
			boolean isCurrentTimeoutCustom = ((currentTimeoutTime % 1000)==CUSTOM_TIMEOUT_OFFSET_MARKER_MILLIS); 
			if (!isCurrentTimeoutCustom) {
				systemTimeoutTimeMillis = currentTimeoutTime;
			}
		} catch (SettingNotFoundException snfe) {
			Log.e(TAG, "Error setting Screen timeout", snfe);
		}
	}
	
	public void restoreSystemTimeout() {
		setScreenTimeout(getSystemTimeoutTimeMillis());
	}

	public void setScreenTimeout() {
		int newTimeout = getScreenTimeoutPreferenceValue();
		setScreenTimeout(newTimeout);
	}

	public static void setScreenTimeout(int newScreenTimeout) {
		// default value is whatever is set as the Android Sleep time in the main Android settings screen
		if (newScreenTimeout==DEFAULT_VALUE) {
			newScreenTimeout = getSystemTimeoutTimeMillis();
		}
		
		if (newScreenTimeout!=NOT_SET) {
			Log.d(TAG, "Set screen timeout:"+newScreenTimeout);
			//override screen timeout
			// Set screen timeout to 10 minutes...
			Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, newScreenTimeout);
		}
	}

	private int getScreenTimeoutPreferenceValue() {
		int screenTimeoutPref = NOT_SET;
		
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		if (preferences!=null) {
			screenTimeoutPref = Integer.parseInt(preferences.getString(SCREEN_TIMEOUT_PREF, Integer.toString(NOT_SET)));
		}
		return screenTimeoutPref;
	}
	
	private static ContentResolver getContentResolver() {
		return BibleApplication.getApplication().getContentResolver();
	}
	
	private static int getSystemTimeoutTimeMillis() {
		return systemTimeoutTimeMillis;
	}
}
