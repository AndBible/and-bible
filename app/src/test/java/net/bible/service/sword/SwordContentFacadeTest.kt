/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.misc.elementToString
import net.bible.android.view.activity.page.Selection
import net.bible.service.sword.SwordContentFacade.bibleRefSplit
import net.bible.test.DatabaseResetter

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.core.IsNot.not
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
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
        } catch (e: JSwordError) {
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
            if (e is OsisError) "fixed" else "broken"
        }
        assertThat(html, not(equalTo("broken")))
    }

    //@Ignore("Until ESV comes back")
    @Test
    fun testReadEsvIssue141b() {
        val esv = getBook("ESV2011")

        for (i in 1..35) {
            val verse = getVerse(esv, "Matt.18.$i")

            val html = try {
                SwordContentFacade.readOsisFragment(esv, verse)
            } catch (e: Exception) {
                if (e is OsisError) "fixed" else "broken"
            }
            assertThat(html, not(equalTo("broken")))
        }
    }

    @Test
    fun testReadNasbIssue2365() {
        val nasb = getBook("NASB")
        val key = VerseRangeFactory.fromString((nasb as SwordBook).versification, "Rev.3.1")
        val text = SwordContentFacade.getCanonicalText(nasb, key, true)
        val ref = "“To the angel of the church in Sardis write:  He who has the seven Spirits of God " +
            "and the seven stars, says this: ‘I know your deeds, that you have a name that you are alive, " +
            "but you are dead. "
        assertThat(text, equalTo(ref));
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

class SentenceSplitTest {
    @Test fun testSplitSentences01() = assertThat(
        SwordContentFacade.splitSentences("test test test. test test"),
        equalTo(listOf("test test test. ", "test test"))
    )

    @Test fun testSplitSentences02() = assertThat(
        SwordContentFacade.splitSentences("Returns a sequence of all occurrences of a regular expression within the input string, beginning at the specified startIndex."),
        equalTo(listOf("Returns a sequence of all occurrences of a regular expression within the input string, ", "beginning at the specified startIndex."))
    )

    @Test fun testSplitSentences02c() = assertThat(
        SwordContentFacade.splitSentences("Returns a sequence of all occurrences of a regular expression within the input Matt 1:2-3:4 ning at the specified startIndex."),
        equalTo(listOf("Returns a sequence of all occurrences of a regular expression within the input Matt 1:2-3:4 ning at the specified startIndex."))
    )

    @Test fun testSplitSentences02b() = assertThat(
        SwordContentFacade.splitSentences("Returns a sequence of all occurrences Matt 1:2 a regular expression within the input string, beginning at the specified startIndex."),
        equalTo(listOf("Returns a sequence of all occurrences Matt 1:2 a regular expression within the input string, ", "beginning at the specified startIndex."))
    )

    @Test fun testSplitSentences03() = assertThat(
        SwordContentFacade.splitSentences("Testilause testilause."),
        equalTo(listOf("Testilause testilause."))
    )

    @Test fun testSplitSentences04() = assertThat(
        SwordContentFacade.splitSentences("Testilause testilause"),
        equalTo(listOf("Testilause testilause"))
    )

    @Test fun testSplitSentences05() = assertThat(
        SwordContentFacade.splitSentences("Testilause"),
        equalTo(listOf("Testilause"))
    )

    @Test fun testSplitSentences06() = assertThat(
        SwordContentFacade.splitSentences(""),
        equalTo(listOf())
    )

    @Test fun testSplitSentences07() = assertThat(
        SwordContentFacade.splitSentences("test. Test2. Test3."),
        equalTo(listOf("test. ", "Test2. Test3."))
    )

    @Test fun testSplitSentences07b() = assertThat(
        SwordContentFacade.splitSentences("test - Test."),
        equalTo(listOf("test - ", "Test."))
    )

    @Test fun testSplitSentences08() = assertThat(
        SwordContentFacade.splitSentences("test. Joh 3.2. Test."),
        equalTo(listOf("test. ", "Joh 3.2. Test."))
    )
    @Test fun testSplitSentences08b() = assertThat(
        SwordContentFacade.splitSentences("Joh 3.2. Test."),
        equalTo(listOf("Joh 3.2. Test."))
    )

    @Test fun testSplitSentences08c() = assertThat(
        SwordContentFacade.splitSentences("Joh 3.2,4 Matt 3:4"),
        equalTo(listOf("Joh 3.2,4 Matt 3:4"))
    )

    @Test fun testSplitSentences08d() = assertThat(
        SwordContentFacade.splitSentences("Joh. 3.2:4-Matt. 3:4"),
        equalTo(listOf("Joh. 3.2:4-Matt. 3:4"))
    )

    @Test fun testSplitSentences08f() = assertThat(
        SwordContentFacade.splitSentences("It ends with Matt 12. Then it continues."),
        equalTo(listOf("It ends with Matt 12. ", "Then it continues."))
    )

    @Test fun testSplitSentences08e() = assertThat(
        SwordContentFacade.splitSentences("Joh. 3:2-Joh. 3:4"),
        equalTo(listOf("Joh. 3:2-Joh. 3:4"))
    )

    @Test fun testSplitSentences09() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Eph 1:1-2). Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Eph 1:1-2). ", "Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences10() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Eph 1:1-2,3). Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Eph 1:1-2,3). ", "Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences11() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Eph 1:1-2, Matt 2:3). Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Eph 1:1-2, Matt 2:3). ", "Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences12() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Ef. 1:1-2, Matt. 2:3). Jotain tekstiä lisää."),
                                equalTo(listOf("Jotain tekstiä (Ef. 1:1-2, Matt. 2:3). ", "Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences13() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Ef. 1:1-2,Matt. 2:3). Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Ef. 1:1-2,Matt. 2:3). ", "Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences14() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä Ef. 1:1-2,Matt. 2:3. Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä Ef. 1:1-2,Matt. 2:3. Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences15() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä Ef1:1-2,Matt.2:3. Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä Ef1:1-2,Matt.2:3. Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences16() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Ref1 1:2). Jotain tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Ref1 1:2). ", "Jotain tekstiä lisää."))
    )

    @Test fun testSplitSentences17() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Ref1 1:2). \"Jotain\" tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Ref1 1:2). ", "\"Jotain\" tekstiä lisää."))
    )
    @Test fun testSplitSentences18() = assertThat(
        SwordContentFacade.splitSentences("\"Jotain tekstiä (Ref1 1:2)\". \"Jotain\" tekstiä lisää."),
        equalTo(listOf("\"Jotain tekstiä (Ref1 1:2)\". ", "\"Jotain\" tekstiä lisää."))
    )

    @Test fun testSplitSentences19() = assertThat(
        SwordContentFacade.splitSentences("\"Jotain tekstiä (Ref1 1:2).\" \"Jotain\" tekstiä lisää."),
        equalTo(listOf("\"Jotain tekstiä (Ref1 1:2).\" ", "\"Jotain\" tekstiä lisää."))
    )

    @Test fun testSplitSentences20() = assertThat(
        SwordContentFacade.splitSentences("Jotain tekstiä (Ref1 1:2): \"Jotain\" tekstiä lisää."),
        equalTo(listOf("Jotain tekstiä (Ref1 1:2): ", "\"Jotain\" tekstiä lisää."))
    )

    @Test fun testSplitSentences21() = assertThat(
        SwordContentFacade.splitSentences("1. Joh 4:20"),
        equalTo(listOf("1. Joh 4:20"))
    )

    @Test fun testSplitSentences22() = assertThat(
        SwordContentFacade.splitSentences("¡Hola sinä mies mikä oletkin! How are you?"),
        equalTo(listOf("¡Hola sinä mies mikä oletkin! ", "How are you?"))
    )

    @Test fun testSplitSentences23() = assertThat(
        SwordContentFacade.splitSentences("Test test test! ¡Hola sinä mies mikä oletkin! How are you?"),
        equalTo(listOf("Test test test! ", "¡Hola sinä mies mikä oletkin! ", "How are you?"))
    )

    @Test fun testSplitSentences24() = assertThat(
        SwordContentFacade.splitSentences("We have received in that Beloved. In Him we also."),
        equalTo(listOf("We have received in that Beloved. ", "In Him we also."))
    )

    @Test fun testSplitSentences25() = assertThat(
        SwordContentFacade.splitSentences("We also have “the forgiveness”. You could say that."),
        equalTo(listOf("We also have “the forgiveness”. ", "You could say that."))
    )

    @Test fun testSplitSentences26() = assertThat(
        SwordContentFacade.splitSentences("A picture of the Lamb of God, the Lord Jesus. What you have"),
        equalTo(listOf("A picture of the Lamb of God, ", "the Lord Jesus. ", "What you have"))
    )
}
class BibleRefRegexTest {
    fun matchesExact(s: String): Boolean {
        val m = SwordContentFacade.bibleRefRe.find(s)
        return m?.groupValues?.get(0) == s
    }
    fun matches(s: String): String {
        val m = SwordContentFacade.bibleRefRe.find(s)
        return m?.groupValues?.get(0)!!
    }
    fun finds(s: String): Boolean {
        val m = SwordContentFacade.bibleRefRe.find(s)
        return m != null
    }

