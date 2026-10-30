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

import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.LocaleValue
import java.text.NumberFormat
import java.util.Locale

/** [LocaleSupport] of the JVM default locale. */
object DesktopLocaleSupport : LocaleSupport by DesktopLocaleSupportImpl()

internal class DesktopLocaleSupportImpl(
    private val localeProvider: () -> Locale = { Locale.getDefault() },
) : LocaleSupport {
    private fun value(language: String, country: String, script: String = ""): LocaleValue {
        val lang = language.lowercase()
        val region = country.uppercase()
        val tag = listOf(lang, script, region).filter { it.isNotEmpty() }.joinToString("-")
        val string = if (region.isEmpty()) lang else "${lang}_$region"
        return LocaleValue(lang, region, tag, string)
    }

    override val default: LocaleValue get() = localeProvider().let { value(it.language, it.country, it.script) }

    override fun of(language: String, country: String) = value(language, country)

    /** Accepts `fi`, `en_GB` and `en-GB`. */
    override fun parse(legacy: String): LocaleValue {
        val parts = legacy.split('_', '-')
        return value(parts[0], parts.getOrNull(1) ?: "")
    }

    override fun displayLanguage(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).ifEmpty { tag }
    }

    override fun defaultDisplayLanguage(): String = localeProvider().let { it.getDisplayLanguage(it) }

    /** Java's `%.nf` rounds half up, as on Android. */
    override fun formatFixed(value: Double, decimals: Int): String = String.format(localeProvider(), "%.${decimals}f", value)

    override fun formatGrouped(value: Long): String = NumberFormat.getIntegerInstance(localeProvider()).apply { isGroupingUsed = true }.format(value)
}
