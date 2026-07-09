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

package net.bible.android.control.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.BibleTraverser
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CachedKeyPageTest {
    private fun createPageManager(): CurrentPageManager =
        CurrentPageManager(
            mock(BibleTraverser::class.java),
            mock(BookmarkControl::class.java),
            mock(WindowControl::class.java),
        )

    /**
     * Regression for OSTicket 3369: with no current document (or when building the
     * cached key list fails / the underlying document was deactivated concurrently),
     * cachedGlobalKeyList is null. getKeyPlus previously called findIndexOf, which did
     * `cachedGlobalKeyList!!.indexOf(...)` and crashed with an uncaught NPE on the
     * WebView handler thread. It must instead fall back to the current key.
     */
    @Test
    fun getKeyPlusWithNullKeyListReturnsCurrentKey() {
        val page = createPageManager().currentGeneralBook
        // No document set → cachedGlobalKeyList is null.
        val currentKey = DefaultLeafKeyList("some entry", "some-entry")

        assertThat(page.getKeyPlus(currentKey, 1), equalTo(currentKey as org.crosswire.jsword.passage.Key))
        assertThat(page.getKeyPlus(currentKey, -1), equalTo(currentKey as org.crosswire.jsword.passage.Key))
    }

    /** With a null key list and a null current key, getKeyPlus returns an empty leaf key (no crash). */
    @Test
    fun getKeyPlusWithNullKeyListAndNullKeyReturnsEmptyKey() {
        val page = createPageManager().currentDictionary

        val result = page.getKeyPlus(null, 1)
        assertThat(result, equalTo(DefaultLeafKeyList("") as org.crosswire.jsword.passage.Key))
    }
}
