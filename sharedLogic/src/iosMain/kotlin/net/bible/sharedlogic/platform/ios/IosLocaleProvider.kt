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

import net.bible.service.sword.OverridableLocaleProvider
import org.crosswire.common.util.KLocale
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

/**
 * iOS [OverridableLocaleProvider]: the override first, then the JSword exception for Serbian in Latin script
 * (`sr-Latn` is `KLocale("sr", "LT")`, as on Android), else the current locale. [localeTag] is the seam tests use;
 * it returns a BCP 47 style tag (`pt-BR`, `sr-Latn-RS`), with `_` also accepted.
 */
class IosLocaleProvider(
    private val localeTag: () -> String = { NSLocale.currentLocale.localeIdentifier },
) : OverridableLocaleProvider {
    override var override: KLocale? = null

    override fun getUserLocale(): KLocale {
        override?.let { return it }
        val parts = localeTag().substringBefore('@').split('-', '_').filter { it.isNotEmpty() }
        val language = parts.firstOrNull() ?: return KLocale.ROOT
        val script = parts.drop(1).firstOrNull { it.length == 4 && it.all(Char::isLetter) }
        val region = parts.drop(1).firstOrNull { (it.length == 2 && it.all(Char::isLetter)) || (it.length == 3 && it.all(Char::isDigit)) }
        if (language.lowercase() == "sr" && script.equals("Latn", ignoreCase = true)) return KLocale("sr", "LT")
        return KLocale(language, region ?: "")
    }
}
