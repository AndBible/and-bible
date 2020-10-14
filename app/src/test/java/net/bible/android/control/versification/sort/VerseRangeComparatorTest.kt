package net.bible.android.control.versification.sort

import net.bible.android.control.versification.TestData
import net.bible.service.db.bookmark.BookmarkDto
import org.crosswire.jsword.passage.VerseRange
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.core.IsEqual
import org.hamcrest.number.OrderingComparison
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class VerseRangeComparatorTest {
    private var verseRangeComparator: VerseRangeComparator? = null
    @Before
    fun setup() {
        val bookmarkDto = BookmarkDto()
        bookmarkDto.verseRange = TestData.SYN_PROT_PS_13_2_4
        val bookmarkDto2 = BookmarkDto()
        bookmarkDto2.verseRange = TestData.KJVA_1MACC_1_2_3
        verseRangeComparator = VerseRangeComparator.Builder().withBookmarks(Arrays.asList(bookmarkDto, bookmarkDto2)).build()
    }

    @Test
    @Throws(Exception::class)
    fun testcompareToEqualDifferentVersification() {
        val convertibleVerseRangeUser1 = createConvertibleVerseRangeUserWith(TestData.KJV_PS_14_2_4)
        val convertibleVerseRangeUser2 = createConvertibleVerseRangeUserWith(TestData.SYN_PROT_PS_13_2_4)
        MatcherAssert.assertThat(verseRangeComparator!!.compare(convertibleVerseRangeUser1, convertibleVerseRangeUser2), IsEqual.equalTo(0))
        MatcherAssert.assertThat(verseRangeComparator!!.compare(convertibleVerseRangeUser2, convertibleVerseRangeUser1), IsEqual.equalTo(0))
    }

    @Test
    @Throws(Exception::class)
    fun testcompareToDeuterocanonicalDifferentVersification() {
        val convertibleVerseRangeUser1 = createConvertibleVerseRangeUserWith(TestData.KJVA_1MACC_1_2_3)
        val convertibleVerseRangeUser2 = createConvertibleVerseRangeUserWith(TestData.SYN_PROT_PS_13_2_4)
        MatcherAssert.assertThat(verseRangeComparator!!.compare(convertibleVerseRangeUser1, convertibleVerseRangeUser2), OrderingComparison.greaterThan(0))
        MatcherAssert.assertThat(verseRangeComparator!!.compare(convertibleVerseRangeUser2, convertibleVerseRangeUser1), OrderingComparison.lessThan(0))
    }

    @Test
    @Throws(Exception::class)
    fun testSort() {
        val convertibleVerseRangeUser1 = createConvertibleVerseRangeUserWith(TestData.KJVA_1MACC_1_2_3)
        val convertibleVerseRangeUser2 = createConvertibleVerseRangeUserWith(TestData.SYN_PROT_PS_13_2_4)
        val verseRanges = Arrays.asList(convertibleVerseRangeUser1, convertibleVerseRangeUser2)
        Collections.sort(verseRanges, verseRangeComparator)
        MatcherAssert.assertThat(verseRanges, Matchers.contains(convertibleVerseRangeUser2, convertibleVerseRangeUser1))
    }

    private fun createConvertibleVerseRangeUserWith(convertibleVerseRange: VerseRange): VerseRangeUser {
        return object : VerseRangeUser {
            override val verseRange: VerseRange
                get() = convertibleVerseRange
        }
    }
}
