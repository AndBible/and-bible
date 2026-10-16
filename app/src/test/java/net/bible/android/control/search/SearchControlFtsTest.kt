/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.search

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.index.search.SearchType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app's search paths over a real KJV on the FTS5 index (K6 task 16). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchControlFtsTest {
    private val kjv get() = Books.installed().getBook("KJV") as SwordBook
    private val control: SearchControl get() = GlobalContext.get().get()

    @Before fun index() { FtsTestSupport.ensureIndexed(kjv) }
    @After fun cleanUp() { FtsTestSupport.dropIndex(kjv) }

    private fun search(text: String, type: SearchType): List<String> = runBlocking {
        val decorated = control.decorateSearchString(text, type, SearchControl.SearchBibleSection.ALL, null)
        control.getMultiSearchResults(listOf("KJV"), decorated).mainSearchResults.map { it.normalizedVerse.getName() }
    }

    @Test fun allWordsFindsOnlyVersesWithEveryWord() {
        val hits = search("beginning created", SearchType.ALL_WORDS)
        assertTrue(hits.toString(), "Genesis 1:1" in hits)
        assertTrue(hits.size < search("beginning created", SearchType.ANY_WORDS).size)
    }

    @Test fun anyWordsIsTheUnionOfTheWords() {
        val any = search("reshith beginning", SearchType.ANY_WORDS)
        assertTrue("Genesis 1:1" in any)
        assertTrue(any.size >= search("beginning", SearchType.ANY_WORDS).size)
    }

    @Test fun phraseMatchesAdjacentWordsOnly() {
        assertTrue("Genesis 1:1" in search("In the beginning", SearchType.PHRASE))
        assertTrue(search("beginning the In", SearchType.PHRASE).isEmpty())
    }

    @Test fun strongsNumberSearchFindsTheVerse() {
        val hits = runBlocking { control.getMultiSearchResults(listOf("KJV"), "strong:h7225").mainSearchResults }
        assertTrue(hits.map { it.normalizedVerse.getName() }.toString(), hits.any { it.normalizedVerse.getName() == "Genesis 1:1" })
    }

    /** The probe `LinkControl` runs: a verse range plus a Strong's number must not fail and must find the verse. */
    @Test fun linkControlProbeIsNotEmpty() {
        val key = SwordContentFacade.search(kjv, "+[Gen 1:1] strong:h7225")
        assertEquals(1, key.getCardinality())
    }

    @Test fun sectionTermRestrictsTheSearch() {
        val nt = runBlocking {
            val q = control.decorateSearchString("beginning", SearchType.ALL_WORDS, SearchControl.SearchBibleSection.NT, null)
            control.getMultiSearchResults(listOf("KJV"), q).mainSearchResults
        }
        assertTrue(nt.isNotEmpty())
        assertTrue(nt.none { it.normalizedVerse.getName().startsWith("Genesis") })
    }
}
