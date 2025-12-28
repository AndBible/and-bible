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
package net.bible.service.device.speak

import net.bible.service.common.CommonUtils.settings
import java.util.*

/** maintain a list of languages that are knwn to be supported by the installed TTS engine
 * this list will be updated on success/failure of TTS init
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class TTSLanguageSupport {
    fun addSupportedLocale(locale: Locale) {
        if (!isLangKnownToBeSupported(locale.language)) {
            val langCode = locale.language
            val langList = supportedLangList
            settings
                .setString(TTS_LANG_SUPPORTED_KEY, langList + LANG_SEPARATOR + langCode)
        }
    }

    fun addUnsupportedLocale(locale: Locale) {
        if (isLangKnownToBeSupported(locale.language)) {
            val langCode = locale.language
            val langList = supportedLangList
            settings
                .setString(
                    TTS_LANG_SUPPORTED_KEY,
                    langList.replace(LANG_SEPARATOR + langCode, "")
                )
        }
    }

    fun isLangKnownToBeSupported(langCode: String?): Boolean {
        return supportedLangList.contains(langCode!!)
    }

    private val supportedLangList: String
        get() = settings.getString(TTS_LANG_SUPPORTED_KEY, "")!!

    companion object {
        private const val TTS_LANG_SUPPORTED_KEY = "TTS_LANG_SUPPORTED"
        private const val LANG_SEPARATOR = ","
    }
}
