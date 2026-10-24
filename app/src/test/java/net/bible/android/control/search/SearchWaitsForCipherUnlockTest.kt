/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A restored search-results route re-runs the search by itself; its previews read the hit books. While the startup
 * cipher unlock is held, `searchMulti` must not touch the books (a locked read yields an empty/garbage preview that is
 * cached) and must deliver real previews once the unlock ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchWaitsForCipherUnlockTest {
    private val release = CountDownLatch(1)
    private val entered = CountDownLatch(1)
    private val kjv get() = Books.installed().getBook("KJV")!!

    @After fun tearDown() {
        release.countDown()
        ReadingAppInit.resetForTest()
        FtsTestSupport.dropIndex(kjv)
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    @Test fun searchIssuedWhileTheUnlockIsHeldWaitsAndThenReturnsPreviews() {
        FtsTestSupport.ensureIndexed(kjv)
        ReadingAppInit.resetForTest()
        // The job takes the unlock mutex and asks hasInstalledBible first: hold it there (no DB rows, no host swap).
        ReadingAppInit.installCipherSeamsForTest(hasInstalledBible = {
            entered.countDown()
            release.await(120, TimeUnit.SECONDS)
            true
        })
        ReadingAppInit.unlockStoredCipherKeysInBackground()
        assertTrue(entered.await(30, TimeUnit.SECONDS))

        val koin = GlobalContext.get()
        val service = BibleSearchServiceImpl(
            koin.get<SearchControl>(), koin.get<SearchTextHighlighter>(), mock<AppSettings>(), mock<CoreStrings>(),
        )
        val request = SearchRequest(
            query = "faith", searchType = SearchType.ALL_WORDS, bibleSection = SearchBibleSection.ALL,
            currentBookName = "Genesis", translationIds = listOf("KJV"),
        )
        val result = CompletableDeferred<MultiSearchResults>()
        val searcher = Thread { runBlocking { result.complete(service.searchMulti(request)) } }.apply { start() }
        // An ungated search finishes in a few seconds even cold; the gated one cannot finish while held.
        val finishedWhileHeld = runBlocking { kotlinx.coroutines.withTimeoutOrNull(15_000) { result.await() } }
        assertTrue("the search ran (and read the books) while the cipher unlock was held", finishedWhileHeld == null)

        release.countDown()
        val found = runBlocking { withTimeout(60_000) { result.await() } }
        searcher.join(10_000)
        assertTrue("expected hits for 'faith'", found.main.isNotEmpty())
    }
}
