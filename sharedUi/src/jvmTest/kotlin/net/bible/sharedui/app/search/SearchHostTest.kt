package net.bible.sharedui.app.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.control.search.SearchControl
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SearchHostTest {
    private var openReadingCalls = 0
    private val shown = mutableListOf<String>()
    private val strings: Strings = iosStrings("en")

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() { openReadingCalls++ }
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    /** A [Book] whose `getKey` yields a [Key] named after the requested reference; every other member throws. */
    private fun fakeBook(initials: String, unresolvable: Set<String> = emptySet()): Book =
        Proxy.newProxyInstance(Book::class.java.classLoader, arrayOf(Book::class.java)) { proxy, method, args ->
            when (method.name) {
                "getKey" -> {
                    val name = args[0] as String
                    if (name in unresolvable) throw IllegalArgumentException("no such key $name")
                    Proxy.newProxyInstance(Key::class.java.classLoader, arrayOf(Key::class.java)) { _, m, _ ->
                        if (m.name == "getName") name else throw UnsupportedOperationException(m.name)
                    }
                }
                "getInitials" -> initials
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                "toString" -> "FakeBook($initials)"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Book

    private fun host(books: Map<String, Book> = emptyMap()) = SearchHost(
        shell = shell,
        strings = strings,
        appSettingsProvider = { error("settings must stay lazy") },
        userNotifierProvider = { error("notifier must stay lazy") },
        notificationPermission = object : NotificationPermission {
            override suspend fun request() = Unit
        },
        links = object : ExternalLinks {
            override val askBeforeOpening = false
            override fun open(url: String) = Unit
        },
        searchControlProvider = { error("searchControl must stay lazy") },
        bibleSearchServiceProvider = { error("bibleSearchService must stay lazy") },
        searchIndexServiceProvider = { error("searchIndexService must stay lazy") },
        windowControlProvider = { error("windowControl must stay lazy") },
        pageControlProvider = { error("pageControl must stay lazy") },
        searchResultsCacheProvider = { error("searchResultsCache must stay lazy") },
        epubSearchServiceProvider = { error("epubSearchService must stay lazy") },
        linkControlProvider = { error("linkControl must stay lazy") },
        fakeBookFactoryProvider = { error("fakeBookFactory must stay lazy") },
        historyManagerProvider = { error("history must stay lazy") },
        bookByInitials = { books[it] },
        showInReadingOverride = { book, key -> shown += "${book.initials}:${key.getName()}" },
    )

    @Test
    fun epubResultsTitleShowsPlusOverCap() {
        val host = host()
        val cap = SearchControl.MAX_SEARCH_RESULTS
        assertEquals(strings.searchWithResults2("$cap+", "KJV"), host.epubSearchResultsTitle(cap + 1, "KJV"))
        assertEquals(strings.searchWithResults2("3", "KJV"), host.epubSearchResultsTitle(3, "KJV"))
        assertEquals(strings.searchWithResults2("$cap", "KJV"), host.epubSearchResultsTitle(cap, "KJV"))
    }

    @Test
    fun openSearchResultReturnsToReading() {
        val host = host(mapOf("KJV" to fakeBook("KJV", unresolvable = setOf("Nowhere 99:1"))))
        host.openSearchResult("John 3:16", "KJV", emptyList())
        assertEquals(1, openReadingCalls)
        assertEquals(listOf("KJV:John 3:16"), shown)

        // A reference the book cannot resolve: no navigation, no throw.
        host.openSearchResult("Nowhere 99:1", "KJV", emptyList())
        assertEquals(1, openReadingCalls)
        // No resolvable book at all.
        host.openSearchResult("John 3:16", "ESV", emptyList())
        assertEquals(1, openReadingCalls)
        assertNotNull(shown.singleOrNull())
    }

    @Test
    fun resultBookFallsBackToFirstSelectedTranslation() {
        val host = host(mapOf("KJV" to fakeBook("KJV")))
        host.openSearchResult("John 3:16", null, listOf("KJV", "ESV"))
        assertEquals(1, openReadingCalls)
    }
}
