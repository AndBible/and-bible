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
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
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

    // The KJV index is built by the first test and reused by the rest of the class (building it takes tens of seconds); the
    // index file is removed once, after the last test. A test that damages the index must rebuild it itself.
    @Before fun index() {
        FtsTestSupport.ensureIndexed(kjv)
        indexFile = FtsTestSupport.indexFile(kjv)
    }

    private companion object {
        var indexFile: java.io.File? = null

        @JvmStatic @org.junit.AfterClass fun dropIndexOnce() {
            indexFile?.delete()
            indexFile = null
        }
    }

    private val strings = object : CoreStrings by mock<CoreStrings>() {
        override fun searchInvalidRegex(engineMessage: String) = "INVALID:$engineMessage"
        override val searchRegexNestedRepetition get() = "NESTED"
        override val searchRegexBusy get() = "BUSY"
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

    private fun isNt(ref: String) = (kjv.getKey(ref).first() as Verse).book >= BibleBook.MATT

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
        // Precondition: the restriction key really cannot be resolved for the fixture book (a LucidException,
        // not a BookException), otherwise the assertion below proves nothing.
        try {
            kjv.getKey("Nonexistent Book")
            fail("expected NoSuchKeyException")
        } catch (expected: NoSuchKeyException) {
        }
        val r = request("said:", SearchBibleSection.CURRENT_BOOK).copy(currentBookName = "Nonexistent Book")
        assertEquals(MultiSearchResults.EMPTY.total, search(r).total)
    }

    @Test fun cancellingTheCallerReturnsPromptly() {
        // `^.*.*.*.*.*.*.*.*!` (no group, so the nested-repetition pre-check lets it through) takes ~n^8 steps on any verse
        // without a '!', so with a 1 h budget the scan can only end through the caller's cancellation (the budget's
        // isCancelled).
        val outcome = runBlocking {
            val job = launch(Dispatchers.Default) {
                service().searchMulti(request("^.*.*.*.*.*.*.*.*!"), 1.hours)
            }
            delay(500)
            val runningBeforeCancel = job.isActive
            val returned = withTimeoutOrNull(5.seconds) { job.cancelAndJoin(); true }
            runningBeforeCancel to returned
        }
        assertTrue("the scan must still be running when it is cancelled", outcome.first)
        assertEquals("cancelling must end the scan within the bound", true, outcome.second)
    }

    /** KJV under another category: the same searchable index, so only the category decides. */
    private fun kjvAs(category: BookCategory): Book {
        val k = kjv as SwordBook
        return object : SwordBook(k.bookMetaData as SwordBookMetaData, k.backend) {
            override val bookCategory: BookCategory get() = category
        }
    }

    private fun regexScanOf(book: Book) = runBlocking {
        GlobalContext.get().get<SearchControl>().getMultiRegexResults(
            listOf("X"), Regex("said:"), SearchControl.SearchBibleSection.ALL, null, bookLookup = { book },
        )
    }

    @Test fun onlyBiblesAreRegexSearched() {
        assertTrue("the stand-in Bible must be searchable", regexScanOf(kjvAs(BookCategory.BIBLE)).size > 1)
        assertEquals(0, regexScanOf(kjvAs(BookCategory.COMMENTARY)).size)
        assertEquals(0, regexScanOf(kjvAs(BookCategory.GENERAL_BOOK)).size)
    }

    @Test fun nestedRepetitionIsRefusedBeforeAnythingRuns() {
        for (pattern in listOf("(\\w+\\s?)+:", "(.*a){20}", "(a*)*b")) {
            try {
                search(request(pattern))
                fail("expected SearchSyntaxException for $pattern")
            } catch (e: SearchSyntaxException) {
                assertEquals("NESTED", e.message)
            }
        }
        // Everyday patterns still run.
        assertTrue(search(request("(Lord|God) said")).total > 0)
    }

    @Test fun aFailingIndexIsAnErrorNotAnEmptyResult() {
        val index = org.crosswire.jsword.index.IndexManagerFactory.getIndexManager().getIndex(kjv) as org.crosswire.jsword.index.fts.FtsIndex
        // A closed index without a successor fails every scan; the search must say so instead of finding "nothing".
        index.close()
        try {
            search(request("said:"))
            fail("expected the failure of the only translation to surface")
        } catch (e: org.crosswire.jsword.book.BookException) {
            // surfaced
        } finally {
            // The cached index is closed: rebuild it for the tests that follow.
            FtsTestSupport.dropIndex(kjv)
            FtsTestSupport.ensureIndexed(kjv)
        }
    }

    @Test fun exhaustedBudgetReportsTruncation() {
        val results = search(request("light"), ZERO)
        assertTrue(results.truncatedByTime)
    }
}
