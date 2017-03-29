package net.bible.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import org.apache.commons.lang.StringUtils;

import java.util.Locale;

/**
 * This class is used to change your application locale.
 * @see \http://gunhansancar.com/change-language-programmatically-in-android/
 */
public class LocaleHelper {

	private static final String SELECTED_LANGUAGE = "locale_pref";

	public static void translateTitle(Activity activity) {
		if (LocaleHelper.isLocaleOverridden(activity)) {
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
		if (!StringUtils.isEmpty(overrideLang)) {
			return setLocale(context, overrideLang);
		} else {
			return context;
		}
	}

	private static Context setLocale(Context context, String language) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return updateResources(context, language);
		}

		return updateResourcesLegacy(context, language);
	}

	private static boolean isLocaleOverridden(Context context) {
		return StringUtils.isNotEmpty(getOverrideLanguage(context));
	}

	private static String getOverrideLanguage(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		return preferences.getString(SELECTED_LANGUAGE, null);
	}

	@TargetApi(Build.VERSION_CODES.N)
	private static Context updateResources(Context context, String language) {
		Locale locale = new Locale(language);
		Locale.setDefault(locale);

		Configuration configuration = context.getResources().getConfiguration();
		configuration.setLocale(locale);

		return context.createConfigurationContext(configuration);
	}

	@SuppressWarnings("deprecation")
	private static Context updateResourcesLegacy(Context context, String language) {
		Locale locale = new Locale(language);
		Locale.setDefault(locale);

		Resources resources = context.getResources();

		Configuration configuration = resources.getConfiguration();
		configuration.locale = locale;

		resources.updateConfiguration(configuration, resources.getDisplayMetrics());

		return context;
	}
}