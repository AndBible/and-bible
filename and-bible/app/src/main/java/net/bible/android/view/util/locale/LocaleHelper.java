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
		return preferences.getString(SELECTED_LANGUAGE, null);
	}
}