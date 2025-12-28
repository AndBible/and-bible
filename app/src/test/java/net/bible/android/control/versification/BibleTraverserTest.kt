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
package net.bible.android.control.versification

import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.junit.Before
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import org.mockito.Mockito
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import kotlin.Throws
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsEqual
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.versification.system.SystemKJV
import org.junit.Test
import java.lang.Exception

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
//@Ignore("Until ESV comes back")
class BibleTraverserTest {
    private var bibleTraverser: BibleTraverser? = null
    private var testBook: AbstractPassageBook? = null
    @Before
    fun setup() {
        val mockDocumentBibleBooksFactory = Mockito.mock(
            DocumentBibleBooksFactory::class.java
        )
        bibleTraverser = BibleTraverser(mockDocumentBibleBooksFactory)
        testBook = Books.installed().getBook("ESV2011") as AbstractPassageBook
    }

    @Test
    @Throws(Exception::class)
    fun testGetNextVerseWrapsToNextChapter() {
        MatcherAssert.assertThat(
            bibleTraverser!!.getNextVerse(
                testBook!!,
                TestData.KJV_PS_14_7
            ).verse, IsEqual.equalTo(1)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGetPreviousVerseWrapsToPreviousChapter() {
        MatcherAssert.assertThat(
            bibleTraverser!!.getPrevVerse(
                testBook!!,
                TestData.KJV_PS_14_1
            ).verse, IsEqual.equalTo(6)
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGetNextVerseRangeWrapsToNextChapter() {
        MatcherAssert.assertThat(
            bibleTraverser!!.getNextVerseRange(
                testBook!!, TestData.KJV_PS_14
            ).start, IsEqual.equalTo(Verse(TestData.KJV, BibleBook.PS, 15, 1))
        )
        MatcherAssert.assertThat(
            bibleTraverser!!.getNextVerseRange(
                testBook!!, TestData.KJV_PS_14
            ).end, IsEqual.equalTo(Verse(TestData.KJV, BibleBook.PS, 16, 2))
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGetPreviousVerseRangeWrapsToPreviousChapter() {
        MatcherAssert.assertThat(
            bibleTraverser!!.getPreviousVerseRange(
                testBook!!, TestData.KJV_PS_14
            ).start, IsEqual.equalTo(Verse(TestData.KJV, BibleBook.PS, 12, 8))
        )
        MatcherAssert.assertThat(
            bibleTraverser!!.getPreviousVerseRange(
                testBook!!, TestData.KJV_PS_14
            ).end, IsEqual.equalTo(Verse(TestData.KJV, BibleBook.PS, 13, 6))
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGetNextVerseRangeCanStopBeforeNextChapter() {
        MatcherAssert.assertThat(
            bibleTraverser!!.getNextVerseRange(testBook!!, TestData.KJV_PS_14, false),
            IsEqual.equalTo(
                TestData.KJV_PS_14
            )
        )
    }

    internal interface TestData {
        companion object {
            val KJV = Versifications.instance().getVersification(SystemKJV.V11N_NAME)
            val KJV_PS_14_7 = Verse(KJV, BibleBook.PS, 14, 7)
            val KJV_PS_14_1 = Verse(KJV, BibleBook.PS, 14, 1)
            val KJV_PS_14 = VerseRange(KJV, KJV_PS_14_1, KJV_PS_14_7)
        }
    }
}
