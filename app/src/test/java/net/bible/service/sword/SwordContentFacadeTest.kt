package net.bible.service.sword

import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.service.common.ParseException
import net.bible.test.DatabaseResetter

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.containsString
import org.hamcrest.core.IsNot.not
import org.junit.Assert.assertThat
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[28])
class SwordContentFacadeTest {

    private lateinit var swordContentFacade: SwordContentFacade

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val activeWindowPageManagerProvider = Mockito.mock(ActiveWindowPageManagerProvider::class.java)
        val windowControl = Mockito.mock(WindowControl::class.java)
        val bookmarkControl = BookmarkControl(windowControl, Mockito.mock(AndroidResourceProvider::class.java))
        swordContentFacade = SwordContentFacade(activeWindowPageManagerProvider)
    }

    @After
    fun finishComponentTesting() {
        DatabaseResetter.resetDatabase()
    }

    //@Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testReadFragment() {
        val esv = getBook("ESV2011")
        //val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "John 11:35")
        val key = VerseRangeFactory.fromString((esv as SwordBook).versification, "John 11:35")
        val html = getHtml(esv, key)
        assertThat(html, not(containsString("<html")))
    }

    //@Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testReadCanonicalText() {
        val esv = getBook("ESV2011")
        //val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "Gen 1:1")
        val key = VerseRangeFactory.fromString((esv as SwordBook).versification, "Gen 1:1")

        val html = swordContentFacade.getCanonicalText(esv, key)
        assertThat("Wrong canonical text", html, equalTo("In the beginning, God created the heavens and the earth. "))
    }

    protected fun getVerse(book: Book, verseStr: String): VerseRange {
        val key = VerseRangeFactory.fromString((book as SwordBook).versification, verseStr)
        //val verse = book.getKey(verseStr) as RangedPassage
        return key
    }

    //@Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141a() {
        val esv = getBook("ESV2011")
        //val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "Matt 18")
        val key = VerseRangeFactory.fromString((esv as SwordBook).versification, "Matt 18")

        val html = try {
            swordContentFacade.readOsisFragment(esv, key)
        } catch (e: ParseException) {
            "broken"
        }
        assertThat(html, not(equalTo("broken")))
    }

    //@Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141b_18_11() {
        val esv = getBook("ESV2011")

        val verse = getVerse(esv, "Matt.18.11")

        val html = try {
            swordContentFacade.readOsisFragment(esv, verse)
        } catch (e: ParseException) {
            "broken"
        }
        assertThat(html, not(equalTo("broken")))
    }

    //@Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141b() {
        val esv = getBook("ESV2011")

        for(i in 1..35) {
            val verse = getVerse(esv, "Matt.18.$i")

            val html = try {
                swordContentFacade.readOsisFragment(esv, verse)
            } catch (e: ParseException) {
                "broken"
            }
            assertThat(html, not(equalTo("broken")))
        }
    }


    @Throws(Exception::class)
    private fun getHtml(book: Book, key: Key): String {
        return swordContentFacade.readOsisFragment(book, key)
    }

    private fun getBook(initials: String): Book {
        println("Looking for $initials")
        return Books.installed().getBook(initials)
    }
}
