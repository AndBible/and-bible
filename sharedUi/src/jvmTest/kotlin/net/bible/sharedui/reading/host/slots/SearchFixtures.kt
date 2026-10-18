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
package net.bible.sharedui.reading.host.slots

import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.search.BibleOption
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.service.sword.BookAndKeyList
import net.bible.sharedui.strings.iosStrings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/** In-memory [AppSettings]; only strings are stored (the search state persists nothing else). */
class MapSearchSettings(val strings: MutableMap<String, String?> = mutableMapOf()) : AppSettings {
    override fun getString(key: String, default: String?) = strings[key] ?: default
    override fun getLong(key: String, default: Long) = default
    override fun getInt(key: String, default: Int) = default
    override fun getBoolean(key: String, default: Boolean) = default
    override fun getDouble(key: String, default: Double) = default
    override fun getFloat(key: String, default: Float) = default
    override fun setString(key: String, value: String?) { strings[key] = value }
    override fun setLong(key: String, value: Long?) = Unit
    override fun setInt(key: String, value: Int?) = Unit
    override fun setBoolean(key: String, value: Boolean?) = Unit
    override fun setDouble(key: String, value: Double?) = Unit
    override fun setFloat(key: String, value: Float?) = Unit
}

/**
 * A [Book] / [Key] stand-in: a JDK proxy answering the few members the search code reads, so a
 * test needs neither a SWORD module nor the Android-only `Book` extensions.
 */
fun fakeSearchBook(initials: String, name: String = initials): Book {
    val keys = mutableMapOf<String, Key>()
    lateinit var self: Book
    self = Proxy.newProxyInstance(Book::class.java.classLoader, arrayOf(Book::class.java)) { _, m, args ->
        when (m.name) {
            "getInitials" -> initials
            "getName" -> name
            "getAbbreviation" -> initials
            "getKey" -> keys.getOrPut(args!![0] as String) { fakeKey(args[0] as String) }
            "toString" -> "FakeBook($initials)"
            "hashCode" -> System.identityHashCode(self)
            "equals" -> args!![0] === self
            else -> null
        }
    } as Book
    return self
}

private fun fakeKey(name: String): Key =
    Proxy.newProxyInstance(Key::class.java.classLoader, arrayOf(Key::class.java)) { p, m, args ->
        when (m.name) {
            "getName" -> name
            "toString" -> name
            "hashCode" -> name.hashCode()
            "equals" -> args!![0] === p
            else -> null
        }
    } as Key

class FakeSearchPlatform : ReadingSearchPlatform {
    var current: Book? = fakeSearchBook("KJV")
    var info: SearchDocumentInfo? = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, isEpub = false, indexDone = true)
    val documents = mutableMapOf<String, Book>()
    var bibles: List<Pair<String, String>> = listOf("KJV" to "KJV")
    var referenceOpens = false
    val shown = mutableListOf<Pair<Book, Key>>()
    val linkWindows = mutableListOf<BookAndKeyList>()

    override fun currentDocument() = current
    override fun currentBookName() = "Genesis"
    override fun documentInfo(book: Book?) = if (book == null) null else info
    override fun documentByInitials(initials: String) = documents[initials]
    override fun swordBookByInitials(initials: String) = documents[initials]
    override fun installedBibles() = bibles
    override fun tryOpenReference(query: String) = referenceOpens
    override fun showInActiveWindow(book: Book, key: Key) { shown += book to key }
    override fun showInLinksWindow(list: BookAndKeyList) { linkWindows += list }
}

class FakeSearchIndexJobs : SearchIndexJobs {
    var subscribers = 0
        private set
    var subscribeCalls = 0
        private set
    private val listeners = mutableListOf<(Any, () -> Boolean) -> Unit>()
    var running = false

    override fun subscribe(onEvent: (job: Any, isFinished: () -> Boolean) -> Unit): () -> Unit {
        subscribeCalls++
        subscribers++
        listeners += onEvent
        var removed = false
        return {
            if (!removed) { removed = true; subscribers--; listeners -= onEvent }
        }
    }

    override fun snapshot(): List<ProgressJob> = emptyList()
    override fun isEveryJobFinished() = !running

    /** Delivers a work event for [job] to every live subscriber. */
    fun emit(job: Any, finished: Boolean) = listeners.toList().forEach { it(job, { finished }) }
}

class FakeBibleSearch(var unindexed: Set<String> = emptySet()) : BibleSearchService {
    val persisted = mutableListOf<List<String>>()
    override fun validateIndex(request: SearchRequest) = true
    override fun decorate(request: SearchRequest) = request.query
    override suspend fun searchMulti(request: SearchRequest) = MultiSearchResults.EMPTY
    override fun containsNonScripture() = false
    override fun isCurrentlyShowingScripture() = true
    override fun candidateBibles(): List<BibleOption> = emptyList()
    override fun persistSelection(translationIds: List<String>, strongsSearch: Boolean) { persisted += translationIds }
    override fun unindexedAmong(translationIds: List<String>) = translationIds.filter { it in unindexed }
}

class FakeEpubSearch : EpubSearchService {
    override fun isIndexed(docId: String) = true
    override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode): List<EpubResultRow> = emptyList()
}

class FakeIndexService : SearchIndexService {
    val created = mutableListOf<String>()
    override fun hasIndex(docId: String) = false
    override fun createIndex(docId: String) { created += docId }
}

/** A [ReadingSearchState] over the fakes above; [platform], [jobs] and the services stay reachable. */
class SearchFixture(
    val platform: FakeSearchPlatform = FakeSearchPlatform(),
    val jobs: FakeSearchIndexJobs = FakeSearchIndexJobs(),
    val bible: FakeBibleSearch = FakeBibleSearch(),
    val index: FakeIndexService = FakeIndexService(),
    val settings: MapSearchSettings = MapSearchSettings(),
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    var leftFullScreen = 0
    val state = ReadingSearchState(
        settings = settings,
        scope = scope,
        platform = platform,
        indexJobs = jobs,
        services = ReadingSearchServices(bible, FakeEpubSearch(), SearchResultsCache(), index),
        strings = { iosStrings("en") },
        onLeaveFullScreen = { leftFullScreen++ },
        pollPause = {},
    )
}
