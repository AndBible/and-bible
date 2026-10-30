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
package net.bible.sharedui.desktop.assembly

import net.bible.sharedcore.settings.DeviceLocalPrefs
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUiLanguageTest {
    private val english = Locale.forLanguageTag("en-US")

    private class Prefs(private val values: Map<String, String> = emptyMap()) : DeviceLocalPrefs {
        override fun getString(key: String, default: String?): String? = values[key] ?: default
        override fun setString(key: String, value: String) = Unit
        override fun getBoolean(key: String, default: Boolean) = default
        override fun setBoolean(key: String, value: Boolean) = Unit
        override fun remove(key: String) = Unit
    }

    private fun resolve(vararg env: Pair<String, String>, prefs: DeviceLocalPrefs = Prefs(), locale: Locale = english) =
        DesktopUiLanguage.resolve(prefs, env.toMap()::get, locale)

    @Test fun arabicLangIsArabicAndRightToLeft() {
        val language = resolve("LANG" to "ar_EG.UTF-8")
        assertEquals("ar", language)
        assertTrue(DesktopUiLanguage.isRtl(language))
    }

    @Test fun anUnsupportedLangFallsBackToEnglish() {
        val language = resolve("LANG" to "xx_YY", locale = Locale.forLanguageTag("xx-YY"))
        assertEquals("en", language)
        assertFalse(DesktopUiLanguage.isRtl(language))
    }

    @Test fun languageListWinsOverLangAndTakesTheFirstSupportedEntry() {
        assertEquals("fi", resolve("LANGUAGE" to "fi:en", "LANG" to "de_DE.UTF-8"))
        assertEquals("fi", resolve("LANGUAGE" to "xx:fi:en"))
    }

    @Test fun theJvmLocaleIsTheLastResort() {
        assertEquals("fi", resolve(locale = Locale.forLanguageTag("fi-FI")))
        assertEquals("de", resolve("LANG" to "C", locale = Locale.forLanguageTag("de-DE")))
    }

    @Test fun localePrefOfTheDevicePrefsWins() {
        assertEquals("sv", resolve("LANG" to "fi_FI.UTF-8", prefs = Prefs(mapOf("locale_pref" to "sv"))))
    }

    @Test fun preferredLanguagesDropEncodingModifierAndPosixNames() {
        val tags = DesktopUiLanguage.preferredLanguages(
            mapOf("LANGUAGE" to "pt_BR:C", "LC_ALL" to "POSIX", "LANG" to "ca_ES.UTF-8@valencia")::get, english,
        )
        assertEquals(listOf("pt-BR", "ca-ES", "en-US"), tags)
    }
}
