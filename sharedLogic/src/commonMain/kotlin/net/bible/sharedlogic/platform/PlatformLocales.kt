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

package net.bible.sharedlogic.platform

/** Locale facts that differ per platform; the Android implementation keeps the JDK's legacy language codes. */
interface PlatformLocales {
    /** The default language code as the platform reports it (Android: `iw`, `in`, `ji`, not `he`, `id`, `yi`). */
    val defaultLanguage: String

    /** ISO 639-1 two-letter code to ISO 639-2/T three-letter code, for every language the platform knows. */
    fun iso3ByIso2(): Map<String, String>
}
