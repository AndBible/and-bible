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
    fun aSetLocalePrefWins() = assertEquals("fi", IosUiLanguage.resolve("fi", listOf("de-DE"), supported))

    @Test
    fun anUnsupportedLocalePrefFallsThroughToThePreferredLanguages() =
        assertEquals("pt", IosUiLanguage.resolve("xx", listOf("pt-BR"), supported))

    @Test
    fun theFirstSupportedPreferredLanguageIsUsed() =
        assertEquals("fi", IosUiLanguage.resolve(null, listOf("de-DE", "fi-FI"), setOf("fi", "en")))

    @Test
    fun noMatchMeansEnglish() = assertEquals("en", IosUiLanguage.resolve(null, listOf("xx"), setOf("en")))

    @Test
    fun aBlankLocalePrefIsUnset() {
        assertEquals("fi", IosUiLanguage.resolve("", listOf("fi-FI"), supported))
        assertEquals("fi", IosUiLanguage.resolve("  ", listOf("fi-FI"), supported))
    }

    @Test
    fun scriptVariantsMatchTheirLongestSupportedPrefix() {
        assertEquals("sr-Latn", IosUiLanguage.resolve(null, listOf("sr-Latn-RS"), supported))
        assertEquals("zh-Hans", IosUiLanguage.resolve(null, listOf("zh-Hans-CN"), supported))
        assertEquals("pt", IosUiLanguage.resolve("pt_BR", emptyList(), supported))
    }

    @Test
    fun noPreferencesAtAllMeansEnglish() = assertEquals("en", IosUiLanguage.resolve(null, emptyList(), emptySet()))

    @Test
    fun theGeneratedTablesServeEveryAnswer() {
        val tables = iosStringTable("fi")
        assertTrue(tables.text("speak").isNotBlank())
    }
}
