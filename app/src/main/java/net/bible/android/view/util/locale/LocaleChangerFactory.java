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

import net.bible.service.common.CommonUtils;

/**
 * Prevent 'Could not find method' warnings due to old Android versions not having configuration.setLocale(x)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

public class LocaleChangerFactory {
	private LocaleChanger localeChanger;

	public LocaleChangerFactory() {
		if (CommonUtils.INSTANCE.isNougatPlus()) {
			localeChanger = new NougatPlusLocaleChanger();
		} else {
			localeChanger = new LegacyLocaleChanger();
		}
	}

	public LocaleChanger getLocaleChanger() {
		return localeChanger;
	}
}
