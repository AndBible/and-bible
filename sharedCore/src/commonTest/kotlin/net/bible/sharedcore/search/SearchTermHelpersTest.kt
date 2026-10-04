package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
