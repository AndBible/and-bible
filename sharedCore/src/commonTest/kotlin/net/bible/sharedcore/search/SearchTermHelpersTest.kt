package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTermHelpersTest {
    @Test
    fun testPrepareSearchTerms() {
        val result = prepareSearchTerms("strong:g000123")
        assertEquals("strong:g0*123", result)
    }

    @Test
    fun testSplitSearchTerms() {
        val result = splitSearchTerms("moses \"burning bush\"")
        val expectedResult = listOf("moses", "\"burning bush\"")
        assertEquals(expectedResult, result)
    }

    @Test
    fun testPrepareSearchWord() {
        val result = prepareSearchWord("+\"burning bush\"")
        assertEquals("burning bush", result)
    }

    private fun List<String>.anyMatches(lemma: String) =
        any { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(lemma) }

    @Test
    fun allWordsDecoratedTermMatches() {
        val patterns = strongsHighlightPatterns("+strong:h0*7225")
        assertEquals(1, patterns.size)
        assertTrue(patterns.anyMatches("strong:H7225"))
        assertTrue(patterns.anyMatches("strong:H07225"))
    }

    @Test
    fun phraseDecoratedStrongsTermIsBolded() {
        assertTrue(strongsHighlightPatterns("\"strong:h0*7225\"").anyMatches("strong:H7225"))
    }

    @Test
    fun twoTermsGiveTwoPatterns() {
        val patterns = strongsHighlightPatterns("+strong:h0*7225 +strong:h0*430")
        assertEquals(2, patterns.size)
        assertTrue(patterns.anyMatches("strong:H0430"))
        assertTrue(patterns.anyMatches("strong:H7225"))
        assertFalse(listOf(patterns[0]).anyMatches("strong:H0430") && listOf(patterns[0]).anyMatches("strong:H7225"))
    }

    @Test
    fun excludedTermGivesNoPattern() {
        val patterns = strongsHighlightPatterns("strong:h0*7225 -strong:h0*430")
        assertEquals(1, patterns.size)
        assertTrue(patterns.anyMatches("strong:H7225"))
        assertFalse(patterns.anyMatches("strong:H0430"))
    }

    @Test
    fun shorterNumberDoesNotBoldLongerLemma() {
        assertFalse(strongsHighlightPatterns("strong:h0*72").anyMatches("strong:H7225"))
        assertTrue(strongsHighlightPatterns("strong:H72*").anyMatches("strong:H7225"))
    }

    @Test
    fun multiValueLemmaAttributeMatches() {
        val patterns = strongsHighlightPatterns("strong:g0*2316")
        assertTrue(patterns.anyMatches("strong:G2316 lemma.TR:θεος"))
        assertTrue(patterns.anyMatches("strong:G0846 strong:G2316"))
    }

    @Test
    fun nonStrongsTokensIgnored() {
        assertEquals(1, strongsHighlightPatterns("+strong:h0*7225 +beginning").size)
    }

    @Test
    fun regexMetacharactersAreQuoted() {
        strongsHighlightPatterns("strong:h(7225").forEach { Regex(it, RegexOption.IGNORE_CASE) }
    }

    private fun highlightMatches(term: String, text: String): List<String> =
        Regex(termHighlightPattern(term)!!, RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()

    @Test
    fun latinTermKeepsWordBoundary() {
        assertEquals(listOf("God"), highlightMatches("God", "God created"))
        assertEquals(emptyList(), highlightMatches("God", "Godly"))
    }

    @Test
    fun latinWildcardKeepsSuffix() {
        assertEquals(listOf("believeth"), highlightMatches("believ*", "he believeth now"))
    }

    @Test
    fun cjkTermMatchesInsideText() {
        assertEquals(listOf("上帝"), highlightMatches("上帝", "神爱世人上帝的话"))
    }

    @Test
    fun thaiTermWithCombiningMarksHighlightedWhole() {
        assertEquals(listOf("พระเจ้า"), highlightMatches("พระเจ้า", "ในปฐมกาลพระเจ้าทรงสร้าง"))
    }

    @Test
    fun latinTermKeepsWordBoundaryNextToCjk() {
        assertEquals(emptyList(), highlightMatches("God", "Godhead上帝"))
        assertEquals(listOf("God"), highlightMatches("God", "God上帝"))
        assertEquals(listOf("God"), highlightMatches("God", "上帝God"))
    }

    @Test
    fun emptyTermGivesNull() {
        assertNull(termHighlightPattern("+\"\""))
    }
}
