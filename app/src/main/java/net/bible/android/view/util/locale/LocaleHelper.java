/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.util.locale;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import org.apache.commons.lang3.StringUtils;

/**
 * This class is used to change your application locale.
 * @see <a href="http://gunhansancar.com/change-language-programmatically-in-android/">article</a>
 */
public class LocaleHelper {

	private static final LocaleChangerFactory localeChangerFactory = new LocaleChangerFactory();

	private static final String SELECTED_LANGUAGE = "locale_pref";

	public static void translateTitle(Activity activity) {
		if (isLocaleOverridden(activity)) {
			// http://stackoverflow.com/questions/22884068/troubles-with-activity-title-language
			try {
				int labelRes = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).labelRes;
				activity.setTitle(labelRes);
			} catch (PackageManager.NameNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public static Context onAttach(Context context) {
		String overrideLang = getOverrideLanguage(context);
		if (StringUtils.isNotEmpty(overrideLang)) {
			return localeChangerFactory.getLocaleChanger().changeLocale(context, overrideLang);
		} else {
			return context;
		}
	}

	private static boolean isLocaleOverridden(Context context) {
		return StringUtils.isNotEmpty(getOverrideLanguage(context));
	}

	public static String getOverrideLanguage(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		return preferences.getString(SELECTED_LANGUAGE, "");
	}
}
