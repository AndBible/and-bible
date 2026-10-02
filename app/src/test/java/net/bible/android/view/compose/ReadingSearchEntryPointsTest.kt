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
package net.bible.android.view.compose

import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.MenuCommandHandler
import net.bible.android.view.activity.page.SearchSheetOffsetsUpdated
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.sharedcore.search.MultiSearchResults
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.SearchType
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.passage.Verse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import net.bible.android.control.search.SearchIndexServiceImpl
import net.bible.sharedcore.search.SearchIndexService
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * F6 Task 8b — the six entry points that hand SWORD search to the reading view when a Compose host
 * is mounted, and the flag-OFF guarantee that every one of them still produces the classic `Intent`
 * with no host installed. Also covers Step 2 (a result tap navigates without an Activity round trip)
 * and the [ComposeReadingViewHost.buildSearchRequest] one-shot decoration overrides that make entry
 * points 7 and 8 correct (see their kdoc).
 *
 * Built the same way as [net.bible.android.view.activity.page.OptionsMenuStateBuilderTest] /
 * [net.bible.android.view.activity.page.screen.MainBibleActivityHandleWindowPaneMenuItemTest]: a
 * REAL reading-route [NavHostComposeActivity]/[WindowControl]/[WindowRepository] graph (Robolectric +
 * [TestBibleApplication]), activity built WITHOUT `.create()`, host constructed directly and never
 * `.install()`ed. No "recording fake host" substitute is used for entry points 2/4/6/8's real call
 * sites — a REAL [ComposeReadingViewHost] is cheap here (same pattern those two test files already
 * establish) and its own `searchController` state IS the recording. Entry points 5
 * ([net.bible.android.view.activity.page.BibleJavascriptInterface]'s Ctrl+F) and 7
 * ([net.bible.android.view.activity.page.BibleView]'s selection "Search…") are NOT exercised through
 * their own files: both are one-line calls to [ReadingCommands.composeSearchIfHosted] (7 passes
 * `preDecorated = true`), and neither `BibleJavascriptInterface` nor `BibleView` (a `WebView`
 * subclass) is constructible in this test suite today (no existing test does so) — their logic is
 * exactly what [composeSearchIfHostedSeedsAPreDecoratedQueryUnchanged] /
 * [composeSearchIfHostedReturnsFalseWhenNoHostIsInstalled] cover, and their call sites are two-line
 * diffs, readable by inspection (see the task report for this noted as a scoping decision, not an
 * oversight).
 *
 * Entry point 8's REAL call site ([LinkControl.showAllOccurrences]) can only be driven through its
 * classic branch here: `checkStrongs` needs a genuine Lucene index on disk, which this test's KJV
 * fixture module does not have (confirmed: `~/.sword` ships the KJV text but no search index), so
 * `needToIndex` is always `true` and the host is never even consulted — which is itself a correct,
 * useful assertion (the not-indexed branch must stay classic even when hosted, per the task's
 * resolution 3). Entry point 8's HOSTED behaviour ([ComposeReadingViewHost.openSearchStrongs]'s raw
 * query + forced `ANY_WORDS`/`isStrongsSearch`) is covered directly instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingSearchEntryPointsTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity

    /** The one handler per host [ReadingCommands] builds, constructed the same way (named arguments). */
    private fun menuCommandHandler() = MenuCommandHandler(
        hostActivity = activity,
        composeReadingViewHost = { activity.composeReadingViewHost },
        composeSearchIfHosted = { activity.readingCommands.composeSearchIfHosted() },
    )

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        // The classic fallback branches call startActivity/startActivityForResult, which need
        // ActivityBase.historyTraversal primed (normally done in onCreate()) — same minimal-boot
        // step MainBibleActivityHandleWindowPaneMenuItemTest.setUp takes for the same reason.
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)

        setKjvAsCurrentDocument()
    }

    @After
    fun tearDown() {
        CurrentActivityHolder.deactivate(activity)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    /** Builds a real [ComposeReadingViewHost] against the test [activity] (never `.install()`ed —
     *  see the class kdoc's precedent). */
    private fun host() = ComposeReadingViewHost(activity)

    /**
     * KJV ships `Feature=StrongsNumbers` (verified against this environment's `~/.sword` fixture),
     * so it is searchable AND a valid Strong's Bible, but carries no Lucene index — every entry
     * point below needs SOME current document (`documentControl.currentDocument`, `currentPage`)
     * to get past its own early-return guards.
     */
    private fun setKjvAsCurrentDocument() {
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
    }

    /**
     * A fake Bible with its `indexStatus` set directly to `DONE` (`NullBackend`, no real Lucene
     * index on disk) — same construction `ReadingSearchHostTest.book()` uses. Needed only by the
     * `openSearchWith...` real-path test below: `ReadingSearchController.open` must resolve
     * `SearchKind.Bible` (not `NeedsIndex`) to ever reach `buildSearchRequest` at all, and KJV
     * itself (see [setKjvAsCurrentDocument]'s kdoc) carries no real index in this environment.
     */
    private fun indexedFakeBible(initials: String): SwordBook {
        val conf = """
            [$initials]
            Description=$initials
            Abbreviation=$initials
            Category=Biblical Texts
            ModDrv=RawText
            DataPath=./modules/texts/ztext/$initials/
            Encoding=UTF-8
            Versification=KJV
        """.trimIndent()
        return SwordBook(SwordBookMetaData(conf.toByteArray(), initials), NullBackend()).apply {
            indexStatus = IndexStatus.DONE
        }
    }

    /**
     * F43 Task 6: makes the active window's page an EPUB — a `GENERAL_BOOK`-category document,
     * same fixture construction `ReadingSearchHostTest.book()` uses (`NullBackend`, `Category=Generic
     * Books`/`ModDrv=RawGenBook`, `AndBibleEpubModule=1`). `currentPage`'s setter is private, so the
     * documented way to switch pages is `setCurrentDocumentAndKey` on the `CurrentPageManager` itself
     * — exactly the pattern `menuSearchButtonDoesNothingOnANonSearchablePageEvenWhenHosted` above uses
     * for `FakeBookFactory.myNotesDocument`: `getBookPage` maps the book's category (here GENERAL_BOOK)
     * to `currentGeneralBook`. The key is a plain `Verse`, same as every other fixture in this class —
     * nothing under test reads it, only `documentControl.currentDocument`.
     *
     * The book must be registered with `Books.installed()` (same as [indexedFakeBible]'s callers do):
     * `CurrentPageBase.currentDocument`'s getter treats any book NOT found there as removed
     * (`Book.isRemoved`, `FakeBookFactory.kt:215`) and silently falls back to the category default —
     * which for a never-installed `GENERAL_BOOK` is `null`, making `documentControl.currentDocument`
     * null and every assertion below fail for a reason that has nothing to do with the fix under test.
     * The caller MUST remove it again (`Books.installed().removeBook(...)`, in a `finally`) — this
     * registry is process-global, and a leaked "TestEpub" would leak into later tests.
     */
    private fun givenCurrentDocumentIsAnEpub(): Book {
        val conf = """
            [TestEpub]
            Description=TestEpub
            Abbreviation=TestEpub
            Category=Generic Books
            ModDrv=RawGenBook
            DataPath=./modules/genbook/TestEpub/
            Encoding=UTF-8
            AndBibleEpubModule=1
        """.trimIndent()
        val epub = SwordBook(SwordBookMetaData(conf.toByteArray(), "TestEpub"), NullBackend())
        Books.installed().addBook(epub)
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        windowRepository.activeWindow.pageManager.setCurrentDocumentAndKey(epub, verse)
        return epub
    }

    // ---- The shared guard (entry points 2, 4, 5, 6, 7's exact logic) ---------------------------

    @Test fun composeSearchIfHostedOpensSearchModeOnTheHost() {
        activity.composeReadingViewHost = host()

        val handled = activity.readingCommands.composeSearchIfHosted()

        assertTrue(handled)
        assertTrue(activity.composeReadingViewHost!!.searchController.searchModeActive.value)
    }

    @Test fun composeSearchIfHostedReturnsFalseWhenNoHostIsInstalled() {
        assertNull(activity.composeReadingViewHost, "sanity: fallback path")

        assertFalse(activity.readingCommands.composeSearchIfHosted())
    }

    /**
     * Entry point 7's exact call ([BibleView]'s selection "Search…" passes `preDecorated = true`).
     * The seed reaches the toolbar's query field verbatim — [ComposeReadingViewHost.openSearch]
     * itself does no decorating; only [ComposeReadingViewHost.buildSearchRequest] (covered below)
     * treats it specially once a search actually runs.
     */
    @Test fun composeSearchIfHostedSeedsAPreDecoratedQueryUnchanged() {
        activity.composeReadingViewHost = host()
        val decorated = " \"grace\""

        val handled = activity.readingCommands.composeSearchIfHosted(decorated, preDecorated = true)

        assertTrue(handled)
        assertEquals(decorated, activity.composeReadingViewHost!!.searchController.queries.query.value)
    }

    @Test fun composeSearchStrongsIfHostedReturnsFalseWhenNoHostIsInstalled() {
        assertFalse(activity.readingCommands.composeSearchStrongsIfHosted("H430", listOf("KJV")))
    }

    /**
     * F43: with an EPUB open, pressing search did NOTHING — the host opted EPUB out, and the
     * `getSearchIntent` fallback returns null for a general book, which the caller's `?.let` drops
     * in silence. This is the assertion whose absence let that ship.
     */
    @Test fun composeSearchIfHostedOpensSearchModeForAnEpub() {
        activity.composeReadingViewHost = host()
        val epub = givenCurrentDocumentIsAnEpub()
        try {
            val handled = activity.readingCommands.composeSearchIfHosted()

            assertTrue(handled, "an EPUB must be handled by the reading-view search, not dropped")
            assertTrue(activity.composeReadingViewHost!!.searchController.searchModeActive.value)
            // The stronger assertion: not just "some search mode", but the exact EPUB NeedsIndex phase
            // (this fixture's EPUB has a null `epubBackend`, so `documentIndexDone` reports it
            // unindexed — the precise F43 scenario, an unindexed EPUB reaching the in-sheet index
            // prompt).
            assertEquals(
                ReadingSearchPhase.NeedsIndex("TestEpub", forEpub = true),
                activity.composeReadingViewHost!!.searchController.phase.value,
            )
        } finally {
            Books.installed().removeBook(epub)
        }
    }

    /**
     * Strong's is Bible-only and used to be guarded by the SAME predicate that excluded EPUBs.
     * Removing that predicate must not let a Strong's find-all open over an EPUB.
     *
     * F43 Task 6 fix round 1: the RETURN VALUE assertion is the whole point of this test. The first
     * cut of this fix let `composeSearchStrongsIfHosted` return `true` unconditionally whenever a
     * host was mounted, so `LinkControl.showAllOccurrences` (`ref: MainBibleActivity.kt:1250`, itself
     * called from `LinkControl.kt:388-393`) treated the EPUB decline as "handled" and never fell
     * through to the classic Strong's search — the find-all did NOTHING at all, worse than before
     * this batch (which correctly ran the classic search, since Strong's find-all searches
     * Strong's-enabled BIBLES, not the open document). Asserting only `searchModeActive` stays false
     * would NOT have caught that regression — a `true` return with search mode still closed is
     * exactly what the regression produced.
     */
    @Test fun composeSearchStrongsIfHostedRefusesAnEpub() {
        activity.composeReadingViewHost = host()
        val epub = givenCurrentDocumentIsAnEpub()
        try {
            val handled = activity.readingCommands.composeSearchStrongsIfHosted("H430", listOf("KJV"))

            assertFalse(handled, "an EPUB decline must be reported to the caller so it can fall back")
            assertFalse(activity.composeReadingViewHost!!.searchController.searchModeActive.value,
                "Strong's must not open a search over an EPUB")
        } finally {
            Books.installed().removeBook(epub)
        }
    }

    /**
     * The companion to the test above: with a normal (non-EPUB) document current, Strong's find-all
     * must still open and report `true` — otherwise an implementation that always returns `false`
     * would pass the EPUB-refusal test above for the wrong reason.
     */
    @Test fun composeSearchStrongsIfHostedOpensForANonEpubDocument() {
        activity.composeReadingViewHost = host()
        // sanity: KJV (set in setUp) is not an EPUB.
        assertFalse(GlobalContext.get().get<DocumentControl>().currentDocument?.isEpub == true, "sanity")

        val handled = activity.readingCommands.composeSearchStrongsIfHosted("H430", listOf("KJV"))

        assertTrue(handled, "a non-EPUB document must open the Strong's search")
        assertTrue(activity.composeReadingViewHost!!.searchController.searchModeActive.value)
    }

    // ---- The one-shot decoration overrides (why entry points 7 and 8 are correct) --------------

    /**
     * Default case (no entry point 7/8 seed active): the live settings-sheet word-mode/section
     * apply — `ALL_WORDS`/`ALL` are [ComposeReadingViewHost]'s own defaults.
     */
    @Test fun buildSearchRequestUsesLiveSettingsByDefault() {
        val host = host()

        val request = host.buildSearchRequest("KJV", "grace")

        assertEquals(SearchType.ALL_WORDS, request.searchType)
        assertEquals(SearchBibleSection.ALL, request.bibleSection)
        assertFalse(request.isStrongsSearch)
        assertEquals("grace", request.query)
    }

    /**
     * The bug this guards: without the override, an ALREADY phrase-decorated multi-word string run
     * through `ALL_WORDS.decorate` a second time inserts a stray `+` inside the quotes (see
     * [ComposeReadingViewHost.openSearch]'s kdoc). Forcing identity decorators here is what makes
     * that impossible regardless of what the live settings sheet is set to.
     *
     * Review Critical 1 note: this drives `buildSearchRequest` with the SAME (untrimmed) `decorated`
     * string `openSearch` was seeded with, so — unlike production, where the controller always
     * `.trim()`s the query before it ever reaches `buildSearchRequest`
     * (`ReadingSearchController.enterFormOrResults`/`submit`/`settingsClosed`) — this alone could
     * not have caught a missing `.trim()` on the storage/comparison side: both sides here are
     * always equal by construction. [openSearchWithAPreDecoratedSeedReachesBuildSearchRequestWithIdentityDecoratorsThroughTheRealPath]
     * below is the one that actually exercises the trim boundary; this test is kept as direct
     * coverage of `buildSearchRequest`'s own logic once the flag is set.
     */
    @Test fun buildSearchRequestForcesIdentityDecoratorsForAPreDecoratedSeed() {
        val host = host()
        val decorated = " \"the Lord\""
        host.openSearch(decorated, preDecorated = true)

        val request = host.buildSearchRequest("KJV", decorated)

        assertEquals(SearchType.ANY_WORDS, request.searchType, "ANY_WORDS is the identity decorator")
        assertEquals(SearchBibleSection.ALL, request.bibleSection, "ALL is the identity section term")
        assertFalse(request.isStrongsSearch, "the pre-decorated seed is not a Strong's search")
    }

    /**
     * The real production path: `ReadingSearchController` always hands `buildSearchRequest` a
     * `.trim()`med query (`enterFormOrResults`'s `queries.query.value.trim()`), while
     * [ComposeReadingViewHost.openSearch] originally stored the seed UNTRIMMED in
     * `searchPreDecoratedQuery` — so `query == searchPreDecoratedQuery` never matched for entry
     * point 7's seed, which always carries a leading space (`SearchControl.decorateSearchString`'s
     * `ALL`-section term joins with a literal `" "`). That is Critical 1: the override this whole
     * class of test exists for silently never fired, and the query would have been decorated
     * TWICE. Proven here through the real path — `openSearch`, not a hand-built
     * `buildSearchRequest` call — by pre-seeding the Koin-singleton [SearchResultsCache] (the SAME
     * instance `host.searchResults` reads) with a distinctively-marked result keyed on the request
     * the fix predicts; `SearchResultsController.run` checks the cache SYNCHRONOUSLY before ever
     * touching the real `BibleSearchService`/Lucene, so a cache HIT (the marker appearing) proves
     * the request `buildSearchRequest` actually produced equals the expected one byte-for-byte, with
     * no Koin override of `BibleSearchService` needed (which would risk leaking a swapped binding
     * into later tests the way the review's noted pre-existing `unloadKoinModules` bug does).
     */
    @Test fun openSearchWithAPreDecoratedSeedReachesBuildSearchRequestWithIdentityDecoratorsThroughTheRealPath() {
        val fakeBook = indexedFakeBible("PreDec")
        Books.installed().addBook(fakeBook)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(fakeBook, verse)
            val host = host()
            val decorated = " \"the Lord\""
            val marker = MultiSearchResults(main = emptyList(), other = emptyList(), total = 4242)
            val expectedRequest = SearchRequest(
                query = decorated.trim(),
                searchType = SearchType.ANY_WORDS,
                bibleSection = SearchBibleSection.ALL,
                translationIds = listOf("PreDec"),
                currentBookName = GlobalContext.get().get<SearchControl>().currentBookName,
                isStrongsSearch = false,
            )
            GlobalContext.get().get<SearchResultsCache>().put(expectedRequest, marker)

            host.openSearch(decorated, preDecorated = true)

            assertEquals(
                4242, host.searchResults.results.value.total,
                "the request buildSearchRequest actually produced through the real path must equal " +
                    "the one the fix predicts — a mismatch here means the cache missed and a REAL " +
                    "(uncontrolled) Lucene search ran instead",
            )
        } finally {
            // Review item 1: this cache is a Koin SINGLE, so a seeded entry is JVM-global — leaving it
            // behind is exactly the class of leakage that cost this batch two debugging sessions (a
            // swapped Koin binding, JSword's `BookName` flag).
            GlobalContext.get().get<SearchResultsCache>().clear()
            Books.installed().removeBook(fakeBook)
        }
    }

    /**
     * Review I2: a NEW query must open its results at the top — the screen this replaced created its
     * `LazyListState` per screen (`SearchResultsScreen.kt:103`), while the host hoists one so the
     * scroll survives closing and reopening the sheet (F25). Both halves are asserted here, since the
     * fix must not undo F25: a run replaces the state, reopening the sheet for the query the results
     * already belong to does not.
     *
     * The cache is seeded exactly as the pre-decorated test above does, so the "search" is a
     * synchronous cache hit and no real Lucene query runs.
     */
    @Test fun aNewQueryResetsTheResultsScrollWhileReopeningTheSheetKeepsIt() {
        val fakeBook = indexedFakeBible("ScrollDoc")
        Books.installed().addBook(fakeBook)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(fakeBook, verse)
            val host = host()
            val decorated = " \"the Lord\""
            GlobalContext.get().get<SearchResultsCache>().put(
                SearchRequest(
                    query = decorated.trim(),
                    searchType = SearchType.ANY_WORDS,
                    bibleSection = SearchBibleSection.ALL,
                    translationIds = listOf("ScrollDoc"),
                    currentBookName = GlobalContext.get().get<SearchControl>().currentBookName,
                    isStrongsSearch = false,
                ),
                MultiSearchResults(main = emptyList(), other = emptyList(), total = 7),
            )
            val beforeTheSearch = host.searchResultsListStateForTest

            host.openSearch(decorated, preDecorated = true)

            val afterTheSearch = host.searchResultsListStateForTest
            assertEquals(7, host.searchResults.results.value.total, "sanity: the seeded search ran")
            assertNotSame(
                beforeTheSearch, afterTheSearch,
                "a new query must get a fresh list state — otherwise three hits open clamped at the " +
                    "previous search's row 40 and look like an empty result",
            )

            // First back closes only the sheet; re-entering search with no seed serves the SAME
            // results (`ReadingSearchController.enterFormOrResults`' `resultsForQuery` branch) and so
            // must keep the scroll position F25 exists to preserve.
            assertTrue(host.closeSearchIfOpen(), "sanity: the sheet was open")
            host.openSearch()

            assertSame(
                afterTheSearch, host.searchResultsListStateForTest,
                "reopening the sheet for the same query must NOT reset the scroll (F25)",
            )
        } finally {
            GlobalContext.get().get<SearchResultsCache>().clear()
            Books.installed().removeBook(fakeBook)
        }
    }

    /** Editing the query after a pre-decorated open drops the override with no explicit clearing. */
    @Test fun buildSearchRequestDropsThePreDecoratedOverrideOnceTheQueryChanges() {
        val host = host()
        host.openSearch(" \"the Lord\"", preDecorated = true)

        val request = host.buildSearchRequest("KJV", "a different, user-edited query")

        assertEquals(SearchType.ALL_WORDS, request.searchType, "an edited query is no longer pre-decorated")
    }

    /**
     * Entry point 8: the RAW `strong:$ref` query, [SearchType.ANY_WORDS] forced (classic's own
     * reason: it "does not add anything"), [net.bible.sharedcore.search.SearchRequest.isStrongsSearch]
     * set, and (review item A) `ALL` forced for the section too — classic's ONLY caller of
     * `showAllOccurrences` always passes `ALL` (`BibleView.kt:1465`); applying the live
     * settings-sheet section instead would silently narrow a find-all to whatever OT/NT restriction
     * the user last left there, with no indication in the results.
     */
    @Test fun openSearchStrongsSeedsARawQueryAndForcesAnyWordsAndTheStrongsFlag() {
        val host = host()

        host.openSearchStrongs("H430", listOf("KJV", "WLC"))

        assertEquals("strong:H430", host.searchController.queries.query.value)
        val request = host.buildSearchRequest("KJV", "strong:H430")
        assertEquals(SearchType.ANY_WORDS, request.searchType)
        assertEquals(SearchBibleSection.ALL, request.bibleSection)
        assertTrue(request.isStrongsSearch)
        assertEquals(listOf("KJV", "WLC"), request.translationIds, "seeds the resolved Strong's-Bible selection")
    }

    /**
     * Review item A, the regression this guards against: with the settings sheet left restricted
     * to a non-`ALL` section (as it would be after ANY prior ordinary search that touched it),
     * Strong's find-all must still search `ALL` — not the leftover restriction.
     */
    @Test fun openSearchStrongsForcesAllEvenWhenTheSettingsSheetIsRestricted() {
        val host = host()
        host.searchSection.value = SearchBibleSection.NEW_TESTAMENT

        host.openSearchStrongs("H430", listOf("KJV"))

        val request = host.buildSearchRequest("KJV", "strong:H430")
        assertEquals(SearchBibleSection.ALL, request.bibleSection, "must override the live NEW_TESTAMENT restriction")
    }

    /** A re-run from the results document selector or the settings sheet is the SAME query text —
     *  the Strong's flag must survive it (task resolution 3: "no stale flag to clear by hand"). */
    @Test fun buildSearchRequestKeepsTheStrongsFlagAcrossARepeatedQuery() {
        val host = host()
        host.openSearchStrongs("H430", listOf("KJV"))

        val first = host.buildSearchRequest("KJV", "strong:H430")
        val second = host.buildSearchRequest("KJV", "strong:H430")

        assertTrue(first.isStrongsSearch)
        assertTrue(second.isStrongsSearch, "same query text -> still a Strong's search")
    }

    /**
     * Review item 5: the one-shot overrides are keyed to the query TEXT, and a SEEDLESS open does not
     * change the text — so it must not drop them. The reported sequence: Strong's find-all -> results
     * -> a seedless re-entry (Ctrl+F, the device SEARCH key, the drawer row) -> closing the settings
     * sheet re-ran the very same `strong:H430` string as an ordinary word-mode search
     * (`ReadingSearchController.settingsClosed` re-runs whatever query is in flight).
     */
    @Test fun aSeedlessReEntryKeepsTheStrongsOverrideForTheSameQuery() {
        val host = host()
        host.openSearchStrongs("H430", listOf("KJV"))
        assertTrue(host.buildSearchRequest("KJV", "strong:H430").isStrongsSearch, "sanity")

        host.openSearch() // no seed: the query text stays `strong:H430`

        val request = host.buildSearchRequest("KJV", "strong:H430")
        assertTrue(request.isStrongsSearch, "a seedless re-entry must keep the flag for the same query")
        assertEquals(SearchType.ANY_WORDS, request.searchType)
        assertEquals(SearchBibleSection.ALL, request.bibleSection)
    }

    /** The other side of item 5's narrowing: a NEW seed still replaces the previous run's overrides —
     *  the reset was narrowed, not removed. */
    @Test fun aNewSeedStillReplacesTheOneShotOverrides() {
        val host = host()
        host.openSearchStrongs("H430", listOf("KJV"))

        host.openSearch("grace") // a fresh, raw seed

        assertFalse(
            host.buildSearchRequest("KJV", "strong:H430").isStrongsSearch,
            "a new seed must clear the previous run's Strong's flag",
        )
    }

    // ---- Entry point 2: the Compose toolbar's search button (Task 8a; asserted here per the brief) ----

    @Test fun composeSearchOpensHostSearchWhenHosted() {
        activity.composeReadingViewHost = host()

        activity.readingCommands.composeSearch()

        assertTrue(activity.composeReadingViewHost!!.searchController.searchModeActive.value)
        assertNull(shadowOf(activity).nextStartedActivityForResult, "must not ALSO start the classic intent")
    }

    @Test fun composeSearchFallsBackToClassicIntentWhenNotHosted() {
        assertNull(activity.composeReadingViewHost, "sanity: fallback path")

        activity.readingCommands.composeSearch()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(ActivityBase.STD_REQUEST_CODE, started?.requestCode)
    }

    // ---- Entry point 4: MenuCommandHandler's drawer/menu search row ----------------------------

    @Test fun menuSearchButtonOpensHostSearchWhenHosted() {
        activity.composeReadingViewHost = host()

        val handled = menuCommandHandler().handleMenuRequest(R.id.searchButton)

        assertTrue(handled)
        assertTrue(activity.composeReadingViewHost!!.searchController.searchModeActive.value)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test fun menuSearchButtonFallsBackToClassicIntentWhenNotHosted() {
        assertNull(activity.composeReadingViewHost, "sanity: fallback path")

        val handled = menuCommandHandler().handleMenuRequest(R.id.searchButton)

        assertTrue(handled, "a built classic intent still reports handled")
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(ActivityBase.STD_REQUEST_CODE, started?.requestCode)
    }

    /**
     * An OFF-reading-route nav host with a directly installed [ComposeReadingViewHost]: the
     * `ReadingHostChromeTest.restorePaneFocusReachesThisHostsOwnRepositoryAndIsNotSwallowedByItsGate`
     * trick. Off the reading route the host bootstrapped no repository, so
     * [NavHostComposeActivity.restorePaneFocus] THROWS exactly when it gets past its gate and reaches
     * `hostWindowRepository` -- which makes the gate's decision observable at the real call site
     * (`ComposeReadingViewHost`'s `onDrawerClosed` calls `activity.restorePaneFocus()`), where a
     * Robolectric `BibleView.requestFocus()` would have no observable effect.
     */
    private fun onAnOffRouteHostWithAReadingView(block: (NavHostComposeActivity) -> Unit) {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
        ).create()
        try {
            val offRoute = controller.get()
            offRoute.composeReadingViewHost = ComposeReadingViewHost(offRoute)
            block(offRoute)
        } finally {
            controller.close()
        }
    }

    /**
     * Round 12b §1: the drawer's Search row closes the drawer and opens search in one click, so the
     * drawer-close focus restore lands AFTER the search field has taken focus. Restoring pane focus
     * then dismisses the keyboard — the "flashes and vanishes" report. Guarded here rather than
     * only in `DrawerPaneFocusTest` because the defect was the unconditional CALL SITE, not the rule:
     * so this drives the nav host's own call site, [NavHostComposeActivity.restorePaneFocus], and
     * asserts it returns at the gate (it would otherwise reach the off-route host's absent
     * repository and throw -- see [onAnOffRouteHostWithAReadingView]). Slice 8 F2 fix round 1: it
     * had been reduced to calling the shared predicate directly, which pinned nothing about the call
     * site.
     */
    @Test fun drawerCloseDoesNotStealFocusFromAnOpenSearchBar() = onAnOffRouteHostWithAReadingView { offRoute ->
        offRoute.composeReadingViewHost!!.openSearch("light")
        assertTrue(offRoute.readingCommands.composeSearchModeActive, "sanity: search is open")

        // Returns at the gate: no repository read, so no UninitializedPropertyAccessException.
        offRoute.restorePaneFocus()
    }

    /** The other branch at the same call site: with no search open the restore must get PAST the gate. */
    @Test fun drawerCloseStillRestoresPaneFocusWithNoSearchOpen() = onAnOffRouteHostWithAReadingView { offRoute ->
        assertFalse(offRoute.readingCommands.composeSearchModeActive, "sanity: search is closed")

        assertFailsWith<UninitializedPropertyAccessException>(
            "the classic onDrawerClosed parity must survive for every non-search row: the restore " +
                "reaches this host's repository (absent off the reading route, hence the throw)",
        ) { offRoute.restorePaneFocus() }
    }

    /**
     * Review Important 2: classic gated the WHOLE action on `isSearchable` — false for My Notes,
     * dictionary, map and non-EPUB general-book pages — but the retarget originally called
     * `composeSearchIfHosted()` BEFORE that check, and (pre-F43-Task-6) `searchOpensInReadingView`
     * only excluded EPUB. My Notes' `isSearchable` is a hardcoded `false` (`CurrentMyNotePage.kt:54`),
     * so it needs no document setup to prove the fix: switching the active window to it must leave
     * the row fully inert — no host retarget AND no classic intent — even with a host mounted.
     */
    @Test fun menuSearchButtonDoesNothingOnANonSearchablePageEvenWhenHosted() {
        activity.composeReadingViewHost = host()
        val pageManager = windowRepository.activeWindow.pageManager
        // `currentPage`'s setter is private; the documented way to switch pages is
        // `setCurrentDocumentAndKey` with a book `getBookPage` maps to the target page —
        // `FakeBookFactory.myNotesDocument`'s osisID ("Commentaries.MyNote") maps to
        // `currentMyNotePage` (`CurrentPageManager.kt:244-245`).
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        pageManager.setCurrentDocumentAndKey(FakeBookFactory.myNotesDocument, verse)
        assertFalse(pageManager.currentPage.isSearchable, "sanity")

        val handled = menuCommandHandler().handleMenuRequest(R.id.searchButton)

        assertFalse(handled, "a non-searchable page must not be handled even when hosted")
        assertFalse(
            activity.composeReadingViewHost!!.searchController.searchModeActive.value,
            "must not retarget into the host's search either",
        )
        assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    // ---- Entry point 6: the device SEARCH key ---------------------------------------------------

    private fun searchKeyEvent() = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SEARCH)

    /**
     * Slice 8 F2: the SEARCH key is the one entry point here that needs a SET-UP host. The nav host
     * answers it only while the graph shows the reading destination (classic `MainBibleActivity`
     * answered it by being the reading Activity), and that check reads the graph's `navController`,
     * which a never-`.create()`d fixture does not have. `firstTime` and [resetComposeUiDispatcher] for
     * the reasons `ReadingHostBackChainTest.host()` and slice 8 D1 give. The host's own repository
     * (the bootstrap's) is the one the key reads, so KJV is put on it.
     */
    private fun onASetUpReadingHost(block: (NavHostComposeActivity) -> Unit) {
        firstTime = false
        resetComposeUiDispatcher()
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).setup()
        try {
            val readingHost = controller.get()
            val kjv = Books.installed().getBook("KJV") as SwordBook
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            readingHost.hostWindowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
            block(readingHost)
        } finally {
            controller.close()
            ReadingViewVisibility.setVisible(false)
            ReadingHostPresence.setForeground(null)
        }
    }

    @Test fun searchKeyOpensHostSearchWhenHosted() = onASetUpReadingHost { readingHost ->
        if (readingHost.composeReadingViewHost == null) readingHost.composeReadingViewHost = ComposeReadingViewHost(readingHost)

        val handled = readingHost.onKeyUp(KeyEvent.KEYCODE_SEARCH, searchKeyEvent())

        assertTrue(handled)
        assertTrue(readingHost.composeReadingViewHost!!.searchController.searchModeActive.value)
        assertNull(shadowOf(readingHost).nextStartedActivityForResult)
    }

    @Test fun searchKeyNavigatesTheGraphToSearchWhenNotHosted() = onASetUpReadingHost { readingHost ->
        readingHost.composeReadingViewHost = null

        val handled = readingHost.onKeyUp(KeyEvent.KEYCODE_SEARCH, searchKeyEvent())

        assertTrue(handled)
        // F118 (fix batch 6, plan C5): the STD self-launch is handled in the live graph, never by the platform
        // (no second host instance below API 31). KJV has no index here, so the destination is the index prompt.
        assertNull(shadowOf(readingHost).nextStartedActivityForResult)
        assertEquals("search/index", readingHost.currentRouteForTest()?.substringBefore('?'))
    }

    // ---- Entry point 8: LinkControl.showAllOccurrences (Strong's find-all) ----------------------

    /**
     * The not-indexed branch must stay classic REGARDLESS of whether a Compose host is mounted —
     * task resolution 3: prompting to index a document other than the active window's is Task 11's
     * machinery, which does not exist yet. KJV's `Feature=StrongsNumbers` without a real Lucene
     * index (see the class kdoc) makes this branch the only one reachable through the real
     * `LinkControl` call site in this test environment, which is itself the guarantee under test.
     */
    @Test fun showAllOccurrencesKeepsTheClassicIndexRouteEvenWhenHosted() {
        activity.composeReadingViewHost = host()

        GlobalContext.get().get<LinkControl>().showAllOccurrences("H430", SearchControl.SearchBibleSection.ALL)

        assertNotNull(shadowOf(activity).nextStartedActivity, "classic SearchIndex intent must still launch")
        assertFalse(
            activity.composeReadingViewHost!!.searchController.searchModeActive.value,
            "must NOT retarget while the search document is not indexed",
        )
    }

    @Test fun showAllOccurrencesFallsBackToClassicIndexRouteWhenNotHosted() {
        assertNull(activity.composeReadingViewHost, "sanity: fallback path")

        GlobalContext.get().get<LinkControl>().showAllOccurrences("H430", SearchControl.SearchBibleSection.ALL)

        assertNotNull(shadowOf(activity).nextStartedActivity)
    }

    // ---- Step 2: a result tap navigates without an Activity round trip --------------------------

    /**
     * `onSearchResultSelected` resolves the SWORD key pair and navigates the active window directly,
     * then closes only the sheet (not the whole search session) — see its kdoc for why `closeSheet()`
     * and not `partialExpand()`. Driven into `NeedsIndex` (not `Results`) to reach `sheetVisible =
     * true`, which needs no real index — see the class kdoc for why `Results` is out of reach here.
     */
    @Test fun onSearchResultSelectedNavigatesAndClosesOnlyTheSheet() {
        val host = host()
        host.openSearch("grace")
        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), host.searchController.phase.value, "sanity")
        assertTrue(host.searchController.sheetVisible.value, "sanity: NeedsIndex opens the sheet too")

        host.onSearchResultSelected("Gen.1.5", "KJV")

        val kjv = Books.installed().getBook("KJV") as SwordBook
        assertEquals(
            kjv.getKey("Gen.1.5").osisRef,
            // `currentBible.key` (non-single) reports the whole DISPLAYED unit (e.g. a whole
            // chapter) rather than the exact verse last navigated to; `singleKey` is the precise
            // verse `setCurrentDocumentAndKey` was actually called with.
            windowRepository.activeWindow.pageManager.currentBible.singleKey.osisRef,
        )
        assertFalse(host.searchController.sheetVisible.value, "the sheet closes")
        assertTrue(host.searchController.searchModeActive.value, "search mode itself stays open")
    }

    /**
     * F83 (fix batch 3 §2.1.1): classic's results screen was a history item, so BACK from a verse
     * opened from it returned to the list. The first back after a result tap must bring the list
     * back, not leave search mode.
     */
    @Test fun backAfterAResultTapReopensTheResultsListInsteadOfLeavingSearch() {
        val fakeBook = indexedFakeBible("ResultDoc")
        Books.installed().addBook(fakeBook)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(fakeBook, verse)
            val host = host()
            GlobalContext.get().get<SearchResultsCache>().put(
                SearchRequest(
                    query = "light",
                    searchType = SearchType.ANY_WORDS,
                    bibleSection = SearchBibleSection.ALL,
                    translationIds = listOf("ResultDoc"),
                    currentBookName = GlobalContext.get().get<SearchControl>().currentBookName,
                    isStrongsSearch = false,
                ),
                MultiSearchResults(main = emptyList(), other = emptyList(), total = 3),
            )
            host.openSearch("light", preDecorated = true)
            assertEquals(ReadingSearchPhase.Results("ResultDoc", forEpub = false), host.searchController.phase.value, "sanity")

            host.onSearchResultSelected("Gen.1.5", "ResultDoc")
            assertFalse(host.searchController.sheetVisible.value, "sanity: the tap hides the sheet")

            assertTrue(host.closeSearchIfOpen(), "the back press is consumed")
            assertTrue(host.searchController.searchModeActive.value, "F83: the first back must not leave search mode")
            assertTrue(host.searchController.sheetVisible.value, "F83: the first back brings the results list back")
        } finally {
            GlobalContext.get().get<SearchResultsCache>().clear()
            Books.installed().removeBook(fakeBook)
        }
    }

    /**
     * F100 (fix batch 3 §2.1.3): the persisted selection is searched too, and JSword silently skips
     * a translation without an index. Classic validated every selected translation
     * (`BibleSearchServiceImpl.validateIndex`); a submit must prompt for the first unindexed one.
     */
    @Test fun submittingWithAnUnindexedTranslationInTheSelectionPromptsForItsIndex() {
        val fakeBook = indexedFakeBible("FAKE")
        Books.installed().addBook(fakeBook)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(fakeBook, verse)
            // KJV is installed in this environment but has no Lucene index (see setKjvAsCurrentDocument).
            CommonUtils.settings.setString("search_selected_translations", "KJV")
            val host = host()
            host.openSearch()
            assertEquals(ReadingSearchPhase.Form("FAKE", forEpub = false), host.searchController.phase.value, "sanity")

            host.searchController.queries.setQuery("light")
            host.searchController.submit()

            assertEquals(
                ReadingSearchPhase.NeedsIndex("KJV", forEpub = false),
                host.searchController.phase.value,
                "F100: KJV is selected and has no index, so the submit must ask for one",
            )
            assertEquals(listOf("KJV"), host.searchSelectorPendingIdsForTest, "the Task 11 chain is armed for the selection")
        } finally {
            CommonUtils.settings.removeString("search_selected_translations")
            Books.installed().removeBook(fakeBook)
        }
    }

    /** Runs [block] with a [SearchIndexService] that records `createIndex` instead of building a real index. */
    private fun withRecordingIndexService(block: () -> Unit) {
        val fake = object : SearchIndexService {
            override fun hasIndex(docId: String) = false
            override fun createIndex(docId: String) {}
        }
        val overrideModule = module { single<SearchIndexService> { fake } }
        loadKoinModules(overrideModule)
        try {
            block()
        } finally {
            // Same hand re-install as `ReadingSearchHostTest`'s EpubSearchService override.
            unloadKoinModules(overrideModule)
            loadKoinModules(module { singleOf(::SearchIndexServiceImpl) { bind<SearchIndexService>() } })
        }
    }

    /**
     * Review I1: after the F100 index prompt the automatic search must cover exactly what the submit it
     * replaced would have — the selection PLUS the active (indexed) document (F44/B4) — not just the
     * translation the prompt built. Selection [KJV] (unindexed), active FAKE (indexed).
     */
    @Test fun theAutoRunAfterTheSelectionIndexPromptStillSearchesTheActiveDocument() {
        val fakeBook = indexedFakeBible("FAKE")
        Books.installed().addBook(fakeBook)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(fakeBook, verse)
            CommonUtils.settings.setString("search_selected_translations", "KJV")
            withRecordingIndexService {
                val host = host()
                host.openSearch()
                host.searchController.queries.setQuery("lord")
                host.searchController.submit()
                assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), host.searchController.phase.value, "sanity")

                host.searchController.acceptIndexing()
                host.searchController.onIndexingFinished(true)

                val ids = assertNotNull(host.lastSearchRequestForTest, "the build finishing must run the waiting search").translationIds
                assertEquals(listOf("KJV", "FAKE"), ids, "I1: the active document is searched too, as the submit would have")
            }
        } finally {
            CommonUtils.settings.removeString("search_selected_translations")
            Books.installed().removeBook(fakeBook)
        }
    }

    /**
     * Review M1: a prompt abandoned (BACK, then a pane switch) must not leave its chain armed. The
     * next submit raises an ordinary prompt for the new pane's own document, and the build that follows
     * must not be hijacked into prompting for KJV again.
     */
    @Test fun anAbandonedSelectionPromptDoesNotLeaveAStalePendingChain() {
        val fakeBook = indexedFakeBible("FAKE")
        Books.installed().addBook(fakeBook)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(fakeBook, verse)
            CommonUtils.settings.setString("search_selected_translations", "KJV")
            val host = host()
            host.openSearch()
            host.searchController.queries.setQuery("lord")
            host.searchController.submit()
            assertEquals(listOf("KJV"), host.searchSelectorPendingIdsForTest, "sanity: the prompt armed the chain")
            host.searchController.closeSheet()

            // The pane switch: the active document is now KJV, which has no index.
            setKjvAsCurrentDocument()
            host.searchController.submit()

            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), host.searchController.phase.value, "sanity")
            assertNull(host.searchSelectorPendingIdsForTest, "M1: the abandoned prompt's chain must be gone")
        } finally {
            CommonUtils.settings.removeString("search_selected_translations")
            Books.installed().removeBook(fakeBook)
        }
    }

    /** An empty result set (nothing to add to the multi-document link) must not throw, and still
     *  closes the sheet exactly like a populated one. */
    @Test fun openSearchResultsInWindowClosesTheSheetEvenWithNoRows() {
        val host = host()
        host.openSearch("grace")
        assertTrue(host.searchController.sheetVisible.value, "sanity")

        host.openSearchResultsInWindow()

        assertFalse(host.searchController.sheetVisible.value)
    }

    // ---- F44 fix round M2: the settings sheet renders from the LIVE target ----------------------

    /**
     * Spec §5: the settings sheet's mode row renders from the live target, not from the phase. In a
     * `Results` phase the phase is the RECORDED document, so reading it gave the user the wrong form
     * whenever the two disagreed — EPUB results → tap a Bible pane → open settings → the EPUB
     * word-mode row appeared, and closing it ran a BIBLE search whose word mode, section and
     * translation selection the user had never been shown (`settingsClosed()` re-resolves the target
     * itself). Whatever the sheet offers must be what closing it will apply.
     *
     * The phase here is pinned with `promptIndexFor` purely to make the test DETERMINISTIC: it marks
     * the prompt "explicit", which is the one state `activeDocumentChanged()` refuses to refresh, so
     * the phase provably still records the Bible after the pane switch no matter whether the host's
     * `onMain` event subscription has been drained. Any `Results`/`Indexing` phase reaches the same
     * disagreement by the ordinary route; neither can be produced here without a real Lucene index or
     * a real JSword index build.
     */
    @Test fun theSearchSettingsSheetFollowsTheActiveDocumentNotThePhase() {
        val h = host()
        activity.composeReadingViewHost = h
        // setUp leaves KJV (a Bible) current.
        assertFalse(h.searchSettingsForEpub(), "sanity: a Bible pane must offer the Bible form")
        h.openSearch()
        assertTrue(h.searchController.promptIndexFor("KJV"), "sanity: a session exists to pin")

        val epub = givenCurrentDocumentIsAnEpub()
        try {
            val phase = h.searchController.phase.value
            assertTrue(
                phase is ReadingSearchPhase.NeedsIndex && !phase.forEpub,
                "sanity: the pinned phase still records the BIBLE, so phase and live target disagree",
            )

            assertTrue(
                h.searchSettingsForEpub(),
                "the settings sheet must offer the EPUB word-mode row the active pane's document needs",
            )
        } finally {
            Books.installed().removeBook(epub)
        }
    }

    // ---- Step 3: the search sheet's WebView bottom-offset term ----------------------------------

    @Test fun bottomOffsetForWebViewIncludesTheSearchSheetHeightOnlyWhileVisible() {
        val before = activity.readingInsets.bottomOffsetForWebView

        activity.readingInsets.updateSearchSheetOffsets(visible = true, heightPx = 250)
        assertEquals(before + 250, activity.readingInsets.bottomOffsetForWebView)

        activity.readingInsets.updateSearchSheetOffsets(visible = false, heightPx = 250)
        assertEquals(before, activity.readingInsets.bottomOffsetForWebView, "hidden: the height must not be reserved")
    }

    /**
     * Review item B: only the arithmetic was covered above — nothing asserted that
     * [ReadingInsets.updateSearchSheetOffsets] actually posts
     * [SearchSheetOffsetsUpdated], which is the ONLY thing that makes
     * [BibleView.updateOffsets] re-read [ReadingInsets.bottomOffsetForWebView] and push it to
     * the Vue side at runtime (see that event's kdoc). `on<T>`, not `onMain<T>`, dispatches
     * synchronously (`ABEventBus.post`) — no coroutine/dispatcher wait needed.
     */
    @Test fun updateSearchSheetOffsetsPostsTheEventOnlyWhenSomethingActuallyChanged() {
        var updates = 0
        ABEventBus.register(this) { on<SearchSheetOffsetsUpdated> { updates++ } }
        try {
            activity.readingInsets.updateSearchSheetOffsets(visible = true, heightPx = 100)
            assertEquals(1, updates, "a real change must post")

            activity.readingInsets.updateSearchSheetOffsets(visible = true, heightPx = 100)
            assertEquals(1, updates, "an unchanged (visible, height) pair must not repost")

            activity.readingInsets.updateSearchSheetOffsets(visible = true, heightPx = 150)
            assertEquals(2, updates, "a height-only change must still post")

            activity.readingInsets.updateSearchSheetOffsets(visible = false, heightPx = 150)
            assertEquals(3, updates, "a visibility-only change must still post")
        } finally {
            ABEventBus.unregister(this)
        }
    }
}
