package net.bible.service.sword

import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.misc.elementToString
import net.bible.android.view.activity.page.Selection
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

    //@Before
    //@Throws(Exception::class)
    //fun setUp() {
    //    val activeWindowPageManagerProvider = Mockito.mock(ActiveWindowPageManagerProvider::class.java)
    //    val windowControl = Mockito.mock(WindowControl::class.java)
    //    val bookmarkControl = BookmarkControl(windowControl, Mockito.mock(AndroidResourceProvider::class.java))
    //}

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

        val html = SwordContentFacade.getCanonicalText(esv, key)
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
            SwordContentFacade.readOsisFragment(esv, key)
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
            SwordContentFacade.readOsisFragment(esv, verse)
        } catch (e: Exception) {
            if(e is OsisError) "fixed" else "broken"
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
                SwordContentFacade.readOsisFragment(esv, verse)
            } catch (e: Exception) {
                if(e is OsisError) "fixed" else "broken"
            }
            assertThat(html, not(equalTo("broken")))
        }
    }


    @Throws(Exception::class)
    private fun getHtml(book: Book, key: Key): String {
        return elementToString(SwordContentFacade.readOsisFragment(book, key))
    }

    private fun getBook(initials: String): Book {
        println("Looking for $initials")
        return Books.installed().getBook(initials)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[28])
class TestShare {
    private fun testShare(initials: String, verseRangeStr: String, offsetRange: IntRange,
                          showVerseNumbers: Boolean, showFull: Boolean,
                          compareText: String,
    ) {

        val book = Books.installed().getBook(initials) as SwordBook
        val v11n = book.versification
        val verseRange = VerseRangeFactory.fromString(v11n, verseRangeStr)


        val sel = Selection(initials,
            verseRange.start.ordinal,
            offsetRange.first,
            verseRange.end.ordinal,
            offsetRange.last,
            emptyList())

        val text = SwordContentFacade.getSelectionText(sel,
            showVerseNumbers = showVerseNumbers,
            showSelectionOnly = !showFull,
            showReference = true,
            advertiseApp = false,
            showReferenceAtFront = false
        )

        assertThat(text, equalTo(compareText))
    }

    @Test
    fun testShare1a()  =
        testShare("ESV2011", "Ps.83.1", 7..30, true, false,
            "“...do not keep silence; do...” (Psa 83:1, ESV2011)"
        )

    @Test
    fun testShare2a()  =
        testShare("ESV2011", "Ps.83.1", 7..30, false, false,
            "“...do not keep silence; do...” (Psa 83:1, ESV2011)"
        )

    @Test
    fun testShare3a()  =
        testShare("ESV2011", "Ps.83.1", 7..30, true, true,
            "“O God, do not keep silence; do not hold your peace or be still, O God!” (Psa 83:1, ESV2011)"
        )

    @Test
    fun testShare4a()  =
        testShare("ESV2011", "Ps.83.1", 7..30, false, true,
            "“O God, do not keep silence; do not hold your peace or be still, O God!” (Psa 83:1, ESV2011)"
        )


    @Test
    fun testShare1()  =
        testShare("ESV2011", "Ps.83.1-Ps.83.2", 7..30, true, false,
            "“1. ...do not keep silence; do not hold your peace or be still, O God! 2. For behold, " +
                "your enemies make ...” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare2()  =
        testShare("ESV2011", "Ps.83.1-Ps.83.2", 7..30, false, false,
            "“...do not keep silence; do not hold your peace or be still, O God! For behold, " +
                "your enemies make ...” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare3()  =
        testShare("ESV2011", "Ps.83.1-Ps.83.2", 7..30, true, true,
            "“1. O God, do not keep silence; do not hold your peace or be still, O God! 2. For behold, your " +
                "enemies make an uproar; those who hate you have raised their heads.” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare4()  =
        testShare("ESV2011", "Ps.83.1-Ps.83.2", 7..30, false, true,
            "“O God, do not keep silence; do not hold your peace or be still, O God! For behold, your " +
                "enemies make an uproar; those who hate you have raised their heads.” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare5()  =
        testShare("ESV2011", "Matt.2.23-Matt.3.2", 7..11, true, true,
            "“23. And he went and lived in a city called Nazareth, so that what was spoken " +
                "by the prophets might be fulfilled, that he would be called a Nazarene. 1. In those days " +
                "John the Baptist came preaching in the wilderness of Judea, 2. Repent, for the kingdom " +
                "of heaven is at hand.” (Mat 2:23-3:2, ESV2011)"
        )

    @Test
    fun testShare6()  =
        testShare("ESV2011", "Matt.2.23-Matt.3.2", 7..11, true, false,
            "“23. ...went and lived in a city called Nazareth, so that what was spoken " +
                "by the prophets might be fulfilled, that he would be called a Nazarene. 1. In those days " +
                "John the Baptist came preaching in the wilderness of Judea, 2. Repent, for...” (Mat 2:23-3:2, ESV2011)"
        )
    @Test
    fun testShare7()  =
        testShare("ESV2011", "Ps.43.1-Ps.43.3", 0..100, true, false,
            "“1. Vindicate me, O God, and defend my cause against an ungodly people, from the deceitful and unjust " +
                "man deliver me! 2. For you are the God in whom I take refuge; why have you rejected me? Why do I go about " +
                "mourning because of the oppression of the enemy? 3. Send out your light and your truth; let them lead " +
                "me; let them bring me to your holy hill and to you...” (Psa 43:1-3, ESV2011)"
        )
    @Test
    fun testShare8()  =
        testShare("KJV", "Ps.43.1-Ps.43.3", 0..100, false, true,
            "“Judge me, O God, and plead my cause against an ungodly nation: O deliver me from the deceitful and unjust man. " +
                "For thou art the God of my strength: why dost thou cast me off? why go I mourning because of the oppression of the enemy? " +
                "O send out thy light and thy truth: let them lead me; let them bring me unto thy holy hill, and to thy tabernacles.” (Psa 43:1-3, KJV)"
        )
}
