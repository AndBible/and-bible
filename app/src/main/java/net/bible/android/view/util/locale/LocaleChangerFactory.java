package net.bible.android.view.util.locale;

import net.bible.service.common.CommonUtils;

/**
 * Prevent 'Could not find method' warnings due to old Android versions not having configuration.setLocale(x)
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */

public class LocaleChangerFactory {
	private LocaleChanger localeChanger;

	public LocaleChangerFactory() {
		if (CommonUtils.isNougatPlus()) {
			localeChanger = new NougatPlusLocaleChanger();
		} else {
			localeChanger = new LegacyLocaleChanger();
		}
	}

	public LocaleChanger getLocaleChanger() {
		return localeChanger;
	}
}