    @Test fun testRe1() = assertThat(matchesExact("Matt 1:1"), equalTo(true))
    @Test fun testRe2() = assertThat(matchesExact("Matt 1:1, 1"), equalTo(true))
    @Test fun testRe3() = assertThat(matchesExact("Matt 1:1, 1:1-2:2"), equalTo(true))
    @Test fun testRe4() = assertThat(matchesExact("Matt 1:1-2:2, 1-2, 1:1-1:2, 1-2:2, 2-3:1"), equalTo(true))
    @Test fun testRe4b() = assertThat(finds("matt 1:1-2:2, 1-2, 1:1-1:2, 1-2:2, 2-3:1"), equalTo(false))
    @Test fun testRe5() = assertThat(matchesExact("1 Joh 1:1-2:2, 1-2, 1:1-1:2, 1-2:2, 2-3:1"), equalTo(true))
    @Test fun testRe6() = assertThat(matchesExact("1. Joh 1:1-2:2, 1-2, 1:1-1:2, 1-2:2, 2-3:1"), equalTo(true))
    @Test fun testRe7() = assertThat(matchesExact("1. Joh. 1:1-2:2, 1-2, 1:1-1:2, 1-2:2, 2-3:1"), equalTo(true))
    @Test fun testRe2_1() = assertThat(matchesExact("Matt 1"), equalTo(true))

