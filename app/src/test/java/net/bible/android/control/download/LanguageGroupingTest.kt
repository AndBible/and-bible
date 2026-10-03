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

package net.bible.android.control.download

import org.crosswire.common.util.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageGroupingTest {

    private fun grouping(vararg specs: String) = LanguageGrouping(specs.map { Language(it) })

    @Test
    fun `redundant default script is merged with the bare code`() {
        // The actual bug: repos declare some modules as "en" and others as "en-Latn",
        // both rendering "English" but not equal under Language.equals()
        val g = grouping("en", "en-Latn")
        assertEquals(g.key(Language("en")), g.key(Language("en-Latn")))
        assertEquals(1, g.representatives.size)
        assertEquals("en", g.representatives.single().code)
    }

    @Test
    fun `script-qualified variants with redundant script are merged across many languages`() {
        // Real cases observed: fi-Latn, he-Hebr, ru-Cyrl, ar-Arab all duplicate the bare code
        val g = grouping(
            "fi", "fi-Latn", "he", "he-Hebr", "ru", "ru-Cyrl", "ar", "ar-Arab",
        )
        assertEquals(4, g.representatives.size)
        assertEquals(g.key(Language("fi")), g.key(Language("fi-Latn")))
        assertEquals(g.key(Language("he")), g.key(Language("he-Hebr")))
        assertEquals(g.key(Language("ru")), g.key(Language("ru-Cyrl")))
        assertEquals(g.key(Language("ar")), g.key(Language("ar-Arab")))
    }

    @Test
    fun `three-letter code is merged with the two-letter code and script`() {
        // ben (ISO 639-3) + Beng script must merge with bn (ISO 639-1)
        val g = grouping("bn", "ben")
        assertEquals(g.key(Language("bn")), g.key(Language("ben")))
        assertEquals(1, g.representatives.size)
        assertEquals("bn", g.representatives.single().code)
    }

    @Test
    fun `country qualifier is merged`() {
        val g = grouping("en", "en-US", "fi", "fi-FI")
        assertEquals(2, g.representatives.size)
        assertEquals(g.key(Language("en")), g.key(Language("en-US")))
        assertEquals(g.key(Language("fi")), g.key(Language("fi-FI")))
    }

    @Test
    fun `significant scripts are kept distinct when the language has more than one script`() {
        // zh appears as zh (中文), zh-Hans (简体中文) and zh-Hant (繁体中文): the script IS meaningful
        val g = grouping("zh", "zh-Hans", "zh-Hant")
        assertTrue(g.key(Language("zh-Hans")) != g.key(Language("zh-Hant")))
        assertTrue(g.key(Language("zh")) != g.key(Language("zh-Hant")))
        assertEquals(3, g.representatives.size)
    }

    @Test
    fun `same-name script pair is merged even though scripts differ`() {
        // Gagauz in Latin and Cyrillic both render "Gagauz": the script is invisible to the
        // user, so they must collapse into a single entry
        val latn = Language("gag-Latn")
        val cyrl = Language("gag-Cyrl")
        // precondition: JSword renders both with the same name
        assertEquals(latn.name, cyrl.name)
        val g = LanguageGrouping(listOf(latn, cyrl))
        assertEquals(g.key(latn), g.key(cyrl))
        assertEquals(1, g.representatives.size)
    }

    @Test
    fun `country-only differences do not make a script significant`() {
        // zh-Hans-CN and zh-Hans (no country) must collapse to a single Simplified entry
        val g = grouping("zh-Hans-CN", "zh-Hans", "zh-Hant-HK", "zh-Hant")
        assertEquals(2, g.representatives.size)
    }

    @Test
    fun `different languages have different keys`() {
        val g = grouping("en", "fi", "de")
        assertEquals(3, g.representatives.size)
    }

    @Test
    fun `code without a two-letter equivalent is kept as-is`() {
        // grc (Ancient Greek) and lzh (Literary Chinese) have no ISO 639-1 code
        val g = grouping("grc", "lzh")
        assertEquals("grc", g.key(Language("grc")))
        assertEquals("lzh", g.key(Language("lzh")))
        assertEquals(2, g.representatives.size)
    }

    @Test
    fun `null language has a null key`() {
        assertNull(grouping("en").key(null))
    }

    @Test
    fun `representatives are deterministic regardless of input order`() {
        val a = grouping("en-Latn", "en").representatives.map { it.code to it.script }
        val b = grouping("en", "en-Latn").representatives.map { it.code to it.script }
        assertEquals(a, b)
    }
}
