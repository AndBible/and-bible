package net.bible.android.control.versification

import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterRangesTest {
    private val kjv = requireNotNull(Versifications.instance().getVersification("KJV"))
    private fun assertEndpoints(start: String, end: String, range: VerseRange) {
        assertEquals(start, range.start.getOsisID())
        assertEquals(end, range.end.getOsisID())
    }

    @Test fun johnWithoutIntroductions() {
        assertEndpoints("John.3.0", "John.3.36", wholeChapter(Verse(kjv, BibleBook.JOHN, 3, 16), false))
    }

    @Test fun genesisIncludesBookIntroduction() {
        assertEndpoints("Gen.0.0", "Gen.1.31", wholeChapter(Verse(kjv, BibleBook.GEN, 1, 1)))
    }

    @Test fun introductionIncludesFirstChapter() {
        assertEndpoints("Gen.0.0", "Gen.1.31", wholeChapter(Verse(kjv, BibleBook.GEN, 0, 0)))
    }

    @Test fun multipleChapters() {
        assertEndpoints("John.3.0", "John.4.54", wholeChapters(kjv, BibleBook.JOHN, 3, 4))
    }
}