    // To keep regex "simple", we intentionally do not support now book of Jude references that do not include chapter number. Jude 1:1 works fine.
    @Test fun testRe2_2() = assertThat(matchesExact("Jude 1-5"), equalTo(true))
    @Test fun testRe2_3() = assertThat(finds("jude 1-5"), equalTo(false))
    @Test fun testRe2_4() = assertThat(matchesExact("1. Joh. 4:20"), equalTo(true))
    @Test fun testRe2_5() = assertThat(matchesExact("Ex. 13:2"), equalTo(true))
    @Test fun testRe2_6() = assertThat(matchesExact("Psalms 2; 22; 24; 45; 72"), equalTo(true))
    @Test fun testRe2_7() = assertThat(matchesExact("Psalms 2; 22; Job 24; 45; 72"), equalTo(false))
    @Test fun testRe2_8() = assertThat(finds("Acts 1:24; 1 Cor. 4:5"), equalTo(true))
    @Test fun biblesplit1() = assertThat(bibleRefSplit("Acts 1:24; 1 Cor. 4:5").map { it.first }, equalTo(listOf("Acts 1:24", "; ", "1 Cor. 4:5")))
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TestShare {
    private fun testShare(
        initials: String,
        verseRangeStr: String,
        offsetRange: IntRange,
        showVerseNumbers: Boolean,
        showWholeVerse: Boolean,
        compareText: String,
        advertiseApp: Boolean = false,
        showReference: Boolean = true,
        showReferenceAtFront: Boolean = false,
        abbreviateReference: Boolean = true,
        showNotes: Boolean = true,
        showVersion: Boolean = true,
        showEllipsis: Boolean = true,
        showQuotes: Boolean = true
    ) {

        val book = Books.installed().getBook(initials) as SwordBook
        val v11n = book.versification
        val verseRange = VerseRangeFactory.fromString(v11n, verseRangeStr)


        val sel = Selection(
            initials,
            verseRange.start.ordinal,
            offsetRange.first,
            verseRange.end.ordinal,
            offsetRange.last,
            emptyList()
        )

        val text = SwordContentFacade.getSelectionText(
            sel,
            showVerseNumbers = showVerseNumbers,
            showSelectionOnly = !showWholeVerse,
            showReference = showReference,
            advertiseApp = advertiseApp,
            showReferenceAtFront = showReferenceAtFront,
            showQuotes = showQuotes,
            abbreviateReference = abbreviateReference,
            showNotes = showNotes,
            showVersion = showVersion,
            showEllipsis = showEllipsis,
        )

        assertThat(text, equalTo(compareText))
    }

    @Test
    fun testShare1a() =
        testShare(
            "ESV2011", "Ps.83.1", 7..30, true, false,
            "“...do not keep silence; do...” (Psa 83:1, ESV2011)"
        )

    @Test
    fun testShare2a() =
        testShare(
            "ESV2011", "Ps.83.1", 7..30, false, false,
            "“...do not keep silence; do...” (Psa 83:1, ESV2011)"
        )

    @Test
    fun testShare3a() =
        testShare(
            "ESV2011", "Ps.83.1", 7..30, true, true,
            "“O God, do not keep silence; do not hold your peace or be still, O God!” (Psa 83:1, ESV2011)"
        )

    @Test
    fun testShare4a() =
        testShare(
            "ESV2011", "Ps.83.1", 7..30, false, true,
            "“O God, do not keep silence; do not hold your peace or be still, O God!” (Psa 83:1, ESV2011)"
        )


    @Test
    fun testShare1() =
        testShare(
            "ESV2011", "Ps.83.1-Ps.83.2", 7..30, true, false,
            "“1. ...do not keep silence; do not hold your peace or be still, O God! 2. For behold, " +
                "your enemies make ...” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare2() =
        testShare(
            "ESV2011", "Ps.83.1-Ps.83.2", 7..30, false, false,
            "“...do not keep silence; do not hold your peace or be still, O God! For behold, " +
                "your enemies make ...” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare3() =
        testShare(
            "ESV2011", "Ps.83.1-Ps.83.2", 7..30, true, true,
            "“1. O God, do not keep silence; do not hold your peace or be still, O God! 2. For behold, your " +
                "enemies make an uproar; those who hate you have raised their heads.” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare4() =
        testShare(
            "ESV2011", "Ps.83.1-Ps.83.2", 7..30, false, true,
            "“O God, do not keep silence; do not hold your peace or be still, O God! For behold, your " +
                "enemies make an uproar; those who hate you have raised their heads.” (Psa 83:1-2, ESV2011)"
        )

    @Test
    fun testShare5() =
        testShare(
            "ESV2011", "Matt.2.23-Matt.3.2", 7..11, true, true,
            "“23. And he went and lived in a city called Nazareth, so that what was spoken " +
                "by the prophets might be fulfilled, that he would be called a Nazarene. 1. In those days " +
                "John the Baptist came preaching in the wilderness of Judea, 2. “Repent, for the kingdom " +
                "of heaven is at hand.”” (Mat 2:23-3:2, ESV2011)"
        )

    @Test
    fun testShare6() =
        testShare(
            "ESV2011", "Matt.2.23-Matt.3.2", 7..12, true, false,
            "“23. ...went and lived in a city called Nazareth, so that what was spoken " +
                "by the prophets might be fulfilled, that he would be called a Nazarene. 1. In those days " +
                "John the Baptist came preaching in the wilderness of Judea, 2. “Repent, for...” (Mat 2:23-3:2, ESV2011)"
        )

    @Test
    fun testShare7() =
        testShare(
            "ESV2011", "Ps.43.1-Ps.43.3", 0..100, true, false,
            "“1. Vindicate me, O God, and defend my cause against an ungodly people, from the deceitful and unjust " +
                "man deliver me! 2. For you are the God in whom I take refuge; why have you rejected me? Why do I go about " +
                "mourning because of the oppression of the enemy? 3. Send out your light and your truth; let them lead " +
                "me; let them bring me to your holy hill and to you...” (Psa 43:1-3, ESV2011)"
        )

    @Test
    fun testShare8() =
        testShare(
            "KJV", "Ps.43.1-Ps.43.3", 0..100, false, true,
            "“Judge me, O God, and plead my cause against an ungodly nation: O deliver me from the deceitful and unjust man. " +
                "For thou art the God of my strength: why dost thou cast me off? why go I mourning because of the oppression of the enemy? " +
                "O send out thy light and thy truth: let them lead me; let them bring me unto thy holy hill, and to thy tabernacles.” (Psa 43:1-3, KJV)"
        )

    @Test
    fun testShare9() =
        testShare(
            initials = "KJV",
            verseRangeStr = "Ps.43.1",
            offsetRange = 0..100,
            showWholeVerse = true,
            compareText = "Judge me, O God, and plead my cause against an ungodly nation: O deliver me from the deceitful and unjust man.",
            advertiseApp = false,
            showReference = false,
            abbreviateReference = true,
            showVersion = false,
            showReferenceAtFront = false,
            showVerseNumbers = false,
            showQuotes = false,
            showNotes = true
        )

    @Test
    fun testShare10() =
        testShare(
            initials = "KJV",
            verseRangeStr = "Ps.43.1",
            offsetRange = 0..100,
            showWholeVerse = true,
            compareText = "“Judge me, O God, and plead my cause against an ungodly nation: O deliver me from the deceitful and unjust man.” (Psa 43:1, KJV)",
            advertiseApp = false,
            showReference = true,
            abbreviateReference = true,
            showVersion = true,
            showReferenceAtFront = false,
            showVerseNumbers = false,
            showQuotes = true,
            showNotes = true
        )

    @Test
    fun testShare11() =
        testShare(
            initials = "KJV",
            verseRangeStr = "Ps.43.1",
            offsetRange = 0..5,
            showWholeVerse = false,
            compareText = "Judge",
            advertiseApp = false,
            showReference = false,
            abbreviateReference = true,
            showVersion = true,
            showReferenceAtFront = false,
            showVerseNumbers = false,
            showQuotes = false,
            showEllipsis = false,
            showNotes = false
        )

    @Test
    fun testShare12() =
        testShare(
            initials = "KJV",
            verseRangeStr = "Ps.43.1",
            offsetRange = 0..5,
            showWholeVerse = false,
            compareText = "Judge (Psalms 43:1)",
            advertiseApp = false,
            showReference = true,
            abbreviateReference = false,
            showVersion = false,
            showReferenceAtFront = false,
            showVerseNumbers = false,
            showQuotes = false,
            showEllipsis = false,
            showNotes = false
        )
}
