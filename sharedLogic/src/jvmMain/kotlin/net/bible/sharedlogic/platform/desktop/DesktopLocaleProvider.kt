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

import net.bible.service.sword.OverridableLocaleProvider
import org.crosswire.common.util.KLocale
import java.util.Locale

/**
 * The Sword locale provider on the desktop: the JVM default locale, with `sr-Latn` mapped to the `sr_LT` Sword
 * resources use (Serbian in Latin script), as on iOS and Android.
 */
class DesktopLocaleProvider(
    private val localeProvider: () -> Locale = { Locale.getDefault() },
) : OverridableLocaleProvider {
    override var override: KLocale? = null

    override fun getUserLocale(): KLocale {
        override?.let { return it }
        val locale = localeProvider()
        val language = locale.language
        if (language.isEmpty()) return KLocale.ROOT
        if (language.equals("sr", ignoreCase = true) && locale.script.equals("Latn", ignoreCase = true)) return KLocale("sr", "LT")
        return KLocale(language, locale.country)
    }
}
