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
package net.bible.android.view.activity.search

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.sword.SwordBook
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Test

class SearchDocumentFilterTest {
    private fun bible(hasStrongs: Boolean): SwordBook = mock {
        whenever(it.hasFeature(FeatureType.STRONGS_NUMBERS)).thenReturn(hasStrongs)
    }

    @Test
    fun strongsSearchKeepsOnlyStrongsEnabledBibles() {
        val withStrongs = bible(hasStrongs = true)
        val withoutStrongs = bible(hasStrongs = false)
        val result = candidateSearchDocuments(strongsSearch = true, allBibles = listOf(withStrongs, withoutStrongs))
        assertThat(result, equalTo(listOf(withStrongs)))
    }

    @Test
    fun nonStrongsSearchKeepsAllBibles() {
        val a = bible(hasStrongs = true)
        val b = bible(hasStrongs = false)
        val all = listOf(a, b)
        val result = candidateSearchDocuments(strongsSearch = false, allBibles = all)
        assertThat(result, equalTo(all))
    }
}
