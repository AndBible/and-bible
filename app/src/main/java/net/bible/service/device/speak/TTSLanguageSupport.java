/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.device.speak;

import java.util.Locale;

import net.bible.service.common.CommonUtils;

/** maintain a list of languages that are knwn to be supported by the installed TTS engine
 * this list will be updated on success/failure of TTS init
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class TTSLanguageSupport {

	private static final String TTS_LANG_SUPPORTED_KEY = "TTS_LANG_SUPPORTED";
	private static final String LANG_SEPERATOR = ",";

	public void addSupportedLocale(Locale locale) {
		
		if (!isLangKnownToBeSupported(locale.getLanguage())) {
			String langCode = locale.getLanguage();
			String langList = getSupportedLangList();
			CommonUtils.INSTANCE.getSettings()
						.setString(TTS_LANG_SUPPORTED_KEY, langList+LANG_SEPERATOR+langCode);
		}
	}
	
	public void addUnsupportedLocale(Locale locale) {
		if (isLangKnownToBeSupported(locale.getLanguage())) {
			String langCode = locale.getLanguage();
			String langList = getSupportedLangList();
			CommonUtils.INSTANCE.getSettings()
						.setString(TTS_LANG_SUPPORTED_KEY, langList.replace(LANG_SEPERATOR+langCode, ""));
		}
	}
	
	public boolean isLangKnownToBeSupported(String langCode) {
		boolean isSupported = getSupportedLangList().contains(langCode);
		return isSupported;
	}
	
	private String getSupportedLangList() {
		return CommonUtils.INSTANCE.getSettings().getString(TTS_LANG_SUPPORTED_KEY, "");
	}
}
