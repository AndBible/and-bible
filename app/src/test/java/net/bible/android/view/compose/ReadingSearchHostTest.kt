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

import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewGeneration
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.search.AndroidEpubSearchService
import net.bible.android.view.activity.search.epubKeyFor
import net.bible.android.view.activity.search.epubSearchRunFor
import net.bible.android.view.activity.search.stripSearchDecoration
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedcore.search.SearchKind
import net.bible.sharedcore.search.searchKindFor
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.strings.AndroidStrings
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** No-op fake — mirrors `AgentLogHostTest`/`ReadingLlmHostTest`'s own private copies (this repo has
 *  no mocking framework, and each of those is file-private). */
private val noopCommands = object : WindowCommands {
    override fun setActive(windowId: String) {}
    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {}
    override fun addNewWindow(fromWindowId: String) {}
    override fun minimise(windowId: String) {}
    override fun close(windowId: String) {}
    override fun restore(windowId: String) {}
    override fun maximise(windowId: String) {}
    override fun unMaximise() {}
    override fun setPin(windowId: String, value: Boolean) {}
    override fun move(windowId: String, position: Int) {}
    override fun setSynchronised(windowId: String, value: Boolean) {}
    override fun changeSyncGroup(windowId: String, group: Int) {}
    override fun focusNext() {}
    override fun focusPrevious() {}
    override fun setRestoreButtonsVisible(value: Boolean) {}
}

/**
 * F6 Task 8a — the `:app` host wiring for reading-view search.
 *
 * Three things are under test here, and they are the three that a device A/B could only tell you
 * about after the fact:
 *
 * 1. **`searchDocumentInfo`** — the JSword `Book` → portable [SearchDocumentInfo] mapping. This is
 *    where the spec's two defects are actually fixed, so both are asserted through the real
 *    `searchKindFor` decision rather than on the intermediate data.
 * 2. **`awaitIndexDone`** — the post-indexing wait. An index build that reports finished but whose
 *    `indexStatus` never reaches `DONE` must land back in `NeedsIndex`, not in empty `Results`.
 * 3. **The generation must not bump** when search opens, parks and closes — a bump re-runs every
 *    pane's `AndroidView` factory and destroys every `BibleView` WebView.
 *
 * What is deliberately NOT here: anything rendered. `:app` has no `ComposeTestRule` and no golden
 * covers `ComposeReadingViewHost`, so the mount test below can only prove the new parameters exist
 * and reach `mountComposeView` (composition never runs for a container that is never attached to a
 * window) — the same limit `AgentLogHostTest` documents.
 *
 * 4. **F6 Task 11's index prompt for a translation other than the one being read** — a real
 *    [ComposeReadingViewHost] against a real (never `.install()`ed) [MainBibleActivity], same
 *    precedent as `ReadingSearchEntryPointsTest.host()`, plus real unindexed fake Bibles added to
 *    [Books.installed] (no fake `BibleSearchService`, no Koin swap — `unindexedAmong` reads real
 *    `indexStatus` through `SwordDocumentFacade`). The chaining DECISION itself
 *    ([ComposeReadingViewHost.nextSelectorIndexPrompt]) is tested directly, the same way
 *    [awaitIndexDone] above is: driving the real thing would need a genuine JSword
 *    `Progress`/`WorkEvent`/`JobManager` round trip, which no test in this repo attempts.
 */
