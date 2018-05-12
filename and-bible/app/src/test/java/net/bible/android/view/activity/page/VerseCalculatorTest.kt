package net.bible.android.view.activity.page

import junit.framework.Assert.assertEquals
import net.bible.android.control.page.ChapterVerse
import org.junit.Before
import org.junit.Test

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class VerseCalculatorTest {

    private lateinit var verseCalculator: VerseCalculator

    @Before
    fun setup() {
        verseCalculator = VerseCalculator()

        verseCalculator.registerVersePosition(ChapterVerse(1, 0), 2)
        verseCalculator.registerVersePosition(ChapterVerse(1, 1), 20)
        verseCalculator.registerVersePosition(ChapterVerse(1, 2), 40)
        // verse 2 and 3 are on the same line
        verseCalculator.registerVersePosition(ChapterVerse(1, 3), 40)

        verseCalculator.registerVersePosition(ChapterVerse(2, 0), 62)
        verseCalculator.registerVersePosition(ChapterVerse(2, 1), 80)
        verseCalculator.registerVersePosition(ChapterVerse(2, 2), 100)
    }

    @Test
    fun shouldReturnCorrectVerseNumber() {
        // need to move 5 pxels past verse position due to 'stack'
        assertEquals(0, verseCalculator.calculateCurrentVerse(0).verse)
        assertEquals(1, verseCalculator.calculateCurrentVerse(8).verse)
        assertEquals(1, verseCalculator.calculateCurrentVerse(16).verse)
        assertEquals(2, verseCalculator.calculateCurrentVerse(30).verse)

        // chapter 2
        assertEquals(0, verseCalculator.calculateCurrentVerse(50).verse)
        assertEquals(2, verseCalculator.calculateCurrentVerse(90).verse)
    }

    @Test
    fun shouldCopeWithMergedVerses() {
        // miss verse 3
        verseCalculator.registerVersePosition(ChapterVerse(1, 4), 60)

        // need to move 5 pxels past verse position due t0 'stack'
        assertEquals(1, verseCalculator.calculateCurrentVerse(16).verse)
        assertEquals(2, verseCalculator.calculateCurrentVerse(30).verse)
        assertEquals(4, verseCalculator.calculateCurrentVerse(50).verse)
    }
}