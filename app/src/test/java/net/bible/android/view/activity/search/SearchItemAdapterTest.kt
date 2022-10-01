package net.bible.android.view.activity.search

import org.junit.Before
import kotlin.Throws
import org.hamcrest.MatcherAssert
import org.hamcrest.core.IsEqual
import org.junit.Test
import java.lang.Exception

class SearchItemAdapterTest {
    class TestSearch(var searchTerms: String) {
        fun PrepareSearchTerms(): String {
            return prepareSearchTerms(searchTerms)
        }

        fun SplitSearchTerms(): Array<String> {
            return splitSearchTerms(searchTerms)
        }

        fun PrepareSearchWord(): String {
            return prepareSearchWord(searchTerms)
        }
    }

    @Before
    fun setup() {
    }

    @Test
    @Throws(Exception::class)
    fun test_prepareSearchTerms() {
        val test = TestSearch("strong:g000123")
        val result = test.PrepareSearchTerms()
        MatcherAssert.assertThat(result, IsEqual.equalTo("strong:g0*123"))
    }

    @Test
    @Throws(Exception::class)
    fun test_splitSearchTerms() {
        val test = TestSearch("moses \"burning bush\"")
        val result = test.SplitSearchTerms()
        val expectedResult = arrayOf("moses", "\"burning bush\"")
        MatcherAssert.assertThat(result, IsEqual.equalTo(expectedResult))
    }

    @Test
    @Throws(Exception::class)
    fun test_prepareSearchWord() {
        val test = TestSearch("+\"burning bush\"")
        val result = test.PrepareSearchWord()
        MatcherAssert.assertThat(result, IsEqual.equalTo("burning bush"))
    }
}
