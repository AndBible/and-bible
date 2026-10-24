package net.bible.android.view.activity.search

import android.app.Activity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.index.IndexStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchIntentsTest {
    private fun document(indexed: Boolean, epub: Boolean = false, general: Boolean = false): Book {
        val metadata = mock<BookMetaData>()
        whenever(metadata.getProperty("AndBibleEpubModule")).thenReturn(if (epub) "true" else null)
        return mock<Book>().also {
            whenever(it.bookMetaData).thenReturn(metadata)
            whenever(it.indexStatus).thenReturn(if (indexed) IndexStatus.DONE else IndexStatus.UNDONE)
            whenever(it.bookCategory).thenReturn(if (epub || general) BookCategory.GENERAL_BOOK else BookCategory.BIBLE)
            whenever(it.initials).thenReturn("Other Bible/+?")
        }
    }

    private fun assertRoute(document: Book?, expected: String) {
        val host = Robolectric.buildActivity(Activity::class.java).get()
        val intent = searchIntent(document, host)!!
        assertEquals(NavHostComposeActivity::class.java.name, intent.component!!.className)
        assertEquals(expected, intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        assertEquals(setOf(NavHostComposeActivity.EXTRA_ROUTE), intent.extras!!.keySet())
    }

    @Test fun `indexed EPUB opens EPUB search`() = assertRoute(document(true, epub = true), NavRoutes.EPUB_SEARCH)
    @Test fun `indexed Bible opens empty search form`() = assertRoute(document(true), NavRoutes.searchForm())
    @Test fun `unindexed Bible names the requested document in index route`() {
        val book = document(false)
        assertRoute(book, NavRoutes.searchIndex(searchDocument = book.initials))
    }
    @Test fun `null document opens argument free index route`() = assertRoute(null, NavRoutes.searchIndex(searchDocument = null))
    @Test fun `unindexed general book has no search intent`() {
        assertNull(searchIntent(document(false, general = true), Robolectric.buildActivity(Activity::class.java).get()))
    }
}
