package net.bible.android.view.activity.page

import net.bible.service.common.singularPsalmTitle
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reading title for psalms (#3430) and one-chapter books (#3571). */
class PageTitleTest {
    private val kjv = Versifications.instance().getVersification("KJV")

    private fun title(book: BibleBook, chapter: Int, verse: Int) =
        withLongNames { pageTitleFor(Verse(kjv, book, chapter, verse)) }

    @Test fun singlePsalmVerseIsSingular() = assertEquals("Psalm 119:5", title(BibleBook.PS, 119, 5))
    @Test fun singlePsalmWholeChapterIsSingular() = assertEquals("Psalm 119", title(BibleBook.PS, 119, 0))
    @Test fun oneChapterBookVerse() = assertEquals("Obadiah 7", title(BibleBook.OBAD, 1, 7))
    @Test fun oneChapterBookWholeChapterIsBookName() = assertEquals("Jude", title(BibleBook.JUDE, 1, 0))
    @Test fun multiChapterBookUnchanged() = assertEquals("John 3:16", title(BibleBook.JOHN, 3, 16))
    @Test fun multiChapterWholeChapterUnchanged() = assertEquals("John 3", title(BibleBook.JOHN, 3, 0))

    @Test fun psalmRangesAndWholeBookKeepPlural() {
        assertEquals("Psalms 1-2", singularPsalmTitle("Psalms 1-2", "Psalms"))
        assertEquals("Psalms 1:3-2:4", singularPsalmTitle("Psalms 1:3-2:4", "Psalms"))
        assertEquals("Psalms", singularPsalmTitle("Psalms", "Psalms"))
        assertEquals("Psalm 23:1-3", singularPsalmTitle("Psalms 23:1-3", "Psalms"))
    }

    @Test fun localisedPsalmNamesAreLeftAlone() {
        assertEquals("Psalmit 119", singularPsalmTitle("Psalmit 119", "Psalmit"))
        assertEquals("Psalms 119", singularPsalmTitle("Psalms 119", "Psalmit"))
    }

    private fun <T> withLongNames(block: () -> T): T {
        val old = org.crosswire.jsword.versification.BookName.isFullBookName()
        org.crosswire.jsword.versification.BookName.setFullBookName(true)
        try { return block() } finally { org.crosswire.jsword.versification.BookName.setFullBookName(old) }
    }
}
