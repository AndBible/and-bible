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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.view.activity.page.screen.ComposeReadingViewGeneration
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedcore.search.SearchKind
import net.bible.sharedcore.search.searchKindFor
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.index.IndexStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 */
@RunWith(RobolectricTestRunner::class)
// TestBibleApplication, not the bare Application the other `*HostTest`s use: `Book.isEpub` lives in
// `EpubBookKt`, whose static initializer reads `BibleApplication.application` — a bare Application
// leaves that lateinit unset and every mapping test dies in `<clinit>`.
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingSearchHostTest {

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
     */
    @Test
    fun openingAndClosingSearchMustNotRebuildThePaneSubtree() {
        val generation = ComposeReadingViewGeneration()
        val session = Session(indexedBible)
        session.controller.queries.setQuery("light")

        session.controller.open()
        assertTrue(session.controller.sheetVisible.value, "results must open the sheet")
        assertTrue(session.controller.closeSheet(), "the first back closes the sheet")
        assertTrue(session.controller.closeSearchMode(), "the second leaves search mode")

        assertEquals(0, generation.state.value)
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
}
