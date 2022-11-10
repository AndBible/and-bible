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
package net.bible.android.view.activity.page

import org.junit.Before
import kotlin.Throws
import org.hamcrest.MatcherAssert
import net.bible.android.control.page.ChapterVerse
import org.hamcrest.core.IsEqual
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.Matchers
import org.junit.Test
import java.lang.Exception

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ChapterVerseRangeTest {
    private var chapterVerseRange: ChapterVerseRange? = null
    @Before
    fun setup() {
    }

    @Test
    @Throws(Exception::class)
    fun testExpandDown() {
        chapterVerseRange = getChapterVerseRange(7, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(10))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(7)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(10)))
    }

    @Test
    @Throws(Exception::class)
    fun testExpandDown_differentChapter() {
        chapterVerseRange = getChapterVerseRange(7, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(8, 10))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(7)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(8, 10)))
    }

    @Test
    @Throws(Exception::class)
    fun testExpandUp() {
        chapterVerseRange = getChapterVerseRange(7, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(3))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(3)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(7)))
    }

    @Test
    @Throws(Exception::class)
    fun testExpandUp_differentChapter() {
        chapterVerseRange = getChapterVerseRange(7, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(2, 13))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(2, 13)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(7)))
    }

    @Test
    @Throws(Exception::class)
    fun testReduceUp() {
        chapterVerseRange = getChapterVerseRange(3, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(6))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(3)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(5)))
    }

    @Test
    @Throws(Exception::class)
    fun testReduceUp_differentChapter() {
        chapterVerseRange = getChapterVerseRange(3, 3, 4, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(3, 6))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(3)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(5)))
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(4, 7))
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(4, 6))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(4, 5)))
    }

    @Test
    @Throws(Exception::class)
    fun testReduceDown() {
        chapterVerseRange = getChapterVerseRange(3, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(3))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(4)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(7)))
    }

    @Test
    @Throws(Exception::class)
    fun testReduceDown_differentChapter() {
        chapterVerseRange = getChapterVerseRange(3, 3, 4, 7)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(3, 3))
        // there is a compromise in the code that prevents the first verse being deselected if multiple chapters in selection
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(getChapterVerse(3, 3)))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(getChapterVerse(4, 7)))
    }

    @Test
    @Throws(Exception::class)
    fun testReduceToZero() {
        chapterVerseRange = getChapterVerseRange(3, 3)
        chapterVerseRange = chapterVerseRange!!.toggleVerse(getChapterVerse(3))
        MatcherAssert.assertThat(chapterVerseRange!!.isEmpty(), IsEqual.equalTo(true))
        MatcherAssert.assertThat(chapterVerseRange!!.start, IsEqual.equalTo(null))
        MatcherAssert.assertThat(chapterVerseRange!!.end, IsEqual.equalTo(null))
    }

    @Test
    fun testGetExtras() {
        chapterVerseRange = getChapterVerseRange(3, 7)
        val other = getChapterVerseRange(6, 8)
        MatcherAssert.assertThat(
            chapterVerseRange!!.getExtrasIn(other),
            Matchers.containsInAnyOrder(getChapterVerse(8))
        )
        MatcherAssert.assertThat(
            other.getExtrasIn(
                chapterVerseRange!!
            ),
            Matchers.containsInAnyOrder(getChapterVerse(3), getChapterVerse(4), getChapterVerse(5))
        )
    }

    @Test
    fun testGetExtras_multipleChapters() {
        chapterVerseRange = getChapterVerseRange(3, 13, 4, 3)
        val other = getChapterVerseRange(3, 12, 4, 5)
        MatcherAssert.assertThat(
            chapterVerseRange!!.getExtrasIn(other),
            Matchers.containsInAnyOrder(
                getChapterVerse(3, 12),
                getChapterVerse(4, 4),
                getChapterVerse(4, 5)
            )
        )
        //MatcherAssert.assertThat(
        //    other.getExtrasIn(chapterVerseRange!!), Matchers.containsInAnyOrder<ChapterVerse>()
        //)
    }

    private fun getChapterVerseRange(startVerse: Int, endVerse: Int): ChapterVerseRange {
        val start = getChapterVerse(startVerse)
        val end = getChapterVerse(endVerse)
        return ChapterVerseRange(TestData.V11N, BibleBook.JOHN, start, end)
    }

    private fun getChapterVerseRange(
        startChapter: Int,
        startVerse: Int,
        endChapter: Int,
        endVerse: Int
    ): ChapterVerseRange {
        val start = getChapterVerse(startChapter, startVerse)
        val end = getChapterVerse(endChapter, endVerse)
        return ChapterVerseRange(TestData.V11N, BibleBook.JOHN, start, end)
    }

    private fun getChapterVerse(verse: Int): ChapterVerse {
        return ChapterVerse(TestData.CHAPTER, verse)
    }

    private fun getChapterVerse(chapter: Int, verse: Int): ChapterVerse {
        return ChapterVerse(chapter, verse)
    }

    private interface TestData {
        companion object {
            val V11N = Versifications.instance().getVersification(Versifications.DEFAULT_V11N)
            const val CHAPTER = 3
        }
    }
}
