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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidCoreStrings
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import net.bible.android.activity.R

/** The unsupported-syntax message comes through the [CoreStrings] seam, not an Android [Context]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BibleSearchServiceImplSyntaxTest {
    private val kjv get() = Books.installed().getBook("KJV")!!

    @Before fun index() { FtsTestSupport.ensureIndexed(kjv) }
    @After fun cleanUp() { FtsTestSupport.dropIndex(kjv) }

    private val request = SearchRequest(
        query = "faith^2", searchType = SearchType.ALL_WORDS, bibleSection = SearchBibleSection.ALL,
        currentBookName = "Genesis", translationIds = listOf("KJV"),
    )

    private fun messageFor(strings: CoreStrings): String {
        val koin = GlobalContext.get()
        val service = BibleSearchServiceImpl(
            koin.get<SearchControl>(), koin.get<SearchTextHighlighter>(), mock<AppSettings>(), strings,
        )
        try {
            runBlocking { service.searchMulti(request) }
        } catch (e: SearchSyntaxException) {
            return e.message!!
        }
        fail("expected SearchSyntaxException")
        throw AssertionError()
    }

    @Test fun unsupportedSyntaxMessageComesFromCoreStrings() {
        val fake = object : CoreStrings by mock<CoreStrings>() {
            override fun searchUnsupportedSyntax(syntax: String) = "X:$syntax"
        }
        assertEquals("X:^", messageFor(fake))
    }

    @Test fun realAndroidCoreStringsFollowTheInAppLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resources = context.resources
        val original = android.content.res.Configuration(resources.configuration)
        try {
            val finnish = android.content.res.Configuration(original).apply { setLocale(java.util.Locale.forLanguageTag("fi")) }
            resources.updateConfiguration(finnish, resources.displayMetrics)
            assertEquals(
                context.getString(R.string.search_unsupported_syntax, "^"),
                messageFor(AndroidCoreStrings(context)),
            )
        } finally {
            resources.updateConfiguration(original, resources.displayMetrics)
        }
    }
}
