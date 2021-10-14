package net.bible.android.control.download

import net.bible.service.download.FakeBookFactory.createFakeRepoBook
import org.crosswire.common.util.Language
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class RelevantLanguageSorterTest {
    private var relevantLanguageSorter: RelevantLanguageSorter? = null
    private var originalLocale: Locale? = null
    @Before
    @Throws(Exception::class)
    fun createErrorReportControl() {
        originalLocale = Locale.getDefault()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        // the test changes the Locale so ensure the correct default locale is restored
        Locale.setDefault(originalLocale)
    }

    @Test
    @Throws(Exception::class)
    fun testCompare() {
        Locale.setDefault(Locale.KOREAN)
        val svInstalledLang = Language("sv")
        val svInstalledBook = createFakeRepoBook("DEF", "Lang=sv", null)
        val books: MutableList<Book> = ArrayList()
        books.add(svInstalledBook)
        relevantLanguageSorter = RelevantLanguageSorter(books)
        val frPopularLang = Language("fr")
        val koDefaultLang = Language("ko")
        val inNotRelevantLang = Language("in")
        val fiNotRelevantLang = Language("fi")

        // both relevant: installed book and major language
        Assert.assertThat(relevantLanguageSorter!!.compare(svInstalledLang, frPopularLang), Matchers.greaterThan(0))
        Assert.assertThat(relevantLanguageSorter!!.compare(frPopularLang, svInstalledLang), Matchers.lessThan(0))

        // both relevant: default language
        Assert.assertThat(relevantLanguageSorter!!.compare(koDefaultLang, frPopularLang), Matchers.greaterThan(0))
        Assert.assertThat(relevantLanguageSorter!!.compare(frPopularLang, koDefaultLang), Matchers.lessThan(0))

        // One relevant
        Assert.assertThat(relevantLanguageSorter!!.compare(koDefaultLang, inNotRelevantLang), Matchers.lessThan(0))

        // Neither relevant
        Assert.assertThat(relevantLanguageSorter!!.compare(fiNotRelevantLang, inNotRelevantLang), Matchers.lessThan(0))
        Assert.assertThat(relevantLanguageSorter!!.compare(inNotRelevantLang, fiNotRelevantLang), Matchers.greaterThan(0))
    }
}
