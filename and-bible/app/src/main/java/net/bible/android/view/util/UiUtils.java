package net.bible.android.view.util;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class UiUtils {

	private static final int night = CommonUtils.getResourceColor(R.color.actionbar_background_night);
	private static final int day = CommonUtils.getResourceColor(R.color.actionbar_background_day);


    public static void applyTheme(Activity activity) {
    	ScreenSettings.isNightModeChanged();
        if (ScreenSettings.isNightMode()) {
        	activity.setTheme(R.style.AppThemeNight);
        } else {
        	activity.setTheme(R.style.AppThemeDay);
        }
    }

	/** Change actionBar colour according to day/night state
	 */
	public static void setActionBarColor(final ActionBar actionBar) {
		final int newColor = ScreenSettings.isNightMode() ? night : day;

		if (actionBar!=null) {
			CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {

				@Override
				public void run() {
					Drawable colorDrawable = new ColorDrawable(newColor);
			        actionBar.setBackgroundDrawable(colorDrawable);
				}
			});
		}
	}

}
