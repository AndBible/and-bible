/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedlogic.platform.ios

import net.bible.sharedlogic.locale.Iso639
import net.bible.sharedlogic.platform.PlatformLocales
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/**
 * [PlatformLocales] for iOS. [defaultLanguage] returns Android's legacy codes (`iw`, `in`, `ji`) so shared code sees
 * what it sees on Android; [languageProvider] is the seam tests use to inject the device language.
 */
class IosPlatformLocales(
    private val languageProvider: () -> String = { NSLocale.currentLocale.languageCode },
) : PlatformLocales {
    override val defaultLanguage: String
        get() = when (val code = languageProvider()) {
            "he" -> "iw"
            "id" -> "in"
            "yi" -> "ji"
            else -> code
        }

    override fun iso3ByIso2(): Map<String, String> = Iso639.iso3ByIso2
}
