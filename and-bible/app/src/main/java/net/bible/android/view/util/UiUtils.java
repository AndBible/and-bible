package net.bible.android.view.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.util.TypedValue;
import android.view.View;

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

	private static final int ACTIONBAR_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.actionbar_background_night);
	private static final int ACTIONBAR_BACKGROUND_DAY = CommonUtils.getResourceColor(R.color.actionbar_background_day);

	private static final int BIBLEVIEW_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.bible_background_night);
	private static final int BIBLEVIEW_BACKGROUND_DAY = CommonUtils.getResourceColor(R.color.bible_background_day);

	// taken from css
	private static final int BIBLEVIEW_TEXT_NIGHT = CommonUtils.getResourceColor(R.color.bible_text_night);
	private static final int BIBLEVIEW_TEXT_DAY = CommonUtils.getResourceColor(R.color.bible_text_day);

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
		final int newColor = ScreenSettings.isNightMode() ? ACTIONBAR_BACKGROUND_NIGHT : ACTIONBAR_BACKGROUND_DAY;

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

	public static void setBibleViewBackgroundColour(View bibleView, boolean nightMode) {
		bibleView.setBackgroundColor(nightMode ? BIBLEVIEW_BACKGROUND_NIGHT : BIBLEVIEW_BACKGROUND_DAY);
	}

	public static int getBackgroundColour() {
		return ScreenSettings.isNightMode() ? BIBLEVIEW_BACKGROUND_NIGHT : BIBLEVIEW_BACKGROUND_DAY;
	}
	public static int getTextColour() {
		return ScreenSettings.isNightMode() ? BIBLEVIEW_TEXT_NIGHT : BIBLEVIEW_TEXT_DAY;
	}

	public static int getThemeBackgroundColour(Context context) {
		TypedValue a = new TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);

		if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
			// windowBackground is a color
			return a.data;
		} else {
			// windowBackground is not a color, probably a drawable so just guess a colour, but hopefully theme backgrounds are always colors anyway
			return getBackgroundColour();
		}
	}

	public static int getThemeTextColour(Context context) {
		TypedValue a = new TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.textColor, a, true);

		if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
			// textColor is a color
			return a.data;
		} else {
			// textColor is not a color, probably a drawable so just guess a colour, but hopefully theme backgrounds are always colors anyway
			return getTextColour();
		}
	}
}
