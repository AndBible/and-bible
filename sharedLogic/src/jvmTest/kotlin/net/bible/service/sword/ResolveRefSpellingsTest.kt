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

package net.bible.service.sword

import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.jsword.versification.BibleNames
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The reference spellings typed in Find (#4089): [SwordContentFacade.resolveRef] decides whether a query jumps to a
 * passage or runs as a word search. English UI, an English KJV document.
 */
class ResolveRefSpellingsTest {
    private val kjv get() = checkNotNull(Versifications.instance().getVersification("KJV"))
    private lateinit var previousProvider: LocaleProvider

    @BeforeTest fun englishUi() {
        previousProvider = LocaleProviderManager.getLocaleProvider()
        LocaleProviderManager.setLocaleProvider(object : LocaleProvider { override fun getUserLocale() = KLocale.ENGLISH })
    }

    @AfterTest fun restore() {
        LocaleProviderManager.setLocaleProvider(previousProvider)
    }

    private fun resolve(query: String) = SwordContentFacade.resolveRef(query, "en", kjv)?.getOsisRef()

    @Test fun referenceSpellingsJumpToThePassage() {
        val expected = mapOf(
            // #4089's table, every row
            "1 jn 3 16" to "1John.3.16",
            "1 john 3:16" to "1John.3.16",
            "1john 3 16" to "1John.3.16",
            "1 joh 3 16" to "1John.3.16",
            "1 john 3 16" to "1John.3.16",
            "john 3 16" to "John.3.16",
            "1 jo 3 16" to "1John.3.16",
            // variants of the abbreviation that was missing ("Joh" is John's short name, so "1 Joh" is 1 John)
            "1joh 3 16" to "1John.3.16",
            "1 Joh 3:16" to "1John.3.16",
            "2 joh 1 5" to "2John.1.5",
            "3 joh 1:4" to "3John.1.4",
            // other abbreviated book names with chapter and verse
            "phili 4 13" to "Phil.4.13",
            "revel 3:20" to "Rev.3.20",
            // plain cases that already worked
            "John 3:16" to "John.3.16",
            "jn 3 16" to "John.3.16",
            "Gen 1:1" to "Gen.1.1",
            "Joh 3:16" to "John.3.16",
            "Ps 23" to "Ps.23",
        )
        val actual = expected.keys.associateWith { resolve(it) }
        assertEquals(expected, actual)
    }

    @Test fun wordsStayWordSearches() {
        // #2380: words must not open a book. A single book name (#4090) and a book with only a chapter are untouched.
        for (q in listOf("love", "grace", "faith hope", "beginning", "Job", "joh", "light 3", "love 3 16", "so loved 3 16")) {
            assertNull(resolve(q), q)
        }
    }

    @Test fun theFuzzyLookupIsRestored() {
        resolve("1 joh 3 16")
        assertEquals(true, BibleNames.instance().enableFuzzy)
    }
}
