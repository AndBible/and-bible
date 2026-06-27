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
import net.bible.service.sword.BookAndKey
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CurrentPageManagerTest {
    private fun createPageManager(): CurrentPageManager =
        CurrentPageManager(
            mock(BibleTraverser::class.java),
            mock(BookmarkControl::class.java),
            mock(WindowControl::class.java),
        )

    private val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 139, 2)

    /**
     * A non-specific Bible link (book == null) must route to the Bible page whether the
     * verse arrives as a raw VerseKey (e.g. open_ref) or wrapped in a BookAndKey with a
     * null document (e.g. a Bible cross reference opened from an EPUB).
     *
     * Regression: the wrapped form previously returned null, so clicking a Bible link did
     * nothing when a non-Bible document (e.g. an EPUB) was open in the links window.
     */
    @Test
    fun getBookPageRoutesNullDocumentVerseLinkToBible() {
        val pageManager = createPageManager()

        assertThat(pageManager.getBookPage(null, verse), equalTo(pageManager.currentBible as CurrentPage))
        assertThat(
            pageManager.getBookPage(null, BookAndKey(verse)),
            equalTo(pageManager.currentBible as CurrentPage)
        )
    }

    /** A null book with a non-verse key (and no wrapped verse) still has no target page. */
    @Test
    fun getBookPageReturnsNullForNonVerseLinkWithoutDocument() {
        val pageManager = createPageManager()
        assertThat(pageManager.getBookPage(null, null), nullValue())
    }
}
