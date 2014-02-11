package net.bible.android.view.util;

import net.bible.android.activity.R;
import net.bible.service.device.ScreenSettings;
import android.app.Activity;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class UiUtils {

	@SuppressWarnings("unused")
	private static final String TAG = "UiUtils"; 
	

    public static void applyTheme(Activity activity) {
    	ScreenSettings.isNightModeChanged();
        if (ScreenSettings.isNightMode()) {
        	activity.setTheme(R.style.AppThemeNight);
        } else {
        	activity.setTheme(R.style.AppThemeDay);
        }
    }
}