@RunWith(RobolectricTestRunner::class)
// TestBibleApplication, not the bare Application the other `*HostTest`s use: `Book.isEpub` lives in
// `EpubBookKt`, whose static initializer reads `BibleApplication.application` — a bare Application
// leaves that lateinit unset and every mapping test dies in `<clinit>`.
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingSearchHostTest {

    // ---- F6 Task 11's real-host fixture (only the tests below this need it) --------------------

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: MainBibleActivity

    /** Mirrors `ReadingSearchEntryPointsTest.setUp`/`tearDown` — a real [MainBibleActivity]/
     *  [WindowControl]/[WindowRepository] graph, activity built WITHOUT `.create()`, host
     *  constructed directly and never `.install()`ed. */
    @Before
    fun setUpRealHost() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)

        // KJV ships with no real Lucene index in this test environment (same fixture
        // `ReadingSearchEntryPointsTest` documents), so setting it active is enough to get a
        // search session OPEN (not `Closed`) via `openSearch()` without needing an indexed document
        // — the tests below only care that a session exists to redirect, not what it starts on.
        val kjv = Books.installed().getBook("KJV") as SwordBook
        val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, verse)
    }

    @After
    fun tearDownRealHost() {
        CurrentActivityHolder.deactivate(activity)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun host() = ComposeReadingViewHost(activity)

    /**
     * A fake Bible left UNINDEXED (no `indexStatus` override) — the twin of
     * `ReadingSearchEntryPointsTest.indexedFakeBible`, same `NullBackend` construction, same
     * `Biblical Texts`/`RawText` conf so it is a valid Bible candidate.
     */
    private fun unindexedFakeBible(initials: String): SwordBook {
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
        return SwordBook(SwordBookMetaData(conf.toByteArray(), initials), NullBackend())
    }

    // ---- Step 1: Book -> SearchDocumentInfo ----------------------------------------------------

    /**
     * Books built from byte-array metadata: no on-disk conf, no backend I/O, so neither a real
     * module nor SQLite is needed (the same construction `ModuleBackupPackagingTest.fakeBook` and
     * `SyncableDocumentTest.book` use).
     */
    private fun book(
        initials: String,
        category: String,
        modDrv: String = "RawText",
        extraConf: String = "",
        indexed: Boolean = false,
    ): Book {
        val conf = """
            [$initials]
            Description=$initials
            Abbreviation=$initials
            Category=$category
            ModDrv=$modDrv
            DataPath=./modules/texts/ztext/$initials/
            Encoding=UTF-8
            Versification=KJV
            $extraConf
        """.trimIndent()
        val b = SwordBook(SwordBookMetaData(conf.toByteArray(), initials), NullBackend())
        if (indexed) b.indexStatus = IndexStatus.DONE
        return b
    }

    /**
     * Defect 2: a dictionary was admitted to the Lucene path, where `candidateBibles()` offers only
     * Bibles and only `Verse` keys survive — zero results, no explanation.
     */
    @Test
    fun aDictionaryBecomesAnUnavailableSearchKind() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestDict", category = "Lexicons / Dictionaries", modDrv = "RawLD", indexed = true)
        )
        assertEquals(SearchKind.Unavailable, searchKindFor(doc))
    }

    /** Defect 2's other half: a SWORD (non-EPUB) general book is equally unsearchable. */
    @Test
    fun aPlainGeneralBookBecomesAnUnavailableSearchKind() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestGen", category = "Generic Books", modDrv = "RawGenBook", indexed = true)
        )
        assertEquals(SearchKind.Unavailable, searchKindFor(doc))
    }

    /**
     * Defect 1: an EPUB is a `GENERAL_BOOK` to JSword, so an unindexed one fell into the
     * general-book branch and search silently did nothing — making issue #3093's index prompt
     * unreachable. `isEpub` has to be carried separately for `searchKindFor` to test it first.
     */
    @Test
    fun anUnindexedEpubBecomesNeedsIndexForEpub() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestEpub", category = "Generic Books", modDrv = "RawGenBook", extraConf = "AndBibleEpubModule=1")
        )
        assertTrue(doc!!.isEpub, "an AndBible EPUB module must be reported as an EPUB")
        assertEquals(SearchKind.NeedsIndex("TestEpub", forEpub = true), searchKindFor(doc))
    }

    /**
     * `indexStatus` is not the truth for an EPUB: `EpubBook.addEpubBook` re-derives it from the
     * LUCENE index manager, which is never true for an EPUB, so a manually-installed EPUB with a
     * working FTS5 index reports UNDONE — and, symmetrically, nothing may conclude "indexed" from a
     * DONE it did not earn. The Compose path therefore reads the EPUB's own backend, so this
     * document is NOT treated as searchable even though its indexStatus says DONE.
     */
    @Test
    fun anEpubIsNotTreatedAsIndexedJustBecauseIndexStatusSaysSo() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestEpub", category = "Generic Books", modDrv = "RawGenBook",
                 extraConf = "AndBibleEpubModule=1", indexed = true)
        )
        assertTrue(doc!!.isEpub, "sanity: the fixture is an EPUB")
        assertFalse(doc.indexDone, "indexDone must come from the EPUB backend, not indexStatus")
        assertEquals(SearchKind.NeedsIndex("TestEpub", forEpub = true), searchKindFor(doc))
    }

    /** The non-EPUB half is unchanged: `indexStatus` remains the source of truth there. */
    @Test
    fun aNonEpubStillDerivesIndexDoneFromIndexStatus() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestBible", category = "Biblical Texts", indexed = true)
        )
        assertTrue(doc!!.indexDone)
    }

    /**
     * The two-argument [ComposeReadingViewHost.documentIndexDone] overload is the seam that lets
     * a unit test drive both sides of the EPUB branch — no unit test can construct a real
     * `EpubBackend`. This is the intermediate TRUE that is the whole point of the task: even
     * though `indexStatus` says UNDONE, an EPUB whose backend resolver reports indexed must count
     * as indexed.
     */
    @Test
    fun anEpubWithAnIndexedBackendIsIndexedEvenWhenIndexStatusSaysUndone() {
        val epub = book(
            "TestEpub", category = "Generic Books", modDrv = "RawGenBook",
            extraConf = "AndBibleEpubModule=1", indexed = false,
        )
        assertTrue(ComposeReadingViewHost.documentIndexDone(epub) { true })
    }

    /** The mirror: a DONE `indexStatus` must not override a backend resolver reporting not-indexed. */
    @Test
    fun anEpubWithAnUnindexedBackendIsNotIndexedEvenWhenIndexStatusSaysDone() {
        val epub = book(
            "TestEpub", category = "Generic Books", modDrv = "RawGenBook",
            extraConf = "AndBibleEpubModule=1", indexed = true,
        )
        assertFalse(ComposeReadingViewHost.documentIndexDone(epub) { false })
    }

    /**
     * A non-EPUB must ignore the resolver entirely — both in its return value (still driven by
     * `indexStatus`) and by never invoking it. Without the "never called" half, a future
     * "simplification" that calls the resolver for every document type would still pass the
     * value-only assertion (the resolver here always answers `true`, matching `indexStatus`).
     */
    @Test
    fun aNonEpubIgnoresTheBackendResolverEntirely() {
        val bible = book("TestBible", category = "Biblical Texts", indexed = true)
        var resolverCalled = false
        val result = ComposeReadingViewHost.documentIndexDone(bible) { resolverCalled = true; true }
        assertTrue(result, "non-EPUB indexDone must still come from indexStatus")
        assertFalse(resolverCalled, "the backend resolver must never be invoked for a non-EPUB")
    }

    @Test
    fun anIndexedBibleBecomesABibleSearchKind() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestBible", category = "Biblical Texts", indexed = true)
        )
        assertEquals(SearchKind.Bible("TestBible"), searchKindFor(doc))
    }

    @Test
    fun anUnindexedCommentaryBecomesNeedsIndexForSword() {
        val doc = ComposeReadingViewHost.searchDocumentInfo(
            book("TestComm", category = "Commentaries", modDrv = "RawCom")
        )
        assertEquals(SearchKind.NeedsIndex("TestComm", forEpub = false), searchKindFor(doc))
    }

    /** No document in the active window at all — the reading view can be showing an error page. */
    @Test
    fun noDocumentIsUnavailable() {
        assertEquals(null, ComposeReadingViewHost.searchDocumentInfo(null))
        assertEquals(SearchKind.Unavailable, searchKindFor(ComposeReadingViewHost.searchDocumentInfo(null)))
    }

    // ---- Step 5: the indexing wait -------------------------------------------------------------

    private class Session(doc: SearchDocumentInfo?) {
        val runs = mutableListOf<String>()
        var indexed: String? = null
        var leftFullScreen = 0
        var unavailable = 0
        val controller = ReadingSearchController(
            resolveDoc = { doc },
            onUnavailable = { unavailable++ },
            onLeaveFullScreen = { leftFullScreen++ },
            onStartIndexing = { docId -> indexed = docId },
            onRunSearch = { _, query, _ -> runs += query },
        )
    }

    private val unindexedBible =
        SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, isEpub = false, indexDone = false)
    private val indexedBible =
        SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, isEpub = false, indexDone = true)

    /**
     * The case the ~12s cap exists for: JSword declares the job finished, the document's
     * `indexStatus` never reaches `DONE`. The session must go back to the prompt — offering results
     * for an index that does not exist would show an empty list as if nothing matched.
     */
    @Test
    fun anIndexThatNeverReachesDoneGivesUpAndLandsBackInNeedsIndex() = runTest {
        val session = Session(unindexedBible)
        session.controller.queries.setQuery("light")
        session.controller.open()
        session.controller.acceptIndexing()
        assertTrue(session.controller.phase.value is ReadingSearchPhase.Indexing)

        var polls = 0
        val done = ComposeReadingViewHost.awaitIndexDone(
            poll = IndexPollDecision(),
            indexDone = { polls++; false },
            pause = { },
        )

        assertFalse(done, "an index that never reaches DONE must not be reported as done")
        assertEquals(6, polls, "the cap is six status reads (~12s at the production 2s interval)")
        session.controller.onIndexingFinished(done)
        assertEquals(ReadingSearchPhase.NeedsIndex("KJV", forEpub = false), session.controller.phase.value)
        assertTrue(session.runs.isEmpty(), "no search may run against a missing index")
    }

    /** The normal case: the status flips a poll or two late, and the waiting query then runs itself. */
    @Test
    fun anIndexThatReachesDoneRunsTheWaitingQuery() = runTest {
        val session = Session(unindexedBible)
        session.controller.queries.setQuery("light")
        session.controller.open()
        session.controller.acceptIndexing()
        assertEquals("KJV", session.indexed)

        var reads = 0
        val done = ComposeReadingViewHost.awaitIndexDone(
            poll = IndexPollDecision(),
            indexDone = { ++reads >= 3 },
            pause = { },
        )

        assertTrue(done)
        session.controller.onIndexingFinished(done)
        assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), session.controller.phase.value)
        assertEquals(listOf("light"), session.runs)
    }

    // ---- Step 6: the pane subtree must survive a search --------------------------------------

    /**
     * The meaningful version of the check dropped from Task 0. Nothing on the search path may reach
     * [ComposeReadingViewGeneration.rebuild] — a bump re-runs every pane's `AndroidView` factory,
     * which destroys and recreates every `BibleView` WebView (losing the loaded document, the scroll
     * position and every bit of JS state). The scaffold's shape is guarded separately, at the source
     * level, by `SearchSheetStructureGuardTest`.
     *
     * Review I4: this used to drive a *detached* [ReadingSearchController] and then assert over a
     * LOCAL [ComposeReadingViewGeneration] that no production code could reach — so it passed no
     * matter what the search path did. It now drives the REAL host through the real entry point and
     * reads **the host's own** counter ([ComposeReadingViewHost.generationForTest]), which is the only
     * arrangement in which a `rebuild()` added anywhere on this path can turn it red. (Verified by
     * temporarily calling `generation.rebuild()` from `openSearch`: this test failed, `expected 0,
     * actual 1`.)
     */
    @Test
    fun openingAndClosingSearchMustNotRebuildThePaneSubtree() {
        val host = host()
        assertEquals(0, host.generationForTest.state.value, "sanity: nothing has rebuilt yet")

        host.openSearch()
        assertTrue(host.searchController.searchModeActive.value, "sanity: a session is open")
        assertTrue(host.searchController.sheetVisible.value, "sanity: the sheet is up (KJV has no index)")
        assertTrue(host.closeSearchIfOpen(), "the first back closes the sheet")
        assertTrue(host.closeSearchIfOpen(), "the second leaves search mode")

        assertEquals(
            0, host.generationForTest.state.value,
            "no step of opening, parking or closing search may bump the generation — a bump remounts " +
                "every pane's BibleView WebView",
        )
    }

    // ---- Review I1: a FOREIGN finished JSword job must not resolve the index build ---------------

    /**
     * The regression this pins (a port defect, not classic's): `JobManager`'s work listener is global,
     * so a module download or install finishing mid-build used to tear the index feed down, poll,
     * possibly report failure and drop the session back to `NeedsIndex` — whose Create then runs
     * `deleteDocumentIndex` under the build that was about to succeed. See
     * [ComposeReadingViewHost.shouldResolveIndexBuild]'s kdoc for why the gate is "no job is still
     * running" rather than the build's own `Progress` identity.
     */
    @Test
    fun aForeignFinishedJobMustNotResolveTheIndexBuildWhileAnotherJobIsStillRunning() {
        assertFalse(
            ComposeReadingViewHost.shouldResolveIndexBuild(jobFinished = true, everyJobFinished = false),
            "a finished job while something else is still running says nothing about the index build",
        )
    }

    @Test
    fun anUnfinishedJobNeverResolvesTheIndexBuild() {
        assertFalse(ComposeReadingViewHost.shouldResolveIndexBuild(jobFinished = false, everyJobFinished = false))
        // `everyJobFinished` cannot really be true while this very job is unfinished, but the guard
        // must not depend on that invariant holding in a JSword version that reports them differently.
        assertFalse(ComposeReadingViewHost.shouldResolveIndexBuild(jobFinished = false, everyJobFinished = true))
    }

    /** The liveness half: once nothing is running any more, the finish event MUST resolve — otherwise
     *  the sheet would sit in `Indexing` forever. */
    @Test
    fun aFinishedJobWithNothingElseRunningResolvesTheIndexBuild() {
        assertTrue(ComposeReadingViewHost.shouldResolveIndexBuild(jobFinished = true, everyJobFinished = true))
    }

    // ---- Review item 2: the Unavailable message with no document to name ------------------------

    /**
     * `searchUnavailableDocName` is composed from `currentDocument?.name.orEmpty()`, and "no current
     * document at all" (an error page) is itself one of `searchKindFor`'s `Unavailable` cases — so the
     * parameterised string produced a snackbar reading " cannot be searched".
     */
    @Test
    fun theUnavailableMessageFallsBackToAGenericSentenceWhenThereIsNoDocumentName() {
        val strings = AndroidStrings(ApplicationProvider.getApplicationContext())

        val message = ComposeReadingViewHost.searchUnavailableMessage("", strings)

        assertEquals(strings.searchNotAvailable, message)
        assertFalse(message.startsWith(" "), "the empty-name string must not be interpolated at all")
        assertTrue(message.isNotBlank())
    }

    @Test
    fun theUnavailableMessageNamesTheDocumentWhenThereIsOne() {
        val strings = AndroidStrings(ApplicationProvider.getApplicationContext())

        assertEquals(
            strings.searchNotAvailableForDocument("Strong's Hebrew"),
            ComposeReadingViewHost.searchUnavailableMessage("Strong's Hebrew", strings),
        )
    }

    // ---- Review item 4: the per-open UI flags are cleared with the session ----------------------

    /**
     * Leaving search mode must not leave the recent-terms dropdown or the settings sheet flagged
     * open: re-entering search would come up with the dropdown already down over the field.
     */
    @Test
    fun leavingSearchModeClearsTheRecentTermsMenuAndTheSettingsSheetFlags() {
        val host = host()
        host.openSearch()
        host.searchRecentMenuOpen.value = true
        host.searchSettingsOpen.value = true

        assertTrue(host.closeSearchIfOpen(), "first back closes the sheet")
        assertTrue(host.closeSearchIfOpen(), "second back leaves search mode")

        assertFalse(host.searchRecentMenuOpen.value, "the recent-terms dropdown must not stay flagged open")
        assertFalse(host.searchSettingsOpen.value, "nor the settings sheet")
    }

    // ---- Review item 7: the recent-terms MRU is re-read per open --------------------------------

    /**
     * The MRU store is shared with the classic/EPUB search Activities, and this host outlives any
     * single search — so a term recorded there while the host was alive must be picked up on the next
     * open, not clobbered by the host's stale in-memory list.
     */
    @Test
    fun openingSearchRereadsTheRecentTermsWrittenByTheClassicPath() {
        val host = host()

        // Exactly what `SearchComposeActivity` writes (newline-separated, same settings key). The
        // write REPLACES the key, so this asserts an exact list without depending on what any other
        // test in this JVM left in the store.
        CommonUtils.settings.setString("search_recent_terms", "water\nlight")

        host.openSearch()

        assertEquals(listOf("water", "light"), host.searchController.queries.recentTerms.value)
    }

    // ---- The host seam ------------------------------------------------------------------------

    /**
     * Probe: `mountComposeView` accepts the five new search parameters and still mounts. Same
     * minimal-mount style (and same limits) as `AgentLogHostTest.mountAcceptsAgentLogSlot…`.
     */
    @Test
    fun mountAcceptsTheSearchParamsWithoutCrashing() {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())

        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightModeState = mutableStateOf(false),
            pane = { },
            searchBarState = MutableStateFlow(ReadingSearchBarState(query = "light")),
            searchBarCallbacks = ReadingSearchBarCallbacks(
                onQueryChange = {},
                onSubmit = {},
                onRecentTermsOpen = {},
                onRecentTermsDismiss = {},
                onRecentTermSelected = {},
                onOpenSettings = {},
                onClose = {},
                onImeRequestHandled = {},
                onFieldFocusChanged = {},
                onRebuildIndex = {},
            ),
            searchSheetVisibleState = MutableStateFlow(true),
            onSearchSheetDismissed = {},
            searchSheetSlot = { },
            searchSettingsSlot = { },
        )

        assertTrue(
            (0 until container.childCount).any { container.getChildAt(it) is ComposeView },
            "mountComposeView must still add its ComposeView child with the new search params present",
        )
    }

    // ---- F6 Task 10: the Unavailable message --------------------------------------------------

    /**
     * Defect 2's own symptom, from the user's side: a dictionary used to return zero results with
     * no explanation. `open()` on a document [searchKindFor] classifies `Unavailable` must show the
     * message instead of entering search mode — there is nothing to search, so the toolbar must not
     * switch to the search field and the (always-present) sheet must not open.
     */
    @Test
    fun openingSearchOnADictionaryShowsTheUnavailableMessageAndStaysOutOfSearchMode() {
        val dict = book("HostDict", category = "Lexicons / Dictionaries", modDrv = "RawLD", indexed = true)
        // Must be registered in `Books.installed()` — `CurrentPageBase.currentDocument`'s getter
        // treats an unregistered book as `isRemoved` (`FakeBookFactory.isRemoved`) and silently
        // swaps in whatever real dictionary happens to be installed instead, which is exactly the
        // "dictionary page auto-selects a default document" trap `CachedKeyPageTest` documents.
        Books.installed().addBook(dict)
        try {
            // `setCurrentDocument` (not `currentDictionary.setCurrentDocument` + a direct
            // `currentPage` assignment — `currentPage`'s setter is private) sets `currentPage`
            // synchronously before it may fire a key-chooser popup, so this is safe against a
            // never-`.create()`d activity.
            windowRepository.activeWindow.pageManager.setCurrentDocument(dict)

            val host = host()
            assertNull(host.searchUnavailableDocName.value, "sanity: nothing pending before open()")

            host.openSearch()

            assertFalse(host.searchController.searchModeActive.value, "a dictionary has nothing to search")
            assertFalse(host.searchController.sheetVisible.value, "the sheet must not open for it either")
            assertEquals(
                "HostDict",
                host.searchUnavailableDocName.value,
                "the message payload must carry the JSword display name, ready for the snackbar",
            )
        } finally {
            Books.installed().removeBook(dict)
        }
    }

    /** [ComposeReadingViewHost.searchUnavailableMessageShown] is what the composable calls once it
     *  has started showing the snackbar — it must clear the payload so the same message is not
     *  replayed on the next unrelated recomposition. */
    @Test
    fun searchUnavailableMessageShownClearsThePendingMessage() {
        val dict = book("HostDict2", category = "Lexicons / Dictionaries", modDrv = "RawLD", indexed = true)
        Books.installed().addBook(dict)
        try {
            windowRepository.activeWindow.pageManager.setCurrentDocument(dict)
            val host = host()
            host.openSearch()
            assertEquals("HostDict2", host.searchUnavailableDocName.value, "sanity")

            host.searchUnavailableMessageShown()

            assertNull(host.searchUnavailableDocName.value)
        } finally {
            Books.installed().removeBook(dict)
        }
    }

    // ---- F6 Task 11: an index prompt for a translation other than the one being read ------------

    /**
     * The gap this task closes: [ReadingSearchController] only derives `NeedsIndex` from the
     * ACTIVE window's document, so before `promptIndexFor` existed, choosing an unindexed
     * translation in the results selector had no way to prompt for THAT translation. Two fresh,
     * genuinely unindexed fake Bibles (real `Books.installed()` entries, real `indexStatus`, no
     * fake `BibleSearchService`) prove `onSearchTranslationsChosen` redirects the session to the
     * FIRST one rather than re-deriving `NeedsIndex` from the active KJV.
     */
    @Test
    fun choosingAnUnindexedTranslationInTheSelectorPromptsForThatTranslationNotTheActiveDocument() {
        val u1 = unindexedFakeBible("HostU1")
        val u2 = unindexedFakeBible("HostU2")
        Books.installed().addBook(u1)
        Books.installed().addBook(u2)
        try {
            val host = host()
            host.openSearch()
            assertTrue(host.searchController.sheetVisible.value, "sanity: a session is open")

            host.onSearchTranslationsChosen(listOf("HostU1", "HostU2"))

            assertEquals(
                ReadingSearchPhase.NeedsIndex("HostU1", forEpub = false),
                host.searchController.phase.value,
                "must prompt for the CHOSEN translation, not KJV (the active document)",
            )
            assertTrue(host.searchController.sheetVisible.value)
            assertEquals(
                listOf("HostU1", "HostU2"),
                host.searchSelectorPendingIdsForTest,
                "the whole chosen set must be remembered so the second can be chained to later",
            )
        } finally {
            Books.installed().removeBook(u1)
            Books.installed().removeBook(u2)
        }
    }

    /** A selection with no unindexed translation at all must not arm the pending-chain field —
     *  that field is what keeps an ordinary submit from ever chaining (see Step 3's gate). */
    @Test
    fun choosingAFullyIndexedSelectionArmsNoPendingChain() {
        val indexed = unindexedFakeBible("HostIdx").apply { indexStatus = IndexStatus.DONE }
        Books.installed().addBook(indexed)
        try {
            val host = host()
            host.openSearch()

            host.onSearchTranslationsChosen(listOf("HostIdx"))

            assertNull(host.searchSelectorPendingIdsForTest)
        } finally {
            Books.installed().removeBook(indexed)
        }
    }

    /** Cancelling out of search entirely must leave nothing armed for a later, unrelated session —
     *  the single cleanup point ([ComposeReadingViewHost]'s `onSearchModeClosed`) covers both the
     *  first back press (closes the sheet) and the second (leaves search mode). */
    @Test
    fun leavingSearchClearsThePendingSelectorChain() {
        val u1 = unindexedFakeBible("HostU3")
        Books.installed().addBook(u1)
        try {
            val host = host()
            host.openSearch()
            host.onSearchTranslationsChosen(listOf("HostU3"))
            assertEquals(listOf("HostU3"), host.searchSelectorPendingIdsForTest, "sanity")

            assertTrue(host.closeSearchIfOpen(), "first back closes the sheet")
            assertTrue(host.closeSearchIfOpen(), "second back leaves search mode")

            assertNull(host.searchSelectorPendingIdsForTest)
        } finally {
            Books.installed().removeBook(u1)
        }
    }

    /**
     * F6-B2/B3 fix-round 1: [ComposeReadingViewHost.searchFieldFocused] must be reset by
     * [ComposeReadingViewHost.onSearchModeClosed] itself, not only by `leaveSearch()`'s toolbar-close
     * path — because `closeSearchIfOpen()` (the live back-button path) reaches `onSearchModeClosed()`
     * too, and does so on the FIRST press whenever the sheet is not up: an already-indexed document
     * with an empty query opens straight into `Form` (`sheetVisible = false`), so the ordinary way out
     * is a single back press. Before the fix, that press left the flag `true` with no field left on
     * screen, and the activity keys its IME padding on it for the rest of the activity's life.
     */
    @Test
    fun closingSearchViaTheBackButtonFromAnEmptyFormResetsTheFieldFocusFlag() {
        val indexed = unindexedFakeBible("HostFocusIdx").apply { indexStatus = IndexStatus.DONE }
        Books.installed().addBook(indexed)
        try {
            val verse = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
            windowRepository.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(indexed, verse)
            val host = host()

            host.openSearch()
            assertFalse(
                host.searchController.sheetVisible.value,
                "sanity: an already-indexed document with an empty query opens straight into Form",
            )

            // Simulate the toolbar field having taken focus, as `ReadingToolbar`'s `LaunchedEffect`
            // does in response to entering `Form` phase.
            host.searchFieldFocused.value = true

            assertTrue(
                host.closeSearchIfOpen(),
                "a single back press must leave search mode entirely from an empty form",
            )

            assertFalse(
                host.searchFieldFocused.value,
                "the field-focus flag must not survive search mode closing, or the activity's IME " +
                    "padding stays suppressed for the rest of the activity's life",
            )
        } finally {
            Books.installed().removeBook(indexed)
        }
    }

    // ---- The chaining decision itself (mirrors `awaitIndexDone`'s direct-drive style above) -----

    @Test
    fun nextSelectorIndexPromptChainsToTheSecondUnindexedTranslationRatherThanRerunning() {
        val next = ComposeReadingViewHost.nextSelectorIndexPrompt(
            pendingIds = listOf("A", "B"),
            indexDone = true,
            unindexedAmong = { listOf("B") }, // A just finished; B is still not indexed
        )
        assertEquals("B", next)
    }

    @Test
    fun nextSelectorIndexPromptReturnsNullOnceTheSetIsCleanSoTheOrdinaryRerunTakesOver() {
        val next = ComposeReadingViewHost.nextSelectorIndexPrompt(
            pendingIds = listOf("A", "B"),
            indexDone = true,
            unindexedAmong = { emptyList() }, // both now indexed
        )
        assertNull(next)
    }

    /** Gate: the plain "document being read has no index" flow (no selector involved) must never
     *  chain — `pendingIds` is `null` there, and `unindexedAmong` must not even be consulted. */
    @Test
    fun nextSelectorIndexPromptReturnsNullOutsideASelectorDrivenRun() {
        var consulted = false
        val next = ComposeReadingViewHost.nextSelectorIndexPrompt(
            pendingIds = null,
            indexDone = true,
            unindexedAmong = { consulted = true; it },
        )
        assertNull(next)
        assertFalse(consulted, "an ordinary submit must not even query unindexedAmong")
    }

    @Test
    fun nextSelectorIndexPromptReturnsNullWhenTheBuildFailed() {
        val next = ComposeReadingViewHost.nextSelectorIndexPrompt(
            pendingIds = listOf("A"),
            indexDone = false,
            unindexedAmong = { it },
        )
        assertNull(next, "a failed build must fall back to onIndexingFinished(false), not chain")
    }

    // ---- Task 4: the host owns an EPUB search -----------------------------------------------------

    /**
     * An EPUB query must reach the EPUB controller, never the SWORD one — and must actually run:
     * loading flips synchronously (`EpubSearchResultsController.run` sets `_loading.value = true`
     * BEFORE launching the coroutine, so this is deterministic, not a race), and the results sheet's
     * scroll state is reset to a fresh instance, exactly as [ComposeReadingViewHost.runSearch] does
     * for a SWORD query. (Verified this test goes RED against an emptied `runEpubSearch` body — see
     * the fix-round section of `task-4-report.md`.)
     */
    @Test
    fun runEpubSearchDrivesTheEpubControllerAndNotTheSwordOne() {
        val h = host()
        val listStateBefore = h.searchResultsListStateForTest

        h.runEpubSearch("TestEpub", "grace")

        assertTrue(h.epubSearchResults.loading.value, "run() must flip loading synchronously")
        assertNotSame(
            listStateBefore, h.searchResultsListStateForTest,
            "a new EPUB query must reset the sheet's scroll state, same as a SWORD query",
        )
        assertEquals(0, h.searchResults.results.value.total, "the SWORD controller must be untouched")
    }

    /**
     * Review M10 — the OTHER direction of the same routing, which nothing pinned: a Bible query must
     * drive the SWORD controller and leave the EPUB one completely untouched. Without this, a
     * `runSearch` that accidentally ran BOTH (or an `onRunSearch` lambda whose branch inverted) would
     * still pass [runEpubSearchDrivesTheEpubControllerAndNotTheSwordOne] above.
     */
    @Test
    fun runSearchDrivesTheSwordControllerAndNotTheEpubOne() {
        val h = host()

        h.runSearch("KJV", "grace")

        assertTrue(h.searchResults.loading.value, "run() must flip loading synchronously")
        assertFalse(h.epubSearchResults.loading.value, "the EPUB controller must be untouched")
        assertTrue(h.epubSearchResults.results.value.isEmpty(), "no EPUB rows may appear for a Bible query")
    }

    /**
     * Review I1, end to end — the text-selection "Search '…'" seed arrives ALREADY Lucene-decorated
     * (`BibleView`'s `decorateSearchString(sel, PHRASE, ALL, "")` → `" \"…\""`), and handing that to
     * FTS5 is either a syntax error (mode `PHRASE`, quoted twice) or silently wrong (a word mode
     * reading the whole quoted blob as one phrase).
     *
     * Driven through the REAL controller down to the service seam — a Koin-swapped recording
     * [EpubSearchService], the same idiom `ComposeReadingViewHostTest.currentBibleVerseChangedTriggersARefresh`
     * uses — rather than asserting on `epubSearchRunFor` alone: the pure tests below already pin the
     * function, and what a pure test CANNOT catch is `runEpubSearch` forgetting to call it. That
     * failure mode (a seam proved in isolation, unwired in production) is exactly what this port has
     * shipped before.
     */
    @Test
    fun aPreDecoratedEpubSeedReachesTheServiceStrippedAndAsAPhrase() {
        val fake = RecordingEpubSearchService()
        val overrideModule = module { single<EpubSearchService> { fake } }
        loadKoinModules(overrideModule)
        try {
            val h = host()
            h.persistEpubSearchMode(EpubSearchMode.ALL_WORDS)
            // Exactly what the "Search '…'" action produces, stored the way `openSearch` stores it.
            h.openSearch(" \"in the beginning\"", preDecorated = true)

            h.runEpubSearch("TestEpub", "\"in the beginning\"")

            assertEquals(
                listOf(Triple("TestEpub", "in the beginning", EpubSearchMode.PHRASE)), fake.calls,
                "the decoration must be stripped and the run forced to PHRASE — a selection IS a phrase",
            )
            assertEquals(
                EpubSearchMode.ALL_WORDS, h.epubSearchMode.value,
                "but the forcing is one-shot: it must not be written back over the user's choice",
            )
            assertEquals(
                "ALL_WORDS", CommonUtils.settings.getString("epubSearch-SearchType"),
                "nor may it reach the settings key the standalone EPUB Activities share",
            )
        } finally {
            // See `ComposeReadingViewHostTest`'s note: `unloadKoinModules` removes the override's
            // definition WITHOUT restoring the production one, and `GlobalContext` is process-wide
            // across this Gradle test JVM fork — so the real binding is re-installed by hand.
            unloadKoinModules(overrideModule)
            loadKoinModules(module { singleOf(::AndroidEpubSearchService) { bind<EpubSearchService>() } })
            assertTrue(
                GlobalContext.get().get<EpubSearchService>() is AndroidEpubSearchService,
                "must not leak the fake EpubSearchService binding into later tests",
            )
        }
    }

    /** An ordinary (not pre-decorated) EPUB query must reach the service untouched, under the stored
     *  mode — the control case for the test above, and the one every normal search takes. */
    @Test
    fun anOrdinaryEpubQueryReachesTheServiceVerbatimUnderTheStoredMode() {
        val fake = RecordingEpubSearchService()
        val overrideModule = module { single<EpubSearchService> { fake } }
        loadKoinModules(overrideModule)
        try {
            val h = host()
            h.persistEpubSearchMode(EpubSearchMode.ANY_WORD)

            h.runEpubSearch("TestEpub", "\"quoted\" on purpose")

            assertEquals(
                listOf(Triple("TestEpub", "\"quoted\" on purpose", EpubSearchMode.ANY_WORD)), fake.calls,
                "nothing may be stripped or overridden for a query the user typed",
            )
        } finally {
            unloadKoinModules(overrideModule)
            loadKoinModules(module { singleOf(::AndroidEpubSearchService) { bind<EpubSearchService>() } })
        }
    }

    /**
     * Review M3 — the mode store is shared with the two standalone EPUB search Activities, so the
     * host must RE-READ it on every open, not once at construction. Written as "another surface set
     * it while this host was alive", which is the case that used to read back stale.
     */
    @Test
    fun openSearchRereadsTheEpubWordModeFromTheSharedStore() {
        val h = host()
        h.persistEpubSearchMode(EpubSearchMode.FTS)
        // The standalone Activity's write, performed behind this host's back.
        CommonUtils.settings.setString("epubSearch-SearchType", "PHRASE")

        h.openSearch()

        assertEquals(EpubSearchMode.PHRASE, h.epubSearchMode.value)
    }

    // ---- Task 2: an EPUB result tap navigates to the hit's ordinal -----------------------------

    /**
     * [epubKeyFor] is the seam both Compose EPUB-result surfaces
     * ([ComposeReadingViewHost.onEpubSearchResultSelected] and
     * `EpubSearchResultsComposeActivity.onSelect`) resolve a tapped row's key through, and the fix this
     * task makes: classic hands `setCurrentDocumentAndKey` the whole `BookAndKey` with its
     * `OrdinalRange` included (`EpubSearchResults.kt:171-178`), so the reader lands on the hit rather
     * than the top of the fragment.
     *
     * Driving [ComposeReadingViewHost.onEpubSearchResultSelected] itself end to end would need a real
     * EPUB backend with genbook data on disk: the `TestEpub` `NullBackend` fixtures this file already
     * uses (`book(...)`, `givenCurrentDocumentIsAnEpub` in `ReadingSearchEntryPointsTest`) activate to
     * an EMPTY key map (`NullBackend.readIndex()` returns an empty `DefaultKeyList`), so
     * `Book.getKey(text)` throws `NoSuchKeyException` for every key on them — the navigation call would
     * never be reached and the test would pass or fail for a reason unrelated to the ordinal. This pins
     * the pure reattachment logic directly instead: a real KJV verse key stands in for any
     * `Book.getKey`-resolvable key (the resolution itself is untouched by this task; only the ordinal
     * reattachment is), and both call sites are reviewed to confirm they call this SAME function rather
     * than a copy (see the fix-round report).
     */
    @Test
    fun epubKeyForReattachesTheHitOrdinalAsAnOrdinalRange() {
        val kjv = Books.installed().getBook("KJV") as SwordBook

        val key = epubKeyFor(kjv, docId = "KJV", keyId = "KJV:Gen.1.1", ordinal = 12)

        assertEquals("Gen.1.1", key.key.osisRef, "the docId prefix must be stripped before re-resolving")
        assertEquals(12, key.ordinal?.start, "the hit's ordinal must reach the returned BookAndKey")
        assertNull(
            key.ordinal?.end,
            "a single hit has no range end, matching the search hit's own OrdinalRange(it.ordinal)",
        )
    }

    /** A different ordinal for the same key must produce a different result — guards against a wiring
     *  bug that hardcodes or silently drops the parameter (e.g. always attaching `0`). */
    @Test
    fun epubKeyForOrdinalTracksTheArgumentNotTheKey() {
        val kjv = Books.installed().getBook("KJV") as SwordBook

        val first = epubKeyFor(kjv, docId = "KJV", keyId = "KJV:Gen.1.1", ordinal = 3)
        val second = epubKeyFor(kjv, docId = "KJV", keyId = "KJV:Gen.1.1", ordinal = 40)

        assertEquals(3, first.ordinal?.start)
        assertEquals(40, second.ordinal?.start)
    }

    // ---- Review I1: the pure decoration strip ------------------------------------------------------

    @Test
    fun anUndecoratedSeedIsLeftExactlyAsItIs() {
        assertEquals("in the beginning", stripSearchDecoration("in the beginning"))
    }

    @Test
    fun aDecoratedSeedLosesExactlyOneLayerOfQuotes() {
        // What `SearchControl.decorateSearchString(sel, PHRASE, ALL, "")` produces: a leading space
        // (the empty ALL-section term, joined with a literal " ") plus the phrase quotes.
        assertEquals("in the beginning", stripSearchDecoration(" \"in the beginning\""))
        // One layer only — a doubly quoted string keeps its inner pair.
        assertEquals("\"in the beginning\"", stripSearchDecoration("\"\"in the beginning\"\""))
    }

    @Test
    fun quotesInsideTheSelectionSurviveTheStrip() {
        assertEquals("he said \"no\" twice", stripSearchDecoration("\"he said \"no\" twice\""))
        // Unbalanced/one-sided quoting is not decoration, so nothing is removed.
        assertEquals("\"unclosed", stripSearchDecoration("\"unclosed"))
        assertEquals("\"", stripSearchDecoration(" \" "))
    }

    @Test
    fun anOrdinaryRunKeepsTheStoredModeAndTheQueryAsTyped() {
        val params = epubSearchRunFor("\"quoted\" on purpose", preDecorated = false, storedMode = EpubSearchMode.FTS)
        assertEquals("\"quoted\" on purpose", params.query)
        assertEquals(EpubSearchMode.FTS, params.mode)
    }

    @Test
    fun aPreDecoratedRunIsStrippedAndForcedToPhrase() {
        val params = epubSearchRunFor(" \"in the beginning\"", preDecorated = true, storedMode = EpubSearchMode.ANY_WORD)
        assertEquals("in the beginning", params.query)
        assertEquals(EpubSearchMode.PHRASE, params.mode)
    }

    /** The word-mode round-trips through the SAME settings key the standalone EPUB Activity uses. */
    @Test
    fun theEpubSearchModePersistsUnderTheClassicSettingsKey() {
        val h = host()

        h.persistEpubSearchMode(EpubSearchMode.PHRASE)

        assertEquals("PHRASE", CommonUtils.settings.getString("epubSearch-SearchType"))
    }

    /** FTS is stored as an ABSENT value, exactly as classic wrote `searchType?.name`. */
    @Test
    fun theFtsModePersistsAsNoStoredSearchType() {
        val h = host()
        h.persistEpubSearchMode(EpubSearchMode.PHRASE)

        h.persistEpubSearchMode(EpubSearchMode.FTS)

        assertNull(CommonUtils.settings.getString("epubSearch-SearchType"))
    }

    /**
     * The read half ([ComposeReadingViewHost]'s private `loadEpubSearchMode`) has no direct test of
     * its own — this pins the round trip in BOTH directions for every mode, which is exactly what
     * would have caught the wrong-`SearchType`-enum defect (fix-round 1): a fresh host must read back
     * whatever a previous host just persisted.
     */
    @Test
    fun theEpubSearchModeRoundTripsForEveryValueThroughAFreshHost() {
        for (mode in EpubSearchMode.entries) {
            host().persistEpubSearchMode(mode)

            val fresh = host()

            assertEquals(mode, fresh.epubSearchMode.value, "a fresh host must read back the mode $mode just persisted")
        }
    }
}

/** Recording fake — no mocking framework in this repo (see `noopCommands` above for the same idiom). */
private class RecordingEpubSearchService : EpubSearchService {
    val calls = mutableListOf<Triple<String, String, EpubSearchMode>>()
    override fun isIndexed(docId: String): Boolean = true
    override suspend fun searchEpub(docId: String, query: String, mode: EpubSearchMode): List<EpubResultRow> {
        calls.add(Triple(docId, query, mode))
        return emptyList()
    }
}
