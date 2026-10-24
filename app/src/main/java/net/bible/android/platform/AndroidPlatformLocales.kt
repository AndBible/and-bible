/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.platform

import net.bible.sharedlogic.platform.PlatformLocales
import java.util.Locale
import java.util.MissingResourceException

/** JDK-backed [PlatformLocales]; no `KLocale` normalization, so legacy codes (`iw`, `in`, `ji`) stay as-is. */
class AndroidPlatformLocales(
    private val defaultLocale: () -> Locale = Locale::getDefault,
    /** Seam: JDK 17+ already reports `he` for the legacy `iw` locale, so tests inject the raw legacy code here. */
    private val languageOf: (Locale) -> String = Locale::getLanguage,
) : PlatformLocales {
    override val defaultLanguage: String get() = languageOf(defaultLocale())

    override fun iso3ByIso2(): Map<String, String> =
        Locale.getISOLanguages().mapNotNull { iso2 ->
            try {
                iso2 to Locale(iso2).isO3Language
            } catch (e: MissingResourceException) {
                null
            }
        }.toMap()
}
