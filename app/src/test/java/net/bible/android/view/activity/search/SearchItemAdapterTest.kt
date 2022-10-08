package net.bible.android.view.activity.search

import kotlin.Throws
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsEqual
import org.junit.Test
import java.lang.Exception

class SearchItemAdapterTest {
    @Test
    @Throws(Exception::class)
    fun testPrepareSearchTerms() {
        val result = prepareSearchTerms("strong:g000123")
        MatcherAssert.assertThat(result, IsEqual.equalTo("strong:g0*123"))
    }

    @Test
    @Throws(Exception::class)
    fun testSplitSearchTerms() {
        val result = splitSearchTerms("moses \"burning bush\"")
        val expectedResult = listOf("moses", "\"burning bush\"")
        MatcherAssert.assertThat(result, IsEqual.equalTo(expectedResult))
    }

    @Test
    @Throws(Exception::class)
    fun testPrepareSearchWord() {
        val result = prepareSearchWord("+\"burning bush\"")
        MatcherAssert.assertThat(result, IsEqual.equalTo("burning bush"))
    }
}
