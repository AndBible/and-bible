/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.search

import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** `isRegex` requests reach the stored-text scan unchanged (no Lucene decoration) and report budget truncation. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleSearchServiceImplRegexTest {
    private val kjv get() = Books.installed().getBook("KJV")!!

    @Before fun index() { FtsTestSupport.ensureIndexed(kjv) }
    @After fun cleanUp() { FtsTestSupport.dropIndex(kjv) }

    private val strings = object : CoreStrings by mock<CoreStrings>() {
        override fun searchInvalidRegex(engineMessage: String) = "INVALID:$engineMessage"
    }

    private fun service(): BibleSearchServiceImpl {
        val koin = GlobalContext.get()
        return BibleSearchServiceImpl(koin.get<SearchControl>(), koin.get<SearchTextHighlighter>(), mock<AppSettings>(), strings)
    }

    private fun request(query: String, section: SearchBibleSection = SearchBibleSection.ALL) = SearchRequest(
        query = query, searchType = SearchType.ALL_WORDS, bibleSection = section,
        currentBookName = "Genesis", translationIds = listOf("KJV"), isRegex = true,
    )

    private fun search(r: SearchRequest, budget: Duration = SearchControl.REGEX_SEARCH_BUDGET): MultiSearchResults =
        runBlocking { service().searchMulti(r, budget) }

    private fun refsOf(r: MultiSearchResults) = (r.main + r.other).map { it.referenceName }

    private fun assertEveryPreviewMatches(results: MultiSearchResults, pattern: String) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        (results.main + results.other).forEach { row ->
            val text = row.primaryPreview.plainText()
            assertTrue("${row.referenceName}: $text", regex.containsMatchIn(text))
            assertTrue(row.primaryPreview.runs.any { it.highlight })
        }
    }

    @Test fun colonPatternReachesTheRegexUnchanged() {
        // On the Lucene path ':' is stripped; here it is literal regex text.
        val results = search(request("said:"))
        assertTrue(results.total.toString(), results.total > 1)
        assertEveryPreviewMatches(results, "said:")
        assertFalse(results.truncatedByTime)
    }

    @Test fun bracketPatternIsARegexClassNotAPassageTerm() {
        val results = search(request("[Gg]od said"))
        assertTrue(results.total.toString(), results.total > 1)
        assertEveryPreviewMatches(results, "[Gg]od said")
    }

    @Test fun colonPhraseFindsExactlyGenesis1v3() {
        assertEquals(listOf("Genesis 1:3"), refsOf(search(request("light: and there was light"))))
    }

    @Test fun blankPatternIsEmpty() {
        assertEquals(MultiSearchResults.EMPTY, search(request("  ")))
    }

    @Test fun invalidPatternThrowsTheLocalizedMessage() {
        try {
            search(request("(unclosed"))
            fail("expected SearchSyntaxException")
        } catch (e: SearchSyntaxException) {
            assertTrue(e.message, e.message!!.startsWith("INVALID:"))
        }
    }

    private fun isNt(ref: String) = (kjv.getKey(ref).first() as org.crosswire.jsword.passage.Verse).book >= org.crosswire.jsword.versification.BibleBook.MATT

    @Test fun sectionsPartitionTheResults() {
        val all = refsOf(search(request("said:")))
        val nt = refsOf(search(request("said:", SearchBibleSection.NEW_TESTAMENT)))
        val ot = refsOf(search(request("said:", SearchBibleSection.OLD_TESTAMENT)))
        assertTrue(nt.isNotEmpty())
        assertTrue(ot.isNotEmpty())
        assertTrue(nt.toString(), nt.all { isNt(it) })
        assertTrue(ot.toString(), ot.none { isNt(it) })
        assertEquals(all.toSet(), (nt + ot).toSet())
        assertEquals(all.size, nt.size + ot.size)
    }

    @Test fun unknownCurrentBookSkipsTheBookInsteadOfAbortingTheSearch() {
        val r = request("said:", SearchBibleSection.CURRENT_BOOK).copy(currentBookName = "Nonexistent Book")
        assertEquals(MultiSearchResults.EMPTY.total, search(r).total)
    }

    @Test fun cancellingTheCallerReturnsPromptly() {
        val finished = runBlocking {
            val job = launch(Dispatchers.Default) {
                service().searchMulti(request("(?:\\w|\\s)*zzz(?:\\w|\\s)*qqq"), 1.hours)
            }
            delay(50)
            withTimeoutOrNull(5.seconds) { job.cancelAndJoin(); true }
        }
        assertEquals(true, finished)
    }

    @Test fun exhaustedBudgetReportsTruncation() {
        val results = search(request("light"), ZERO)
        assertTrue(results.truncatedByTime)
    }
}
