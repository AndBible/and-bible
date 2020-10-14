package net.bible.android.control.versification.sort

import net.bible.android.control.versification.TestData
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import java.util.*

class VersificationPrioritiserTest {
    @Test
    fun prioritiseVersifications() {
        val convertibleVerseRangeUsers = Arrays.asList(
            createConvertibleVerseRangeUserWith(TestData.KJV),
            createConvertibleVerseRangeUserWith(TestData.NRSV),
            createConvertibleVerseRangeUserWith(TestData.LXX),
            createConvertibleVerseRangeUserWith(TestData.NRSV),
            createConvertibleVerseRangeUserWith(TestData.KJV),
            createConvertibleVerseRangeUserWith(TestData.NRSV),
            createConvertibleVerseRangeUserWith(TestData.SEGOND)
        )
        val versificationPrioritiser = VersificationPrioritiser(convertibleVerseRangeUsers)
        val orderedVersifications = versificationPrioritiser.prioritisedVersifications
        MatcherAssert.assertThat(orderedVersifications[0], Matchers.equalTo(TestData.NRSV))
        MatcherAssert.assertThat(orderedVersifications[1], Matchers.equalTo(TestData.KJV))
        MatcherAssert.assertThat(orderedVersifications.size, Matchers.equalTo(4))
    }

    private fun createConvertibleVerseRangeUserWith(v11n: Versification): VerseRangeUser {
        return object : VerseRangeUser {
            override val verseRange: VerseRange
                get() = VerseRange(v11n, Verse(v11n, BibleBook.JOHN, 3, 16))
        }
    }
}
