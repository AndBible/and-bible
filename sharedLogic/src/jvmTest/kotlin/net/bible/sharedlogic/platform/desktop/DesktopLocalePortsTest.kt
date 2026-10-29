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

import org.crosswire.common.util.KLocale
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLocalePortsTest {
    private fun tag(t: String) = Locale.forLanguageTag(t)

    @Test fun platformLocalesMapsModernCodesToLegacy() {
        assertEquals("iw", DesktopPlatformLocales { "he" }.defaultLanguage)
        assertEquals("in", DesktopPlatformLocales { "id" }.defaultLanguage)
        assertEquals("ji", DesktopPlatformLocales { "yi" }.defaultLanguage)
        assertEquals("fi", DesktopPlatformLocales { "fi" }.defaultLanguage)
        assertEquals("iw", DesktopPlatformLocales { "iw" }.defaultLanguage)
    }

    @Test fun platformLocalesDefaultsToTheJvmLocaleAndHasIso3Table() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(tag("he-IL"))
            assertEquals("iw", DesktopPlatformLocales().defaultLanguage)
        } finally { Locale.setDefault(old) }
        assertEquals("fin", DesktopPlatformLocales().iso3ByIso2()["fi"])
    }

    @Test fun localeProviderSerbianLatinException() {
        assertEquals("LT", DesktopLocaleProvider { tag("sr-Latn-RS") }.getUserLocale().country)
        assertEquals("sr", DesktopLocaleProvider { tag("sr-Latn") }.getUserLocale().language)
        assertEquals("RS", DesktopLocaleProvider { tag("sr-Cyrl-RS") }.getUserLocale().country)
        assertEquals("RS", DesktopLocaleProvider { tag("sr-RS") }.getUserLocale().country)
    }

    @Test fun localeProviderKeepsLanguageAndCountryAndHonoursOverride() {
        val provider = DesktopLocaleProvider { tag("en-GB") }
        assertEquals("en", provider.getUserLocale().language)
        assertEquals("GB", provider.getUserLocale().country)
        provider.override = KLocale("fi")
        assertEquals("fi", provider.getUserLocale().language)
        provider.override = null
        assertEquals("en", provider.getUserLocale().language)
        assertEquals(KLocale.ROOT.language, DesktopLocaleProvider { Locale.ROOT }.getUserLocale().language)
    }

    @Test fun localeSupportValuesAndParsing() {
        val support = DesktopLocaleSupportImpl { tag("en-GB") }
        assertEquals("en_GB", support.default.asString)
        assertEquals("en-GB", support.default.languageTag)
        assertEquals("sr-Latn-RS", DesktopLocaleSupportImpl { tag("sr-Latn-RS") }.default.languageTag)
        assertEquals("en_GB", support.parse("en-GB").asString)
        assertEquals("fi", support.parse("fi").asString)
        assertEquals("pt_BR", support.of("PT", "br").asString)
    }

    @Test fun localeSupportFormatting() {
        val fi = DesktopLocaleSupportImpl { tag("fi-FI") }
        assertEquals("1,5", fi.formatFixed(1.5, 1))
        assertEquals("0,13", fi.formatFixed(0.125, 2)) // half up, as Java and Android
        assertEquals("suomi", fi.defaultDisplayLanguage())
        assertEquals("svenska", fi.displayLanguage("sv"))
        assertEquals("1 234 567", fi.formatGrouped(1234567))
        assertEquals("1,234,567", DesktopLocaleSupportImpl { Locale.US }.formatGrouped(1234567))
    }
}
