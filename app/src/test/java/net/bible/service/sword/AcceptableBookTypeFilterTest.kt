package net.bible.service.sword

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookMetaData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptableBookTypeFilterTest {
    private fun addon(required: String, category: BookCategory = BookCategory.AND_BIBLE): Book {
        val metadata = mock<BookMetaData> { on { getProperty("AndBibleMinimumVersion") } doReturn required }
        return mock {
            on { bookCategory } doReturn category
            on { bookMetaData } doReturn metadata
        }
    }

    @Test fun minimumVersionIsInclusiveAndReadAtUseTime() {
        val required = Int.MAX_VALUE.toLong() + 1
        var version = required - 1
        val filter = AndBibleAddonFilter { version }
        val book = addon(required.toString())
        assertFalse(filter.test(book))
        version = required
        assertTrue(filter.test(book))
        version++
        assertTrue(filter.test(book))
        assertFalse(filter.test(addon("0", BookCategory.BIBLE)))
    }
}
