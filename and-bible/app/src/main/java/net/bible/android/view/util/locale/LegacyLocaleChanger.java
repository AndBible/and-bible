package net.bible.android.view.util.locale;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import net.bible.service.common.Logger;

import java.util.Locale;

/**
 * Change locale on older (pre-N) Android devices.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class LegacyLocaleChanger implements LocaleChanger {

	private final Logger logger = new Logger(this.getClass().getName());

	@SuppressWarnings("deprecation")
	@Override
	public Context changeLocale(Context context, String language) {
		logger.debug("Update resources legacy to:"+language);
		Locale locale = new Locale(language);
		Locale.setDefault(locale);

		Resources resources = context.getResources();

		Configuration configuration = resources.getConfiguration();
		configuration.locale = locale;

		resources.updateConfiguration(configuration, resources.getDisplayMetrics());

		return context;

	}
}
