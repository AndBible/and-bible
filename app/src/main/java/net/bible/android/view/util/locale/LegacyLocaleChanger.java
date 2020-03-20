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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import net.bible.service.common.Logger;

import java.util.Locale;

/**
 * Change locale on older (pre-N) Android devices.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

public class LegacyLocaleChanger implements LocaleChanger {

	private final Logger logger = new Logger(this.getClass().getName());

	@SuppressWarnings("deprecation")
	@Override
	public Context changeLocale(Context context, String language) {
		logger.debug("Update resources legacy to:"+language);
		Locale locale = null;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
			locale = Locale.forLanguageTag(language);
		} else {
			locale = new Locale(language);
		}

		Locale.setDefault(locale);

		Resources resources = context.getResources();

		Configuration configuration = resources.getConfiguration();
		configuration.locale = locale;

		resources.updateConfiguration(configuration, resources.getDisplayMetrics());

		return context;

	}
}
