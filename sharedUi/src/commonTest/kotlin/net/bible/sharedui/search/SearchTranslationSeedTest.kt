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
package net.bible.sharedui.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTranslationSeedTest {
    private val installed = setOf("KJV", "ESV", "NASB")

    @Test
    fun bibleSearchStartsFromTheSavedSelection() {
        assertEquals(listOf("ESV", "KJV"), seedSearchTranslations("NASB", isBible = true, saved = "ESV,KJV", installed))
    }

    @Test
    fun commentarySearchIgnoresTheSavedSelection() {
        assertEquals(listOf("MHC"), seedSearchTranslations("MHC", isBible = false, saved = "ESV,KJV", installed))
    }

    @Test
    fun staleInitialsAreDropped() {
        assertEquals(listOf("KJV", "ESV"), seedSearchTranslations("NASB", isBible = true, saved = "KJV,GONE,ESV", installed))
    }

    @Test
    fun onlyStaleOrNoSavedInitialsFallBackToTheCurrentDocument() {
        assertEquals(listOf("NASB"), seedSearchTranslations("NASB", isBible = true, saved = "GONE", installed))
        assertEquals(listOf("NASB"), seedSearchTranslations("NASB", isBible = true, saved = null, installed))
        assertEquals(listOf("NASB"), seedSearchTranslations("NASB", isBible = true, saved = "  ", installed))
    }

    @Test
    fun noCurrentDocumentAndNothingSavedSeedsNothing() {
        assertEquals(emptyList<String>(), seedSearchTranslations(null, isBible = false, saved = "KJV", installed))
        assertEquals(emptyList<String>(), seedSearchTranslations(null, isBible = true, saved = null, installed))
    }

    @Test
    fun parsesTheCommaJoinedEncoding() {
        assertEquals(listOf("KJV", "ESV"), parseSavedSearchTranslations("KJV,GONE,ESV", installed))
        assertEquals(emptyList<String>(), parseSavedSearchTranslations("", installed))
    }
}
