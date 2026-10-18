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

import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.LocaleValue
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.scriptCode

/** [LocaleSupport] on `NSLocale.currentLocale`, read on every access. `asString` is `language_COUNTRY` like `Locale.toString()`. */
object IosLocaleSupport : LocaleSupport by IosLocaleSupportImpl()

/**
 * The implementation behind [IosLocaleSupport]; [localeProvider] is the test seam (the object reads the current locale).
 *
 * Language codes: this class and `IosLocaleProvider` use the modern codes `NSLocale` reports (`he`, `id`, `yi`),
 * and `IosPlatformLocales.defaultLanguage` alone maps them to Android's legacy `iw`, `in`, `ji`, because that is the
 * one place shared code compares the default language against legacy JDK codes (`AndroidPlatformLocales`).
 * `LocaleValue.language` is not used for such comparisons.
 */
internal class IosLocaleSupportImpl(
    private val localeProvider: () -> NSLocale = { NSLocale.currentLocale },
) : LocaleSupport {
    private fun value(language: String, country: String, script: String = ""): LocaleValue {
        val lang = language.lowercase()
        val region = country.uppercase()
        val tag = listOf(lang, script, region).filter { it.isNotEmpty() }.joinToString("-")
        val string = if (region.isEmpty()) lang else "${lang}_$region"
        return LocaleValue(lang, region, tag, string)
    }

    private fun from(locale: NSLocale) =
        value(locale.languageCode, locale.countryCode ?: "", locale.scriptCode ?: "")

    override val default: LocaleValue get() = from(localeProvider())

    override fun of(language: String, country: String) = value(language, country)

    /** Accepts `fi`, `en_GB` and `en-GB`. */
    override fun parse(legacy: String): LocaleValue {
        val parts = legacy.split('_', '-')
        return value(parts[0], parts.getOrNull(1) ?: "")
    }

    override fun displayLanguage(tag: String): String {
        val locale = NSLocale.localeWithLocaleIdentifier(tag)
        return locale.localizedStringForLanguageCode(locale.languageCode) ?: tag
    }

    override fun defaultDisplayLanguage(): String = localeProvider().let {
        it.localizedStringForLanguageCode(it.languageCode) ?: it.languageCode
    }

    override fun formatFixed(value: Double, decimals: Int): String = formatter(decimals, grouping = false)
        .stringFromNumber(NSNumber(double = value)) ?: value.toString()

    override fun formatGrouped(value: Long): String = formatter(0, grouping = true)
        .stringFromNumber(NSNumber(longLong = value)) ?: value.toString()

    private fun formatter(decimals: Int, grouping: Boolean) = NSNumberFormatter().apply {
        locale = localeProvider()
        // Java's %.nf rounds half up; NSNumberFormatter defaults to half even.
        roundingMode = NSNumberFormatterRoundHalfUp
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = decimals.toULong()
        maximumFractionDigits = decimals.toULong()
        usesGroupingSeparator = grouping
    }
}
