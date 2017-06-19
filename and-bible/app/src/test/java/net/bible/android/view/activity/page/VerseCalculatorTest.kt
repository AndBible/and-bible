package net.bible.android.view.activity.page

import junit.framework.Assert.assertEquals
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

        verseCalculator.registerVersePosition(0, 2)
        verseCalculator.registerVersePosition(1, 20)
        verseCalculator.registerVersePosition(2, 40)
    }

    @Test
    fun shouldReturnCorrectVerseNumber() {
        // need to move 5 pxels past verse position due t0 'stack'
        assertEquals(0, verseCalculator.calculateCurrentVerse(0))
        assertEquals(1, verseCalculator.calculateCurrentVerse(8))
        assertEquals(1, verseCalculator.calculateCurrentVerse(16))
        assertEquals(2, verseCalculator.calculateCurrentVerse(30))
        assertEquals(2, verseCalculator.calculateCurrentVerse(50))
    }

    @Test
    fun shouldKnowLastVerse() {
        assertEquals(false, verseCalculator.isLastVerse(0))
        assertEquals(false, verseCalculator.isLastVerse(1))
        assertEquals(true, verseCalculator.isLastVerse(2))
        // should not throw error if last verse merged and so go beyond last verse
        assertEquals(true, verseCalculator.isLastVerse(3))
    }

    @Test
    fun shouldCopeWithMergedVerses() {
        // miss verse 3
        verseCalculator.registerVersePosition(4, 60)

        // need to move 5 pxels past verse position due t0 'stack'
        assertEquals(1, verseCalculator.calculateCurrentVerse(16))
        assertEquals(2, verseCalculator.calculateCurrentVerse(30))
        assertEquals(4, verseCalculator.calculateCurrentVerse(50))
    }
}