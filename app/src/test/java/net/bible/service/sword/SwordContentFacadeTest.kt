package net.bible.service.sword

import net.bible.android.TestBibleApplication
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.ParseException
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import net.bible.test.DatabaseResetter

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.containsString
import org.hamcrest.core.IsNot.not
import org.junit.Assert.assertThat
import org.junit.Ignore
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[28])
class SwordContentFacadeTest {

    private lateinit var swordContentFacade: SwordContentFacade

    @Before
    @Throws(Exception::class)
    fun setUp() {
        swordContentFacade = SwordContentFacade(BookmarkFormatSupport(), MyNoteFormatSupport())
    }

    @After
    fun finishComponentTesting() {
        DatabaseResetter.resetDatabase()
    }

    @Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testReadFragment() {
        val esv = getBook("ESV2011")
        val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "John 11:35")

        val html = getHtml(esv, key, true)
        assertThat(html, not(containsString("<html")))
    }

    @Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testReadWordsOfChrist() {
        val esv = getBook("ESV2011")
        val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "Luke 15:4")

        val html = getHtml(esv, key, false)
        assertThat(html, containsString("“What <a href='gdef:05101' class='strongs'>5101</a>  man <a href='gdef:00444' class='strongs'>444</a>  of <a href='gdef:01537' class='strongs'>1537</a>  you <a href='gdef:05216' class='strongs'>5216</a> , having <a href='gdef:02192' class='strongs'>2192</a>  a hundred <a href='gdef:01540' class='strongs'>1540</a>  sheep"))
    }

    @Ignore("Until ESV comes back")
    @Test
    @Throws(Exception::class)
    fun testReadCanonicalText() {
        val esv = getBook("ESV2011")
        val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "Gen 1:1")

        val html = swordContentFacade.getCanonicalText(esv, key)
        assertThat("Wrong canonical text", html, equalTo("In the beginning, God created the heavens and the earth. "))
    }

    protected fun getVerse(book: Book, verseStr: String): Verse {
        val verse = book.getKey(verseStr) as RangedPassage
        return verse.getVerseAt(0)
    }

    @Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141a() {
        val esv = getBook("ESV2011")
        val key = PassageKeyFactory.instance().getKey((esv as SwordBook).versification, "Matt 18")

        val html = try {
            swordContentFacade.readHtmlTextOptimizedZTextOsis(esv, key, false, WorkspaceEntities.TextDisplaySettings.default)
        } catch (e: ParseException) {
            "broken"
        }
        assertThat(html, not(equalTo("broken")))
    }

    @Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141b_18_11() {
        val esv = getBook("ESV2011")

        val verse = getVerse(esv, "Matt.18.11")

        val html = try {
            swordContentFacade.readHtmlTextOptimizedZTextOsis(esv, verse, false, WorkspaceEntities.TextDisplaySettings.default)
        } catch (e: ParseException) {
            "broken"
        }
        assertThat(html, not(equalTo("broken")))
    }

    @Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141b() {
        val esv = getBook("ESV2011")

        for(i in 1..35) {
            val verse = getVerse(esv, "Matt.18.$i")

            val html = try {
                swordContentFacade.readHtmlTextOptimizedZTextOsis(esv, verse, false, WorkspaceEntities.TextDisplaySettings.default)
            } catch (e: ParseException) {
                "broken"
            }
            assertThat(html, not(equalTo("broken")))
        }
    }


    @Throws(Exception::class)
    private fun getHtml(book: Book, key: Key, asFragment: Boolean): String {
        return swordContentFacade.readHtmlText(book, key, asFragment, WorkspaceEntities.TextDisplaySettings.default)
    }

    private fun getBook(initials: String): Book {
        println("Looking for $initials")
        return Books.installed().getBook(initials)
    }
}
