/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.AbstractSpeakTests
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertThat
import java.util.Date
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider

/**
 * Tests for KeyHistoryItem, specifically the range description formatting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class KeyHistoryItemTest {

    private val kjvV11n = Versifications.instance().getVersification("KJV")
    private lateinit var window: Window
    private var windowControl: WindowControl? = null
    private val windowRepository: WindowRepository get() = windowControl!!.windowRepository

    @Before
    fun setUp() {
        val bibleTraverser = mock(BibleTraverser::class.java)
        val bookmarkControl = BookmarkControl(AbstractSpeakTests.windowControl, mock(AndroidResourceProvider::class.java))
        val mockCurrentPageManagerProvider = Provider {
            CurrentPageManager(bibleTraverser, bookmarkControl, windowControl!!)
        }
        windowControl = CommonUtils.windowControl
        windowControl!!.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.settings.setBoolean("first-time", false)
        windowRepository.initialize()

        val pageManager = mockCurrentPageManagerProvider.get()
        window = Window(
            WorkspaceEntities.Window(
                workspaceId = IdType(),
                isSynchronized = false,
                isPinMode = false,
                isLinksWindow = false,
                windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.toString()),
            ),
            pageManager,
            windowRepository
        )
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun testSameChapterRangeFormat() {
        // Test same chapter range: "Matt 5:1–48"
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 5, 1)
        val endVerse = Verse(kjvV11n, BibleBook.MATT, 5, 48)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            endKey = endVerse
        )

        val description = historyItem.description
        // Should contain the range format with en-dash
        assertThat(description, containsString("Matt 5:1"))
        assertThat(description, containsString("–48"))
    }

    @Test
    fun testCrossChapterRangeFormat() {
        // Test cross-chapter range: "Matt 5:1–6:2"
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 5, 1)
        val endVerse = Verse(kjvV11n, BibleBook.MATT, 6, 2)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            endKey = endVerse
        )

        val description = historyItem.description
        // Should contain both chapter references
        assertThat(description, containsString("Matt 5:1"))
        assertThat(description, containsString("–"))
        assertThat(description, containsString("6:2"))
    }

    @Test
    fun testCrossBookRangeFormat() {
        // Test cross-book range
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 28, 20)
        val endVerse = Verse(kjvV11n, BibleBook.MARK, 1, 5)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            endKey = endVerse
        )

        val description = historyItem.description
        // Should contain both book references
        assertThat(description, containsString("Matt"))
        assertThat(description, containsString("–"))
        assertThat(description, containsString("Mark"))
    }

    @Test
    fun testNoRangeWhenEndKeyMissing() {
        // Test fallback to single verse when endKey is null
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 5, 1)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            endKey = null
        )

        val description = historyItem.description
        // Should contain start verse but no range indicator
        assertThat(description, containsString("Matt 5:1"))
        assertThat(description, not(containsString("–")))
    }

    @Test
    fun testNoRangeWhenEndKeyEqualsStartKey() {
        // Test fallback to single verse when endKey equals startKey
        val verse = Verse(kjvV11n, BibleBook.MATT, 5, 1)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = verse,
            anchorOrdinal = null,
            window = window,
            endKey = verse
        )

        val description = historyItem.description
        // Should contain start verse but no range
        assertThat(description, containsString("Matt 5:1"))
        assertThat(description, not(containsString("–")))
    }

    @Test
    fun testNoRangeWhenEndKeyBeforeStartKey() {
        // Test fallback to single verse when endKey is before startKey
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 5, 10)
        val endVerse = Verse(kjvV11n, BibleBook.MATT, 5, 5)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            endKey = endVerse
        )

        val description = historyItem.description
        // Should contain start verse but no range (end is before start)
        assertThat(description, containsString("Matt 5:10"))
        assertThat(description, not(containsString("–")))
    }

    @Test
    fun testDescriptionIncludesDocumentAbbreviation() {
        val verse = Verse(kjvV11n, BibleBook.PS, 23, 1)

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = verse,
            anchorOrdinal = null,
            window = window,
            endKey = null
        )

        val description = historyItem.description
        // Should include the document abbreviation
        assertThat(description, containsString("ESV"))
    }

    @Test
    fun testCreatedAtPreservedWhenExplicitlyProvided() {
        // Test that createdAt timestamp is preserved when explicitly provided
        // This is important for the endKey update scenario in HistoryManager.add()
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 5, 1)
        val originalTimestamp = Date(1000000L) // Fixed timestamp in the past

        val historyItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            createdAt = originalTimestamp,
            endKey = null
        )

        assertThat(historyItem.createdAt, equalTo(originalTimestamp))
    }

    @Test
    fun testCreatedAtPreservedWhenCopyingWithNewEndKey() {
        // Simulates the HistoryManager.add() scenario: when user scrolls further,
        // we create a new item with the original timestamp but updated endKey
        val startVerse = Verse(kjvV11n, BibleBook.MATT, 5, 1)
        val endVerse = Verse(kjvV11n, BibleBook.MATT, 5, 48)
        val originalTimestamp = Date(1000000L)

        // Original item (simulating existing history entry)
        val originalItem = KeyHistoryItem(
            document = ESV,
            key = startVerse,
            anchorOrdinal = null,
            window = window,
            createdAt = originalTimestamp,
            endKey = null
        )

        // Create updated item preserving original timestamp (as done in HistoryManager.add())
        val updatedItem = KeyHistoryItem(
            document = originalItem.document,
            key = originalItem.key,
            anchorOrdinal = originalItem.anchorOrdinal,
            window = originalItem.window,
            createdAt = originalItem.createdAt,
            endKey = endVerse
        )

        // Verify timestamp is preserved and endKey is updated
        assertThat(updatedItem.createdAt, equalTo(originalTimestamp))
        assertThat(updatedItem.endKey, equalTo(endVerse))
        assertThat(updatedItem.key, equalTo(startVerse))
    }

    companion object {
        private val ESV: Book = Books.installed().getBook("ESV2011")
    }
}
