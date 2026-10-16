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

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Unsupported query syntax (K6-3) reaches the user, and the AI tool, as an explanatory message. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleSearchServiceUnsupportedSyntaxTest {
    private val kjv get() = Books.installed().getBook("KJV")!!

    @Before fun index() { FtsTestSupport.ensureIndexed(kjv) }
    @After fun cleanUp() { FtsTestSupport.dropIndex(kjv) }

    @Test fun serviceThrowsTheLocalizedUnsupportedSyntaxMessage() {
        val service = GlobalContext.get().get<BibleSearchService>()
        val request = SearchRequest(
            // the app strips colons from typed queries (SearchControl.cleanSearchString), so a boost is the typed form
            query = "faith^2", searchType = SearchType.ALL_WORDS, bibleSection = SearchBibleSection.ALL,
            currentBookName = "Genesis", translationIds = listOf("KJV"),
        )
        try {
            runBlocking { service.searchMulti(request) }
            fail("expected an exception")
        } catch (e: SearchSyntaxException) {
            assertTrue(e.message, e.message!!.startsWith("This search uses syntax that is not supported:"))
            assertTrue(e.message, e.message!!.contains("^"))
        }
    }

    @Test fun searchToolReturnsTheUnsupportedSyntaxText() {
        val result = runBlocking {
            net.bible.service.llm.tools.read.SearchBibleTool.execute(
                JsonObject(mapOf("query" to JsonPrimitive("heading:x"), "books" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("KJV"))))),
                net.bible.service.llm.agent.AgentContext(promptId = net.bible.android.database.IdType()),
            )
        }
        val error = result as net.bible.service.llm.tools.ToolResult.Error
        assertTrue(error.message, error.message.startsWith("Unsupported search syntax"))
        assertTrue(error.message, error.message.contains("heading"))
    }

    @Test fun walksTheCauseChain() {
        val inner = org.crosswire.jsword.index.fts.UnsupportedSearchSyntaxException("^2")
        val wrapped = org.crosswire.jsword.book.BookException("Failed to initialize the search index", RuntimeException("x", inner))
        assertEquals("^2", unsupportedSyntaxIn(wrapped)?.syntax)
        assertEquals(null, unsupportedSyntaxIn(RuntimeException("plain")))
    }
}
