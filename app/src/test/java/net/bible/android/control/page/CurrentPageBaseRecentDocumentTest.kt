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
import net.bible.service.common.CommonUtils
import net.bible.service.common.RecentDocumentsStore
import org.crosswire.jsword.book.Books
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CurrentPageBaseRecentDocumentTest {
    private fun createPageManager(): CurrentPageManager =
        CurrentPageManager(
            mock(BibleTraverser::class.java),
            mock(BookmarkControl::class.java),
            mock(WindowControl::class.java),
        )

    /**
     * CurrentPageBase.setCurrentDocument is the one leaf every document-switch route funnels
     * through (CurrentPageManager.setCurrentDocument and setCurrentDocumentAndKey both land
     * here). It must record the switch for round 15b's document quick sheet Recent tab.
     */
    @Test
    fun settingTheCurrentDocumentRecordsItAsRecent() {
        CommonUtils.settings.setString(RecentDocumentsStore.KEY, null)
        val page = createPageManager().currentBible
        val someInstalledBook = Books.installed().getBook("KJV")

        page.setCurrentDocument(someInstalledBook)

        assertEquals(listOf(someInstalledBook.initials), RecentDocumentsStore.read())
    }
}
