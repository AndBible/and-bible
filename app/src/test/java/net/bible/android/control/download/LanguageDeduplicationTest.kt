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

class LanguageDeduplicationTest {

    @Test
    fun `iso639-3 and iso639-1 codes for the same language share a key`() {
        // The bug: eBible/IBT use three-letter codes, CrossWire uses two-letter codes
        assertEquals(LanguageDeduplication.canonicalKey(Language("en")), LanguageDeduplication.canonicalKey(Language("eng")))
        assertEquals(LanguageDeduplication.canonicalKey(Language("fi")), LanguageDeduplication.canonicalKey(Language("fin")))
        assertEquals(LanguageDeduplication.canonicalKey(Language("de")), LanguageDeduplication.canonicalKey(Language("deu")))
    }

    @Test
    fun `country qualifier does not create a distinct key`() {
        assertEquals(LanguageDeduplication.canonicalKey(Language("en")), LanguageDeduplication.canonicalKey(Language("en-US")))
        assertEquals(LanguageDeduplication.canonicalKey(Language("fi")), LanguageDeduplication.canonicalKey(Language("fi-FI")))
    }

    @Test
    fun `different scripts remain distinct`() {
        // Simplified vs Traditional Chinese are genuinely different and must NOT be merged
        assertTrue(
            LanguageDeduplication.canonicalKey(Language("zh-Hans")) != LanguageDeduplication.canonicalKey(Language("zh-Hant"))
        )
    }

    @Test
    fun `different languages have different keys`() {
        assertTrue(LanguageDeduplication.canonicalKey(Language("en")) != LanguageDeduplication.canonicalKey(Language("fi")))
    }

    @Test
    fun `three-letter code without a two-letter equivalent is kept`() {
        // grc (Ancient Greek) has no ISO 639-1 code, so it must survive unchanged
        assertEquals("grc", LanguageDeduplication.canonicalKey(Language("grc")))
    }

    @Test
    fun `null language has a null key`() {
        assertNull(LanguageDeduplication.canonicalKey(null))
    }

    @Test
    fun `deduplicate collapses code-standard duplicates and prefers two-letter representative`() {
        val result = LanguageDeduplication.deduplicate(listOf(Language("eng"), Language("en"), Language("fin")))
        assertEquals(2, result.size)
        val codes = result.map { it.code }.toSet()
        // English collapsed to the two-letter representative; Finnish has only "fin" so it survives as-is
        assertTrue("en" in codes)
        assertTrue("fin" in codes)
        assertTrue("eng" !in codes)
    }

    @Test
    fun `deduplicate keeps genuinely distinct languages`() {
        val result = LanguageDeduplication.deduplicate(listOf(Language("en"), Language("fi"), Language("de")))
        assertEquals(3, result.size)
    }

    @Test
    fun `deduplicate is deterministic regardless of input order`() {
        val a = LanguageDeduplication.deduplicate(listOf(Language("eng"), Language("en")))
        val b = LanguageDeduplication.deduplicate(listOf(Language("en"), Language("eng")))
        assertEquals(a.map { it.code }, b.map { it.code })
    }
}
