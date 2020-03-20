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
 */

public class NougatPlusLocaleChanger implements LocaleChanger {

	private final Logger logger = new Logger(this.getClass().getName());

	@TargetApi(Build.VERSION_CODES.N)
	@Override
	public Context changeLocale(Context context, String language) {
		logger.debug("Update resources N plus");

		Locale locale = Locale.forLanguageTag(language);
		Locale.setDefault(locale);

		Configuration configuration = context.getResources().getConfiguration();
		configuration.setLocale(locale);

		return context.createConfigurationContext(configuration);
	}
}
