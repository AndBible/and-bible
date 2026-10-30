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
package net.bible.sharedui.assembly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosUiLanguageTest {
    private val supported = setOf("en", "fi", "pt", "sr-Latn", "zh-Hans")

    @Test
    fun aSetLocalePrefWins() = assertEquals("fi", HostUiLanguage.resolve("fi", listOf("de-DE"), supported))

    @Test
    fun anUnsupportedLocalePrefFallsThroughToThePreferredLanguages() =
        assertEquals("pt", HostUiLanguage.resolve("xx", listOf("pt-BR"), supported))

    @Test
    fun theFirstSupportedPreferredLanguageIsUsed() =
        assertEquals("fi", HostUiLanguage.resolve(null, listOf("de-DE", "fi-FI"), setOf("fi", "en")))

    @Test
    fun noMatchMeansEnglish() = assertEquals("en", HostUiLanguage.resolve(null, listOf("xx"), setOf("en")))

    @Test
    fun aBlankLocalePrefIsUnset() {
        assertEquals("fi", HostUiLanguage.resolve("", listOf("fi-FI"), supported))
        assertEquals("fi", HostUiLanguage.resolve("  ", listOf("fi-FI"), supported))
    }

    @Test
    fun scriptVariantsMatchTheirLongestSupportedPrefix() {
        assertEquals("sr-Latn", HostUiLanguage.resolve(null, listOf("sr-Latn-RS"), supported))
        assertEquals("zh-Hans", HostUiLanguage.resolve(null, listOf("zh-Hans-CN"), supported))
        assertEquals("pt", HostUiLanguage.resolve("pt_BR", emptyList(), supported))
    }

    @Test
    fun matchingIgnoresCaseAndAnswersTheSupportedSpelling() {
        assertEquals("sr-Latn", HostUiLanguage.resolve(null, listOf("sr-latn-RS"), supported))
        assertEquals("zh-Hans", HostUiLanguage.resolve("ZH-hans", emptyList(), supported))
        assertEquals("fi", HostUiLanguage.resolve(null, listOf("FI"), supported))
    }

    @Test
    fun noPreferencesAtAllMeansEnglish() = assertEquals("en", HostUiLanguage.resolve(null, emptyList(), emptySet()))

    @Test
    fun theFallbackLanguageHasGeneratedTables() {
        val en = hostStringTable(HostUiLanguage.resolve(null, emptyList(), emptySet()))
        assertTrue(en.text("speak") != "speak" && en.text("speak").isNotBlank())
        assertTrue(hostStringTable("fi").text("speak") != en.text("speak"))
    }

    @Test
    fun rtlLanguagesAreDetectedFromTheResolvedTag() {
        for (tag in listOf("he", "iw", "ar", "ur", "fa", "he-IL", "ar_EG", "HE")) assertTrue(HostUiLanguage.isRtl(tag), tag)
        for (tag in listOf("en", "fi", "pt-BR", "zh-Hans", "sr-Latn", "")) assertTrue(!HostUiLanguage.isRtl(tag), tag)
    }

    private class FakePrefs(private val values: Map<String, String>) : net.bible.sharedcore.settings.DeviceLocalPrefs {
        override fun getString(key: String, default: String?) = values[key] ?: default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun setString(key: String, value: String) = Unit
        override fun setBoolean(key: String, value: Boolean) = Unit
        override fun remove(key: String) = Unit
    }

    @Test
    fun deviceResolutionReadsLocalePrefThenTheInjectedDeviceLanguages() {
        val real = net.bible.sharedui.strings.StringsData.data.keys
        assertTrue("fi" in real && "en" in real)
        assertEquals("fi", resolveDeviceUiLanguage(FakePrefs(mapOf("locale_pref" to "fi")), { listOf("en-US") }))
        assertEquals("fi", resolveDeviceUiLanguage(FakePrefs(emptyMap()), { listOf("xx-XX", "fi-FI") }))
        assertEquals("fi", resolveDeviceUiLanguage(FakePrefs(mapOf("locale_pref" to " ")), { listOf("fi-FI") }))
        assertEquals("en", resolveDeviceUiLanguage(FakePrefs(emptyMap()), { emptyList() }))
    }
}
