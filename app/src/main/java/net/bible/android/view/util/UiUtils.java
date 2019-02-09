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

package net.bible.android.view.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.ActionBar;
import android.util.TypedValue;
import android.view.View;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class UiUtils {

	private static final int ACTIONBAR_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.actionbar_background_night);
	private static final int ACTIONBAR_BACKGROUND_DAY = CommonUtils.getResourceColor(R.color.actionbar_background_day);

	private static final int BIBLEVIEW_BACKGROUND_NIGHT = CommonUtils.getResourceColor(R.color.bible_background_night);
	private static final int BIBLEVIEW_BACKGROUND_DAY = CommonUtils.getResourceColor(R.color.bible_background_day);

	// taken from css
	private static final int BIBLEVIEW_TEXT_NIGHT = CommonUtils.getResourceColor(R.color.bible_text_night);
	private static final int BIBLEVIEW_TEXT_DAY = CommonUtils.getResourceColor(R.color.bible_text_day);

	public static boolean applyTheme(Activity activity) {
		return applyTheme(activity, true);
	}

	public static boolean applyTheme(Activity activity, boolean recreate) {
    	boolean changed = ScreenSettings.INSTANCE.isNightModeChanged();
    	if(changed) {
			if (ScreenSettings.INSTANCE.isNightMode()) {
				activity.setTheme(R.style.AppThemeNight);
			} else {
				activity.setTheme(R.style.AppThemeDay);
			}
			if (recreate) {
				activity.recreate();
			}
			return true;
		}
		return false;
    }

	/** Change actionBar colour according to day/night state
	 */
	public static void setActionBarColor(final ActionBar actionBar) {
		final int newColor = ScreenSettings.INSTANCE.isNightMode() ? ACTIONBAR_BACKGROUND_NIGHT : ACTIONBAR_BACKGROUND_DAY;

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
		return ScreenSettings.INSTANCE.isNightMode() ? BIBLEVIEW_BACKGROUND_NIGHT : BIBLEVIEW_BACKGROUND_DAY;
	}
	public static int getTextColour() {
		return ScreenSettings.INSTANCE.isNightMode() ? BIBLEVIEW_TEXT_NIGHT : BIBLEVIEW_TEXT_DAY;
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
