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
package net.bible.sharedui.reading.host.slots

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.bible.sharedcore.search.BibleOption

/**
 * #4092: a commentary search names its document. The selector's candidates are Bibles only, so the chip used to be
 * blank for a commentary, and the count said "N verses in 1 translations".
 */
class SearchedDocumentsLabelTest {
    private val candidates = listOf(BibleOption("KJV", "KJV", true), BibleOption("BSB", "BSB", false))
    private val documents = mapOf("MHC" to "MHC", "KJV" to "KJV", "BSB" to "BSB", "TSK" to "TSK")
    private val bibles = setOf("KJV", "BSB")

    @Test fun aCommentaryIsNamedOnTheChip() {
        assertEquals("MHC", searchedDocumentsLabel(listOf("MHC"), candidates, documents::get))
    }

    @Test fun biblesKeepTheirCandidateAbbreviations() {
        assertEquals("KJV, BSB", searchedDocumentsLabel(listOf("KJV", "BSB"), candidates, documents::get))
    }

    @Test fun anUninstalledDocumentFallsBackToItsInitials() {
        assertEquals("Gone", searchedDocumentsLabel(listOf("Gone"), candidates) { null })
    }

    @Test fun aSingleNonBibleSearchIsCountedInItsDocument() {
        assertEquals("MHC", singleNonBibleSearched(listOf("MHC"), isBible = { it in bibles }, documents::get))
    }

    @Test fun bibleSearchesKeepTheTranslationCount() {
        assertNull(singleNonBibleSearched(listOf("KJV"), isBible = { it in bibles }, documents::get))
        assertNull(singleNonBibleSearched(listOf("KJV", "MHC"), isBible = { it in bibles }, documents::get))
        assertNull(singleNonBibleSearched(emptyList(), isBible = { it in bibles }, documents::get))
    }
}
