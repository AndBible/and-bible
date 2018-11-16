package net.bible.android.view.util.locale;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import net.bible.service.common.Logger;

import java.util.Locale;

/**
 * Change locale on older Nougat+ Android devices.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class NougatPlusLocaleChanger implements LocaleChanger {

	private final Logger logger = new Logger(this.getClass().getName());

	@TargetApi(Build.VERSION_CODES.N)
	@Override
	public Context changeLocale(Context context, String language) {
		logger.debug("Update resources N plus");

		Locale locale = new Locale(language);
		Locale.setDefault(locale);

		Configuration configuration = context.getResources().getConfiguration();
		configuration.setLocale(locale);

		return context.createConfigurationContext(configuration);
	}
}
