package net.bible.service.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization of [TitleSplitter] as it behaves today. [TitleSplitter] passes its patterns to Kotlin's
 * `String.split(vararg delimiters)`, which matches them literally, not as regexes, so no split happens and only the
 * per-part truncation and the single-part [shorten] path are observable. These tests pin that behaviour so the
 * commons-lang3 removal cannot change it; fixing the split is a separate, behaviour-changing task.
 */
class TitleSplitterTest {
    private val splitter = TitleSplitter()

    @Test fun verseReferenceIsNotSplitBecauseTheRegexIsMatchedLiterally() {
        // the single part is then truncated to MAX_PART_LENGTH (6)
        assertArrayEquals(arrayOf("1Cor 2"), splitter.split("1Cor 2:1"))
    }

    @Test fun moduleNameIsTruncatedToSixCharsWhenNotSplit() {
        assertArrayEquals(arrayOf("GodsWo"), splitter.split("GodsWord"))
    }

    @Test fun longCamelCaseTitleIsTruncatedToSixChars() {
        assertArrayEquals(arrayOf("Strong"), splitter.split("StrongsRealGreekWord"))
    }

    @Test fun shortTitleIsReturnedUnchangedWhenWithinSixChars() {
        assertArrayEquals(arrayOf("Genesi"), splitter.split("Genesis"))
        assertArrayEquals(arrayOf("Gen"), splitter.split("Gen"))
    }

    @Test fun shortenKeepsTextThatFitsUnchanged() {
        assertEquals("Genesis", splitter.shorten("Genesis", 7))
    }

    @Test fun shortenSingleWordTruncatesToMaxLength() {
        assertEquals("Gen", splitter.shorten("Genesis", 3))
        assertEquals("Gene", splitter.shorten("Genesis", 4))
        assertEquals("Genesi", splitter.shorten("Genesis", 6))
    }

    @Test fun shortenCamelCaseTitleTruncatesAsOnePart() {
        assertEquals("Strong", splitter.shorten("StrongsRealGreek", 6))
        assertEquals("Strongs", splitter.shorten("StrongsRealGreek", 7))
    }

    @Test fun splitOfEmptyStringYieldsSingleEmptyPart() {
        assertArrayEquals(arrayOf(""), splitter.split(""))
    }

    @Test fun shortenOfEmptyStringIsEmpty() {
        assertEquals("", splitter.shorten("", 3))
    }
}
