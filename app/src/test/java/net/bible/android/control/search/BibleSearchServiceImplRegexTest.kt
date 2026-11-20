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
import kotlinx.coroutines.runBlocking
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

    @Test fun colonAndBracketPatternReachTheRegexUnchanged() {
        // ':' is stripped and '[...]' is a passage/range term on the Lucene path; here both are regex syntax.
        val results = search(request("light\\s*:\\s*and there was [l]ight"))
        val rows = results.main
        assertTrue(rows.map { it.referenceName }.toString(), rows.any { it.referenceName.contains("1:3") })
        val regex = Regex("light\\s*:\\s*and there was [l]ight", RegexOption.IGNORE_CASE)
        rows.forEach { row ->
            val preview = row.primaryPreview
            assertTrue(preview.plainText(), regex.containsMatchIn(preview.plainText()))
            assertTrue(preview.runs.any { it.highlight })
        }
        assertFalse(results.truncatedByTime)
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

    @Test fun newTestamentSectionDropsOldTestamentVerses() {
        val all = search(request("light\\s*:\\s*and there was [l]ight")).main.map { it.referenceName }
        assertTrue(all.toString(), all.any { it.startsWith("Gen") || it.contains("Gen") })
        val nt = search(request("light\\s*:\\s*and there was [l]ight", SearchBibleSection.NEW_TESTAMENT))
        assertTrue(nt.main.map { it.referenceName }.toString(), nt.main.isEmpty())
        val ot = search(request("light\\s*:\\s*and there was [l]ight", SearchBibleSection.OLD_TESTAMENT))
        assertEquals(all, ot.main.map { it.referenceName })
    }

    @Test fun exhaustedBudgetReportsTruncation() {
        val results = search(request("light"), ZERO)
        assertTrue(results.truncatedByTime)
    }
}
