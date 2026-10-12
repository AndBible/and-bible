package net.bible.android.control.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DefaultDocumentsTest {
    @Test fun firstInstalledBibleAndNewTestamentDefaultRemainUnchanged() {
        val first = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }[0]
        assertEquals(first, defaultBible())
        assertEquals("John.3.16", defaultVerse().start.getOsisID())
        assertEquals("John.3.18", defaultVerse().end.getOsisID())
    }

    @Test fun noInstalledBibleStillFailsAtFirstElement() {
        val books = Books.installed()
        val bibles = books.getBooks { it.bookCategory == BookCategory.BIBLE }.toList()
        try {
            bibles.forEach { books.removeBook(it) }
            assertThrows(IndexOutOfBoundsException::class.java) { defaultBible() }
            assertThrows(IndexOutOfBoundsException::class.java) { defaultVerse() }
        } finally {
            bibles.forEach { books.addBook(it) }
        }
    }
}
