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
package net.bible.sharedlogic.platform.desktop

import net.bible.sharedlogic.locale.Iso639
import net.bible.sharedlogic.platform.PlatformLocales
import java.util.Locale

/**
 * [PlatformLocales] for the desktop. Since JDK 17 `Locale.getLanguage()` reports `he`, `id` and `yi`; Android (and the
 * persisted data and Sword modules that follow it) use the legacy `iw`, `in` and `ji`, so those are mapped back.
 */
class DesktopPlatformLocales(
    private val languageProvider: () -> String = { Locale.getDefault().language },
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
