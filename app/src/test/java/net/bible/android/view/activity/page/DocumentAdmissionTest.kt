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

package net.bible.android.view.activity.page

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.Document
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowView
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.BridgeDispatcher
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.ConfigInputs
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.DocumentLoaderPlatform
import net.bible.sharedui.reading.session.ReadingLinkHandler
import net.bible.test.DatabaseResetter
import net.bible.test.testCoreStrings
import net.bible.test.TestWindowRepositories
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import net.bible.android.common.toV11n
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page's document admission (#3975 rework 4) against a real `Window.loadText`, the real [DocumentLoader] and the
 * production dispatcher, with ONE real main thread (a single-thread dispatcher with `immediate`, like Android's). The
 * page's calls come from the test thread, which plays the JavaBridge thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentAdmissionTest {
    /** A main dispatcher on one thread whose `immediate` runs inline on that thread, as Android's does. */
    private class SingleThreadMain : MainCoroutineDispatcher() {
        @Volatile lateinit var thread: Thread
        private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "test-main").also { thread = it } }
        override val immediate: MainCoroutineDispatcher = object : MainCoroutineDispatcher() {
            override val immediate: MainCoroutineDispatcher get() = this
            override fun isDispatchNeeded(context: CoroutineContext) = Thread.currentThread() !== thread
            override fun dispatch(context: CoroutineContext, block: Runnable) = executor.execute(block)
        }
        override fun dispatch(context: CoroutineContext, block: Runnable) = executor.execute(block)
        fun shutdown() = executor.shutdownNow()
    }

    private class FakeHost : JsHost {
        val evaluated: MutableList<String> = Collections.synchronizedList(mutableListOf())
        /** The next evaluation matching this throws (once), like a WebView that fails to take the call. */
        @Volatile var failOnce: ((String) -> Boolean)? = null
        override var isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        val posted = mutableListOf<() -> Unit>()
        override fun post(block: () -> Unit) { posted += block }
        fun flushPosted() { val pending = posted.toList(); posted.clear(); pending.forEach { it() } }
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            failOnce?.let { fail -> if (fail(js)) { failOnce = null; throw IllegalStateException("evaluate failed") } }
            evaluated += js
            callback?.invoke("null")
        }
        fun snapshot() = synchronized(evaluated) { evaluated.toList() }
    }

    private val main = SingleThreadMain()
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var window: Window
    private lateinit var session: BibleWindowSession
    private lateinit var loader: DocumentLoader
    private lateinit var view: LoaderView
    private val host = FakeHost()
    private val control = WindowControl()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * What escaped the repository scope's launches. In the app that scope is the activity's `lifecycleScope`, which
     * has no handler: anything recorded here would end the process (BibleApplication's uncaught handler).
     */
    private val uncaught = java.util.concurrent.CopyOnWriteArrayList<Throwable>()

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        repo = WindowRepository(TestWindowRepositories.scope().let { scope ->
            CoroutineScope(scope.coroutineContext + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> uncaught += e })
        })
        control.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        window = repo.activeWindow
        val channel = BibleViewJsChannel(host) {}
        channel.markClientReady()
        session = BibleWindowSession(
            window = window,
            jsChannel = channel,
            hostScope = { sessionScope },
            surface = object : ReadingWindowSurface {
                override fun copyText(label: String?, text: String) {}
                override fun shareText(text: String, subject: String?) {}
                override fun shareHtml(html: String, title: String) {}
                override fun stopSelection(removeRanges: Boolean) {}
                override fun toast(text: String) {}
                override fun openWebSearch(query: String) {}
            },
            navigator = java.lang.reflect.Proxy.newProxyInstance(
                ReadingNavigator::class.java.classLoader, arrayOf(ReadingNavigator::class.java),
            ) { _, m, _ -> error("unexpected navigator call ${m.name}") } as ReadingNavigator,
            windowControlProvider = { control },
        )
        loader = DocumentLoader(session, object : DocumentLoaderPlatform {
            override val lastUpdated = 1L
            override val topOffset = 0f
            override val bottomOffset = 0f
            override val hasActiveIndicator = false
            override fun configInputs(favouriteLabelIds: List<IdType>) = ConfigInputs(
                displaySettings = window.pageManager.actualTextDisplaySettings,
                isActive = true, isBottomWindow = true, hasActiveIndicator = false, nightMode = false,
                showErrorBox = false, favouriteLabels = favouriteLabelIds, recentLabels = emptyList(),
                studyPadCursors = emptyMap(), autoAssignLabels = emptyList(), hideCompareDocuments = emptySet(),
                limitAmbiguousModalSize = false, windowDisplayId = window.displayId,
                disableBibleModalButtons = emptySet(), disableGenericModalButtons = emptySet(),
                monochromeMode = false, einkMode = false, colorEinkMode = false, pureMonochromeMode = false,
                disableAnimations = false, fontSizeMultiplier = 1f, enabledExperimentalFeatures = emptyList(),
                themeColors = "null", disableClickToEdit = false, llmConfigured = false, notesContentType = "HTML",
            )
            override fun isCurrentView() = true
            override fun enableZoomForMap(isMap: Boolean) {}
        }, strings = { testCoreStrings() })
        session.paging = loader
        view = LoaderView()
        window.bibleView = view
    }

    @After
    fun tearDown() {
        try {
            sessionScope.cancel()
            DatabaseResetter.resetDatabase(repo.scope, sessionScope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
            Dispatchers.resetMain()
            main.shutdown()
        }
        assertTrue("a window load must not end the app with an uncaught exception: $uncaught", uncaught.isEmpty())
    }

    /** A window view that forwards to the loader, like BibleView; [failLoadOf] makes a delivery throw. */
    private inner class LoaderView : WindowView {
        @Volatile var failLoadOf: (Document) -> Boolean = { false }
        val requestsEnded = java.util.concurrent.LinkedBlockingQueue<Long>()
        @Volatile var lastRequest = 0L
        override val verseRangeLoaded: VerseRange? = null
        override var lastUpdated: Long = 1L
        override val firstDocument get() = session.firstDocument
        override var listenEvents = true
        override fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) {}
        override fun highlightBibleOrdinalRange(range: IntRange) {}
        override fun destroy() {}
        override suspend fun loadDocument(document: Document, updateLocation: Boolean, key: Key?,
                                          anchorOrdinal: OrdinalRange?, htmlId: String?, windowContentGeneration: Long?) {
            check(!failLoadOf(document)) { "content of $document unreadable" }
            loader.loadDocument(document, updateLocation, key, anchorOrdinal, htmlId, windowContentGeneration)
        }
        override fun scrollOrJumpToVerse(key: Key, forceNow: Boolean) {}
        override fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean) {}
        override fun hasChapterLoaded(chapter: Int) = session.hasChapterLoaded(chapter)
        override fun requestFocus() = false
        override fun updateTextDisplaySettings(onAttach: Boolean) {}
        override fun adjustLoadingCount(adj: Int) = true
        override fun documentRequested() = loader.documentRequested().also { lastRequest = it }
        override fun documentRequestEnded(request: Long, unchanged: Boolean, failure: Throwable?) {
            loader.documentRequestEnded(request, unchanged, failure)
            requestsEnded.add(request)
        }

        fun awaitLastRequestEnded() = waitUntil("request $lastRequest ended") { lastRequest in requestsEnded }
    }

    private fun waitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out: $what" }
            Thread.sleep(10)
        }
        // Let what the main thread has queued behind it run.
        onMain {}
    }

    private fun <T> onMain(block: () -> T): T = runBlocking(main) { block() }

    private fun dispatcher() = BridgeDispatcher(ReadingBridge(session),
        { callId -> session.jsChannel.execute(BibleViewEvents.response(callId, null)) }, {})

    private fun post(method: String, args: String, generation: Long) =
        dispatcher().dispatch("""{"method":"$method","args":$args,"documentGeneration":$generation}""")

    private fun book(initials: String) = Books.installed().getBook(initials)!!
    private fun verse(book: BibleBook, chapter: Int, verse: Int) = Verse((book("KJV") as SwordBook).versification, book, chapter, verse)
    private val shownVerse get() = window.pageManager.currentBible.currentBibleVerse.verse

    /** Shows [verse] of [initials] through `Window.loadText` on the main thread and waits until it is admitted. */
    private fun show(initials: String, verse: Verse): Long {
        val before = session.documentGeneration
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book(initials), verse)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("$initials $verse admitted") {
            session.documentGeneration != NO_DOCUMENT_GENERATION && session.documentGeneration != before
        }
        return session.documentGeneration
    }

    private fun response(callId: Long) = host.snapshot().filter { it.startsWith("bibleView.response($callId, ") }
    private val nullResponse = { callId: Long -> BibleViewEvents.response(callId, null).js + ";" }

    /**
     * Finding 1 (codex I1, opus I1): a chapter request admitted for the page's document performs its effect (the
     * chapter range) in the same main-thread step as the check. Before, the check ran on the bridge thread and the
     * effect later: John 6's request extended Genesis 3's range (3 -> 4) when Genesis was admitted in between.
     */
    @Test
    fun aChapterRequestCannotExtendTheRangeOfADocumentAdmittedAfterItsCheck() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val caller = Thread {
            loader.runForPageDocument(a, call = {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                loader.requestMoreToEnd(51)
            }, drop = { session.jsChannel.execute(BibleViewEvents.response(51, null)) })
        }.apply { start() }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        // The user navigates to Genesis 3 while the accepted request has not acted yet.
        sessionScope.launch(main) {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), verse(BibleBook.GEN, 3, 1))
            window.loadText()
        }
        Thread.sleep(500)
        release.countDown()
        caller.join(10_000)
        view.awaitLastRequestEnded()
        waitUntil("Genesis admitted") { session.documentGeneration != a && session.documentGeneration != NO_DOCUMENT_GENERATION }
        waitUntil("51 answered") { response(51).isNotEmpty() }
        assertEquals("Genesis 3's range", 3 to 3, session.minChapter to session.maxChapter)
    }

    /**
     * Finding 1, second half: `setCurrentDocument` changes the module before `Window.loadText` requests it. A chapter
     * request of the page in that gap must not read the new module's chapter into the old page.
     */
    @Test
    fun aChapterRequestBetweenAModuleChangeAndItsRequestWaitsForTheNewDocument() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        // The module changes; Window.loadText (the request) comes later.
        onMain { window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011")) }
        post("requestMoreToEnd", "[52]", a)
        Thread.sleep(500)
        onMain {}
        assertEquals("not answered with the new module's chapter: ${response(52)}", emptyList<String>(), response(52))

        onMain { window.loadText() }
        view.awaitLastRequestEnded()
        waitUntil("52 answered") { response(52).isNotEmpty() }
        assertEquals("the page was replaced: answered null", listOf(nullResponse(52)), response(52))
    }

    /**
     * Finding 3 (codex I3): a failing load of another book of the SAME module (KJV John 6 -> KJV Genesis 1) must not
     * admit John's page again: its report would overwrite the requested Genesis 1:1 and its paging would read Genesis.
     */
    @Test
    fun aFailedLoadOfAnotherBookOfTheSameModuleDoesNotAdmitTheOldPage() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        view.failLoadOf = { (it as? BibleDocument)?.verseRange?.start?.book == BibleBook.GEN }
        val genesis = verse(BibleBook.GEN, 1, 1)
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), genesis)
            window.loadText()
        }
        view.awaitLastRequestEnded()

        post("scrolledToOrdinal", "[\"John.6\",${verse(BibleBook.JOHN, 6, 20).ordinal}]", a)
        onMain {}
        assertEquals("the old page's report must not move the window off Genesis 1:1", genesis.ordinal, shownVerse.ordinal)
        post("requestMoreToEnd", "[53]", a)
        waitUntil("53 answered") { response(53).isNotEmpty() }
        assertEquals("no Genesis chapter for John's page", listOf(nullResponse(53)), response(53))
    }

    /**
     * Finding 4 (codex I4): after a request of another module that ended without a document, returning to the page's
     * document restores ALL of its admission, paging included (before: reports admitted again, paging answered null
     * for good, which ends infinite scroll on the page).
     */
    @Test
    fun returningToThePagesDocumentRestoresItsPaging() {
        val john = verse(BibleBook.JOHN, 6, 1)
        val a = show("KJV", john)
        // Module changes without their loads (the requests end without a document): to ESV and back.
        onMain {
            window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011"))
            loader.documentRequestEnded(loader.documentRequested())
        }
        onMain {
            window.pageManager.currentBible.onlySetCurrentDocument(book("KJV"))
            loader.documentRequestEnded(loader.documentRequested())
        }
        host.evaluated.clear()
        post("requestMoreToEnd", "[54]", a)
        waitUntil("54 answered") { response(54).isNotEmpty() }
        assertFalse("John 7, not null: ${response(54)}", response(54).single() == nullResponse(54))
        assertEquals(7, session.maxChapter)
    }

    /**
     * Finding 5 (opus I3): a request that does not change the page's document (a reload of the same chapter, an
     * unchanged synced commentary) does not close the gate: the page's restore state still reaches the window. A
     * request of another document does close it, and a scroll report waits for no request at all.
     */
    @Test
    fun aRequestOfTheSameDocumentKeepsThePagesNonPositionCallsAdmitted() {
        val john = verse(BibleBook.JOHN, 6, 1)
        val a = show("KJV", john)
        onMain { view.documentRequested() }
        post("saveState", "[\"same\"]", a)
        onMain {}
        assertEquals("restore state of the same document is kept", "same", window.pageManager.jsState)
        post("scrolledToOrdinal", "[\"John.6\",${verse(BibleBook.JOHN, 6, 20).ordinal}]", a)
        onMain {}
        assertEquals("a scroll report waits while the request reads the key", john.ordinal, shownVerse.ordinal)

        onMain {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), verse(BibleBook.GEN, 1, 1))
            view.documentRequested()
        }
        post("saveState", "[\"other\"]", a)
        onMain {}
        assertFalse("restore state of the replaced document is not stored", window.pageManager.jsState == "other")
    }

    /**
     * Finding 6 (codex residual 2): a navigation off the main thread (sync, restore) changes the key before it
     * requests the document. A report of the page that the main thread runs in between must not move the window back.
     */
    @Test
    fun aReportBetweenAnOffMainKeyChangeAndItsRequestIsDropped() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        val genesis = verse(BibleBook.GEN, 1, 1)
        window.pageManager.currentBible.doSetKey(genesis) // test thread: off main, the request comes later
        post("scrolledToOrdinal", "[\"John.6\",${verse(BibleBook.JOHN, 6, 20).ordinal}]", a)
        onMain {}
        assertEquals(genesis.ordinal, shownVerse.ordinal)
    }

    /**
     * Finding 7 (codex residual 3): a failed load of another document does not leave the window silently closed: an
     * error document with a working retry replaces the old page.
     */
    @Test
    fun aFailedLoadOfAnotherDocumentShowsAnErrorDocumentWithAWorkingRetry() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        view.failLoadOf = { (it as? BibleDocument)?.verseRange?.start?.book == BibleBook.GEN }
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), verse(BibleBook.GEN, 1, 1))
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("an error document is sent", 3_000) {
            host.snapshot().any { it.contains("add_documents") && it.contains("'type': `error`") && it.contains("'retry': true") }
        }
        val errorGeneration = session.documentGeneration
        assertTrue(errorGeneration != NO_DOCUMENT_GENERATION)

        // The page shows the error document; its retry link loads the window again, which now succeeds.
        view.failLoadOf = { false }
        post("documentShown", "[$errorGeneration]", errorGeneration)
        onMain {}
        assertTrue("the page shows the admitted error document: ${session.admission.state.let { "${it.shown} ${it.admitted?.generation} ${it.admitted?.source} ${it.admitted?.contexts} ${it.pending}" }}",
            onMain { session.pageShowsAdmittedDocument() })
        val handled = onMain { ReadingLinkHandler(session) { _, _, _, _ -> }.handle("ab-retry://") }
        assertTrue(handled)
        view.awaitLastRequestEnded()
        waitUntil("Genesis admitted after the retry") {
            (session.firstDocument as? BibleDocument)?.verseRange?.start?.book == BibleBook.GEN
        }
    }

    // ---- Rework 5 (#3975): findings of the v4 reviews ----

    private val kjv get() = (book("KJV") as SwordBook).versification
    private fun admitted() = session.admission.state.admitted
    private fun stateText() = session.admission.state.let {
        "shown ${it.shown} admitted ${it.admitted?.generation} ${it.admitted?.source} ${it.admitted?.contexts} pending ${it.pending?.target}"
    }
    private fun notNull(callId: Long) = response(callId).let { it.isNotEmpty() && it.single() != nullResponse(callId) }

    /**
     * O1 (opus), first case: the top of chapter 1 in a module with book introductions is chapter 0 (the introduction is
     * loaded with chapter 1). A report there must not close the page for good: its calls and paging stay admitted.
     */
    @Test
    fun aReportInTheBookIntroductionKeepsThePageAdmitted() {
        val a = show("KJV", verse(BibleBook.GEN, 1, 1))
        post("scrolledToOrdinal", "[\"Gen.1\",${verse(BibleBook.GEN, 0, 0).ordinal}]", a)
        onMain {}
        assertEquals("the report moved the window into the introduction", 0, shownVerse.chapter)
        post("saveState", "[\"intro\"]", a)
        onMain {}
        assertEquals("admitted after an introduction report: ${stateText()}", "intro", window.pageManager.jsState)
        post("requestMoreToEnd", "[61]", a)
        waitUntil("61 answered") { response(61).isNotEmpty() }
        assertTrue("Genesis 2, not null: ${response(61)}", notNull(61))
        assertEquals(2, session.maxChapter)
    }

    /** O1, the same trap at load time: a window stored in the introduction (chapter 0) pages on from chapter 1. */
    @Test
    fun aWindowStoredInTheIntroductionPagesFromChapterOne() {
        val a = show("KJV", verse(BibleBook.GEN, 0, 0))
        post("requestMoreToEnd", "[62]", a)
        waitUntil("62 answered") { response(62).isNotEmpty() }
        assertTrue("Genesis 2, not null: ${response(62)} ${stateText()}", notNull(62))
        assertTrue("Genesis 2, not chapter 1 again: ${response(62)}", response(62).single().contains("Gen.2"))
    }

    /**
     * O1, second case: the window's verse is in another versification than its module (a module switch from a
     * Synodal Bible keeps the Synodal verse: Psalm 9:22 Synodal is Psalm 10:1 KJV). The KJV page holds Psalm 10; its
     * reports are KJV ordinals, its paging continues with Psalm 11, and its calls stay admitted.
     */
    @Test
    fun aKjvPageOfAWindowHoldingASynodalVerseStaysAdmitted() {
        val synodal = Versifications.instance().getVersification("Synodal")!!
        val stored = Verse(synodal, BibleBook.PS, 9, 22)
        assertEquals("fixture: Synodal Psalm 9:22 is KJV Psalm 10", 10, stored.toV11n(kjv).chapter)
        val before = session.documentGeneration
        onMain {
            window.pageManager.currentBible.onlySetCurrentDocument(book("KJV"))
            window.pageManager.currentBibleVerse.setVerseSelected(synodal, stored)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("KJV Psalm 10 admitted") { session.documentGeneration.let { it != NO_DOCUMENT_GENERATION && it != before } }
        val a = session.documentGeneration

        val reported = Verse(kjv, BibleBook.PS, 10, 5)
        post("scrolledToOrdinal", "[\"Ps.10\",${reported.ordinal}]", a)
        onMain {}
        assertEquals("the report is a KJV ordinal", reported.getOsisID(), shownVerse.toV11n(kjv).getOsisID())
        post("saveState", "[\"psalm\"]", a)
        onMain {}
        assertEquals("admitted after its own report: ${stateText()}", "psalm", window.pageManager.jsState)
        post("requestMoreToEnd", "[63]", a)
        waitUntil("63 answered") { response(63).isNotEmpty() }
        assertTrue("KJV Psalm 11: ${response(63)} ${stateText()}", notNull(63) && response(63).single().contains("Ps.11"))
        post("scrolledToOrdinal", "[\"Ps.11\",${Verse(kjv, BibleBook.PS, 11, 2).ordinal}]", a)
        onMain {}
        assertEquals("Ps.11.2", shownVerse.toV11n(kjv).getOsisID())
        post("saveState", "[\"psalm 11\"]", a)
        onMain {}
        assertEquals("admitted in the scrolled-in chapter: ${stateText()}", "psalm 11", window.pageManager.jsState)
    }

    private val dao get() = DatabaseContainer.instance.myDocumentDb.myDocumentDao()

    /** A registered MyDocument (a general book) with pages [pageKeys]; unregistered in [cleanUpMyDocument]. */
    private fun myDocument(initials: String, vararg pageKeys: String): org.crosswire.jsword.book.Book = runBlocking {
        MyDocumentBookManager.unregisterDocument(initials)
        val document = MyDocument(name = "Test $initials", initials = initials)
        dao.insert(document)
        pageKeys.forEachIndexed { i, key ->
            dao.insertPageWithContent(MyDocumentPage(documentId = document.id, title = "Title $key", pageKey = key,
                contentType = MyDocumentContentType.MARKDOWN, orderNumber = i), "content of $key")
        }
        myDocuments += initials
        MyDocumentBookManager.registerDocument(document)!!
    }
    private val myDocuments = mutableListOf<String>()

    @After
    fun cleanUpMyDocument() {
        myDocuments.forEach { MyDocumentBookManager.unregisterDocument(it) }
    }

    private fun showEntry(book: org.crosswire.jsword.book.Book, key: Key): Long {
        val before = session.documentGeneration
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book, key)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("${book.initials} $key admitted: ${stateText()}") {
            session.documentGeneration.let { it != NO_DOCUMENT_GENERATION && it != before }
        }
        return session.documentGeneration
    }

    /**
     * O2 (opus) / C1 (codex): a general book page (MyDocument, AI documents, EPUB) that another entry of the same book
     * replaces (a link to another entry, selecting another AI page) is a document change: the outgoing page's restore
     * state must not be written to the window while the other entry is requested.
     */
    @Test
    fun anEntryOfTheSameGeneralBookReplacingThePageIsADocumentChange() {
        val book = myDocument("MyDoc_3975", "page_a", "page_b")
        val keys = book.getGlobalKeyList().toList()
        val a = showEntry(book, keys[0])
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book, keys[1])
            view.documentRequested()
        }
        post("saveState", "[\"state of a\"]", a)
        onMain {}
        assertFalse("A's restore state written while B is requested: ${stateText()}", window.pageManager.jsState == "state of a")
    }

    /** O2 counterpart: the page's own entry stays admitted (its scroll reports, saveState). */
    @Test
    fun aGeneralBookEntryStaysAdmittedForItsOwnCalls() {
        val book = myDocument("MyDoc_3975b", "page_a", "page_b")
        val keys = book.getGlobalKeyList().toList()
        val a = showEntry(book, keys[0])
        post("saveState", "[\"own\"]", a)
        onMain {}
        assertEquals("own entry admitted: ${stateText()}", "own", window.pageManager.jsState)
    }

    /**
     * C2 (codex): a delivery that fails after the document was admitted (the WebView throws on `clear_document` for
     * Genesis after John) must not leave native admitting a document the page never received: an error document with
     * a retry replaces the page.
     */
    @Test
    fun aDeliveryFailingAfterAdmissionShowsAnErrorDocument() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        host.failOnce = { it.contains("clear_document") }
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), verse(BibleBook.GEN, 1, 1))
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("an error document is sent: ${stateText()}", 3_000) {
            host.snapshot().any { it.contains("add_documents") && it.contains("'type': `error`") && it.contains("'retry': true") }
        }
        assertTrue(session.documentGeneration != a)
    }

    /**
     * Minor (opus): a retry that fails again shows the error document again, instead of nothing.
     */
    @Test
    fun aRetryThatFailsAgainShowsTheErrorDocumentAgain() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        view.failLoadOf = { (it as? BibleDocument)?.verseRange?.start?.book == BibleBook.GEN }
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), verse(BibleBook.GEN, 1, 1))
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("first error document", 3_000) { host.snapshot().any { it.contains("'retry': true") } }
        val first = session.documentGeneration
        post("documentShown", "[$first]", first)
        onMain {}
        host.evaluated.clear()
        assertTrue(onMain { ReadingLinkHandler(session) { _, _, _, _ -> }.handle("ab-retry://") })
        view.awaitLastRequestEnded()
        waitUntil("the error document again: ${stateText()}", 3_000) { host.snapshot().any { it.contains("'retry': true") } }
        assertTrue("a new error generation", session.documentGeneration != first)
    }

    /**
     * Further (codex): after infinite scroll (John 6 -> John 7) the page restarts (iOS WebContent restart) and only
     * John 6 is replayed. The admitted contexts must be what was replayed: a request of John 7 is a document change.
     */
    @Test
    fun aPageRestartResetsTheContextsToTheReplayedDocument() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        post("requestMoreToEnd", "[64]", a)
        waitUntil("64 answered") { notNull(64) }
        onMain { loader.setClientReady() }
        onMain {}
        onMain {
            window.pageManager.setCurrentDocumentAndKey(book("KJV"), verse(BibleBook.JOHN, 7, 1))
            view.documentRequested()
        }
        post("saveState", "[\"restarted\"]", a)
        onMain {}
        assertFalse("John 7 is not in the replayed page: ${stateText()}", window.pageManager.jsState == "restarted")
    }

    /**
     * Further (codex): a chapter request waiting because the window showed another module (no request yet) runs when
     * the window returns to the page's module, even without a loadText.
     */
    @Test
    fun aWaitingChapterRequestRunsWhenTheWindowReturnsToThePagesSource() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        onMain { window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011")) }
        post("requestMoreToEnd", "[65]", a)
        Thread.sleep(300)
        onMain {}
        assertTrue("waits while another module is shown", response(65).isEmpty())
        onMain { window.pageManager.currentBible.onlySetCurrentDocument(book("KJV")) }
        waitUntil("65 answered after the return: ${stateText()}", 5_000) { response(65).isNotEmpty() }
        assertTrue("John 7: ${response(65)}", notNull(65))
    }

    /**
     * Further (codex): a dictionary page without a key selects its first entry while it is fetched (off main). The
     * window's own load must not reject that key as a newer request; the dictionary is shown and admitted.
     */
    @Test
    fun aDictionaryWithoutAKeyIsShownWithItsFirstEntry() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        val dictionary = book("StrongsGreek")
        val before = session.documentGeneration
        onMain {
            window.pageManager.setCurrentDocument(dictionary)
            window.pageManager.currentDictionary.doSetKey(null)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("the dictionary admitted: ${stateText()}", 5_000) {
            session.documentGeneration.let { it != NO_DOCUMENT_GENERATION && it != before } &&
                admitted()?.source?.book == dictionary
        }
    }

    @Test
    fun rework6NullDictionaryKeyRejectsAnUnrelatedKeyWithoutANewLoad() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        val dictionary = book("StrongsGreek")
        val keys = dictionary.getGlobalKeyList().toList()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val originalSink = net.bible.sharedcore.log.Log.sinkOverride
        net.bible.sharedcore.log.Log.sinkOverride = { _, _, message, _ ->
            if (message.startsWith("Loading document:") && message.contains(dictionary.initials)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        try {
            val before = session.documentGeneration
            onMain {
                window.pageManager.setCurrentDocument(dictionary)
                window.pageManager.currentDictionary.doSetKey(null)
                window.loadText()
            }
            assertTrue("dictionary fetch reached its read boundary", entered.await(10, TimeUnit.SECONDS))
            onMain { window.pageManager.currentDictionary.doSetKey(keys[1]) }
            release.countDown()
            view.awaitLastRequestEnded()
            onMain {}
            assertEquals("stale fetch must not publish under an unrelated key", before, session.documentGeneration)
        } finally {
            release.countDown()
            net.bible.sharedcore.log.Log.sinkOverride = originalSink
        }
    }

    /** Minor (opus): a chapter whose read fails is taken out of the page's range and contexts again. */
    @Test
    fun aFailedChapterReadIsNotKeptInThePagesRange() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        loader.chapterReadFailureForTest = { it == 7 }
        try {
            post("requestMoreToEnd", "[66]", a)
            waitUntil("66 answered") { response(66).isNotEmpty() }
            onMain {}
            assertEquals("answered null", listOf(nullResponse(66)), response(66))
            assertEquals("John 7 not kept", 6 to 6, session.minChapter to session.maxChapter)
            assertFalse("John 7 not a context: ${stateText()}", "John.7" in admitted()!!.contexts)
        } finally {
            loader.chapterReadFailureForTest = null
        }
    }

    /**
     * Review of rework 5 (I1): the window scrolled into the chapter infinite scroll added (John 7) when the page
     * restarts; the replay holds John 6 only. The window must not stay gated: it loads where it is (John 7) and the
     * new page's calls are admitted.
     */
    @Test
    fun aPageRestartAfterScrollingIntoAnAddedChapterLoadsTheWindowsPosition() {
        val a = show("KJV", verse(BibleBook.JOHN, 6, 1))
        post("requestMoreToEnd", "[67]", a)
        waitUntil("67 answered") { notNull(67) }
        post("scrolledToOrdinal", "[\"John.7\",${verse(BibleBook.JOHN, 7, 3).ordinal}]", a)
        onMain {}
        assertEquals("John.7.3", shownVerse.getOsisID())
        val before = view.lastRequest
        onMain { loader.setClientReady() }
        waitUntil("a new request after the restart: ${stateText()}", 5_000) { view.lastRequest != before }
        view.awaitLastRequestEnded()
        waitUntil("John 7 admitted: ${stateText()}", 5_000) { "John.7" in (admitted()?.loaded ?: emptySet()) }
        val g = session.documentGeneration
        post("saveState", "[\"after restart\"]", g)
        onMain {}
        assertEquals("after restart", window.pageManager.jsState)
    }

    /** Independent v5 review: notes use KJVA ordinals even when the window retains a foreign verse. */
    @Test
    fun reviewV5MyNotesWithAForeignVerseJumpsToItsKjvaOrdinal() {
        val synodal = Versifications.instance().getVersification("Synodal")!!
        val stored = Verse(synodal, BibleBook.PS, 9, 22)
        val expected = stored.toV11n(net.bible.android.database.bookmarks.KJVA)
        val notes = org.koin.mp.KoinPlatform.getKoin().get<net.bible.service.download.FakeBookFactory>().myNotesDocument
        onMain {
            window.pageManager.setCurrentDocumentAndKey(notes, stored)
            window.pageManager.currentBibleVerse.setVerseSelected(synodal, stored)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("notes admitted") { session.firstDocument is net.bible.android.control.page.MyNotesDocument }
        val setup = host.snapshot().last { it.contains("setup_content") }
        assertTrue("KJVA ordinal ${expected.ordinal}, not Synodal ${stored.ordinal}: $setup",
            setup.contains("\"jumpToOrdinal\": ${expected.ordinal}"))
        post("saveState", "[\"notes admitted\"]", session.documentGeneration)
        onMain {}
        assertEquals("notes admitted", window.pageManager.jsState)
    }

    /** Independent v5 review: requests owned by the dead JS page must not advance the replayed page's range. */
    @Test
    fun reviewV5ARestartDiscardsWaitingPagingOfThePreviousJsPage() {
        val generation = show("KJV", verse(BibleBook.JOHN, 6, 1))
        onMain {
            window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011"))
            loader.runForPageDocument(generation,
                call = { loader.requestMoreToEnd(84) },
                drop = { session.jsChannel.execute(BibleViewEvents.response(84, null)) })
            window.pageManager.currentBible.onlySetCurrentDocument(book("KJV"))
            loader.setClientReady()
        }
        Thread.sleep(700)
        onMain {}
        assertEquals("the restarted page only received John 6; no stale request may reserve John 7",
            6 to 6, session.minChapter to session.maxChapter)
        assertFalse("no chapter response from the previous JS page may reach the restarted one", notNull(84))
    }

    /** Independent v5 review: replay delivery needs the same failure recovery as a freshly loaded document. */
    @Test
    fun reviewV5ARestartDeliveryFailureShowsARetryableErrorDocument() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        host.failOnce = { it.contains("clear_document") }
        // Production dispatch catches exceptions: without loader recovery the page silently stays blank.
        dispatcher().dispatch("""{"method":"setClientReady","args":[]}""")
        waitUntil("restart delivers a retryable error", 3_000) {
            host.snapshot().any { it.contains("add_documents") && it.contains("'retry': true") }
        }
    }

    @Test
    fun reviewV6QueuedRestartDeliveryFailureShowsRetryableError() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        host.failOnce = { it.contains("clear_document") }
        host.isMainThread = false
        dispatcher().dispatch("""{"method":"setClientReady","args":[]}""")
        session.jsChannel.runOnMain { }
        host.isMainThread = true
        host.flushPosted()
        assertLastReplayIsRetryableError()
    }

    @Test fun reviewV6QueuedRestartResetFailureShowsRetryableError() = assertQueuedPrefixFailure("reset_loading_count")

    @Test fun reviewV6QueuedRestartLabelsFailureShowsRetryableError() = assertQueuedPrefixFailure("update_labels")

    private fun assertQueuedPrefixFailure(failedEvent: String) {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        host.failOnce = { it.contains(failedEvent) }
        host.isMainThread = false
        dispatcher().dispatch("""{"method":"setClientReady","args":[]}""")
        session.jsChannel.runOnMain { }
        host.isMainThread = true
        host.flushPosted()
        assertLastReplayIsRetryableError()
    }

    /** A queued replay whose labels upload fails later must not leave the labels marked as uploaded for the error document. */
    @Test
    fun followUpQueuedRestartLabelsFailureUploadsLabelsAgainWithTheErrorDocument() {
        assertQueuedPrefixFailure("update_labels")
        val events = host.snapshot()
        val errorAt = events.indexOfLast { it.contains("clear_document") && it.contains("add_documents") }
        assertTrue("labels are uploaded again before the error document: $events",
            events.take(errorAt).any { it.contains("update_labels") })
    }

    @Test fun reviewV6ImmediateRestartResetFailureShowsRetryableError() = assertImmediateReplayFailure("reset_loading_count")
    @Test fun reviewV6ImmediateRestartLabelsFailureShowsRetryableError() = assertImmediateReplayFailure("update_labels")
    @Test fun reviewV6ImmediateRestartClearFailureShowsRetryableError() = assertImmediateReplayFailure("clear_document")

    private fun assertImmediateReplayFailure(failedEvent: String) {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()
        host.failOnce = { it.contains(failedEvent) }
        dispatcher().dispatch("""{"method":"setClientReady","args":[]}""")
        assertLastReplayIsRetryableError()
    }

    private fun assertLastReplayIsRetryableError() {
        val last = host.snapshot().last { it.contains("clear_document") && it.contains("add_documents") }
        assertTrue("last delivered document must be retryable: $last", last.contains("'retry': true"))
        assertTrue("last delivered generation must match admission: $last",
            last.contains(BibleViewEvents.clearDocument(session.admission.admittedGeneration).js))
        assertTrue("the admitted document must be an error", session.admission.state.admitted!!.isError)
        assertTrue("the active document must be an error", session.firstDocument is net.bible.android.control.page.ErrorDocument)
    }

    /** Independent v5 review: an absent book must produce a usable error page, not a permanently closed window. */
    @Test
    fun reviewV5ABookMissingFromTheModuleDoesNotGateTheWindow() {
        show("KJV", verse(BibleBook.JOHN, 6, 1))
        val stored = Verse(net.bible.android.database.bookmarks.KJVA, BibleBook.TOB, 1, 1)
        onMain {
            window.pageManager.currentBibleVerse.setVerseSelected(stored.versification, stored)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("missing book document delivered") { session.admission.state.pending == null }
        val generation = session.documentGeneration
        assertTrue("a missing book leaves an admitted error document: ${stateText()}",
            onMain { session.admission.admits(generation) })
        post("saveState", "[\"missing book\"]", generation)
        onMain {}
        assertEquals("missing book", window.pageManager.jsState)
    }

    /** Independent v5 review: chapter-zero conversion and nonexistent verses retain a callable page. */
    @Test
    fun reviewV5ForeignIntroductionsAndMissingVersesKeepBibleAdmissionOpen() {
        val synodal = Versifications.instance().getVersification("Synodal")!!
        val cases = listOf(
            Verse(synodal, BibleBook.GEN, 0, 0),
            Verse(synodal, BibleBook.PS, 0, 0),
            Verse(synodal, BibleBook.PS, 151, 1),
            Verse(net.bible.android.database.bookmarks.KJVA, BibleBook.TOB, 1, 1),
        )
        cases.forEach { stored ->
            onMain {
                window.pageManager.currentBible.onlySetCurrentDocument(book("KJV"))
                window.pageManager.currentBibleVerse.setVerseSelected(stored.versification, stored)
                window.loadText()
            }
            view.awaitLastRequestEnded()
            waitUntil("${stored.getOsisID()} request settled") { session.admission.state.pending == null }
            val generation = session.documentGeneration
            assertTrue("${stored.getOsisID()} must not gate the window: ${stateText()}",
                onMain { session.admission.admits(generation) })
            if (session.firstDocument is BibleDocument) {
                assertEquals("Bible jump uses the page versification", kjv, (session.initialKey as Verse).versification)
                if (stored.chapter == 0) assertEquals("introduction pages onward from chapter 1", 1, session.maxChapter)
            }
        }
    }

    /** Independent v5 review: KJVA reports must leave notes admitted even at a split Synodal chapter boundary. */
    @Test
    fun reviewV5MyNotesReportAcrossASplitChapterDoesNotCloseAdmission() {
        val synodal = Versifications.instance().getVersification("Synodal")!!
        val stored = Verse(synodal, BibleBook.PS, 9, 1)
        val notes = org.koin.mp.KoinPlatform.getKoin().get<net.bible.service.download.FakeBookFactory>().myNotesDocument
        onMain {
            window.pageManager.setCurrentDocumentAndKey(notes, stored)
            window.pageManager.currentBibleVerse.setVerseSelected(synodal, stored)
            window.loadText()
        }
        view.awaitLastRequestEnded()
        waitUntil("split chapter notes admitted") { session.firstDocument is net.bible.android.control.page.MyNotesDocument }
        val generation = session.documentGeneration
        assertTrue("fixture spans KJVA Psalms 9 and 10: ${stateText()}", "Ps.10" in admitted()!!.contexts)
        val reported = Verse(net.bible.android.database.bookmarks.KJVA, BibleBook.PS, 10, 5)
        post("scrolledToOrdinal", "[\"Ps.10\",${reported.ordinal}]", generation)
        onMain {}
        assertEquals("Ps.10.5", shownVerse.toV11n(net.bible.android.database.bookmarks.KJVA).getOsisID())
        post("saveState", "[\"split notes\"]", generation)
        onMain {}
        assertEquals("split notes", window.pageManager.jsState)
    }

    /** Independent v5 review: every real MyDocument scroll key round-trips, including the entry added by paging. */
    @Test
    fun reviewV5GeneralBookPagingReportsAndRestartRemainUsable() {
        val book = myDocument("MyDoc_3975_review", "page_a", "page_b", "page_c")
        val keys = book.getGlobalKeyList().toList()
        keys.forEach { assertEquals(it.getOsisRef(), book.getKey(it.getOsisRef()).getOsisRef()) }
        val generation = showEntry(book, keys[0])
        post("requestMoreToEnd", "[81]", generation)
        waitUntil("entry b delivered") { notNull(81) }
        post("scrolledToOrdinal", "[\"${keys[1].getOsisRef()}\",2]", generation)
        onMain {}
        assertEquals(keys[1].getOsisRef(), window.pageManager.currentPage.key?.getOsisRef())
        post("saveState", "[\"entry b\"]", generation)
        onMain {}
        assertEquals("entry b", window.pageManager.jsState)
        val before = view.lastRequest
        onMain { loader.setClientReady() }
        waitUntil("restart reloads entry b") { view.lastRequest != before }
        view.awaitLastRequestEnded()
        waitUntil("entry b admitted after restart") { keys[1].getOsisRef() in (admitted()?.loaded ?: emptySet()) }
        val restarted = session.documentGeneration
        post("requestMoreToEnd", "[82]", restarted)
        waitUntil("entry c delivered after restart") { notNull(82) }
        assertEquals(keys[2].getOsisRef(), session.lastKey?.getOsisRef())
    }

    /** Independent v5 review: equal metadata does not make a reinstalled module's key list the old one's. */
    @Test
    fun reviewV5ReinstalledGeneralBookDoesNotReuseTheOldInstancesKeyList() {
        val initials = "MyDoc_3975_reinstalled"
        val old = myDocument(initials, "page_a", "page_b")
        val page = window.pageManager.currentGeneralBook
        onMain { page.onlySetCurrentDocument(old) }
        val oldKeys = page.cachedGlobalKeyList!!
        onMain { MyDocumentBookManager.unregisterDocument(initials) }
        val document = runBlocking { dao.documentByInitials(initials)!! }
        runBlocking {
            dao.insertPageWithContent(MyDocumentPage(documentId = document.id, title = "Title page_c", pageKey = "page_c",
                contentType = MyDocumentContentType.MARKDOWN, orderNumber = 2), "content of page_c")
        }
        val replacement = onMain { MyDocumentBookManager.registerDocument(document)!! }
        assertFalse("replacement is a new instance", old === replacement)
        assertEquals("JSword equality compares metadata, not module instances", old, replacement)
        onMain { page.setCurrentDocument(replacement) }
        val actualNext = page.getKeyPlus(oldKeys[1], 1, replacement)
        assertEquals("the replacement contains a third entry", "page_c", actualNext.getOsisRef())
    }

    /** Independent v5 review: backoff is bounded and closing the session cancels its timer, not the host scope. */
    @Test
    fun reviewV5WaitingPagingBacksOffAndItsTimerDiesWithTheSession() {
        val generation = show("KJV", verse(BibleBook.JOHN, 6, 1))
        onMain { window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011")) }
        post("requestMoreToEnd", "[83]", generation)
        val owned = session.scope.coroutineContext[kotlinx.coroutines.Job]!!
        Thread.sleep(2_100)
        onMain {}
        assertTrue("still waiting, not a premature end of paging", response(83).isEmpty())
        assertEquals("one timer, not one per poll", 1, owned.children.count())
        onMain { session.dispose() }
        waitUntil("session timer cancelled") { owned.children.none() }
        assertTrue("host lifetime remains active", sessionScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }

    @Test
    fun rework6EpubPagingScrollRoundTripsTheAddedEntry() {
        val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        fixture.setUp()
        val state = fixture.openState(fixture.buildEpub("admission-3975"), testCoreStrings(),
            net.bible.android.platform.AndroidAppEnvironment(org.robolectric.RuntimeEnvironment.getApplication()))
        val metadata = state.getBookMetaData()
        val epub = org.crosswire.jsword.book.sword.SwordGenBook(metadata, net.bible.service.sword.epub.EpubBackend(state, metadata))
        Books.installed().addBook(epub)
        try {
            val keys = epub.getGlobalKeyList().toList()
            assertTrue("fixture has at least two entries", keys.size >= 2)
            val generation = showEntry(epub, keys[0])
            post("requestMoreToEnd", "[87]", generation)
            waitUntil("EPUB next entry delivered") { notNull(87) }
            post("scrolledToOrdinal", "[\"${keys[1].getOsisRef()}\",2]", generation)
            onMain {}
            assertEquals(keys[1].getOsisRef(), window.pageManager.currentPage.key?.getOsisRef())
            assertEquals(keys[1].getOsisRef(), epub.getKey(keys[1].getOsisRef()).getOsisRef())
        } finally {
            Books.installed().removeBook(epub)
            state.close()
            fixture.tearDown()
        }
    }

    @Test
    fun rework6LutherModuleUsesItsOwnMalachiChapterForForeignWindowVerse() {
        val dir = java.nio.file.Files.createTempDirectory("luther-admission").toFile()
        val initials = "Luther3975"
        val data = java.io.File(dir, "module").apply { mkdirs() }
        val luther = Versifications.instance().getVersification("Luther")!!
        val mapped = Verse(luther, BibleBook.MAL, 3, 19)
        val text = "<verse>Fixture verse</verse>".toByteArray(Charsets.UTF_8)
        java.io.File(data, "ot").writeBytes(text)
        java.io.RandomAccessFile(java.io.File(data, "ot.vss"), "rw").use { index ->
            index.setLength((luther.getTestamentOrdinal(mapped.ordinal) + 1L) * 6)
            index.seek(luther.getTestamentOrdinal(mapped.ordinal).toLong() * 6)
            index.write(byteArrayOf(0, 0, 0, 0, text.size.toByte(), 0))
        }
        val confFile = java.io.File(dir, "fixture.conf").apply {
            writeText("[$initials]\nDataPath=./module/\nModDrv=RawText\nEncoding=UTF-8\nSourceType=OSIS\nLang=en\nVersification=Luther\n")
        }
        val metadata = org.crosswire.jsword.book.sword.SwordBookMetaDataJvm.create(
            confFile, org.crosswire.common.util.NetUtilJvm.getURI(dir))
        metadata.setDriver(org.crosswire.jsword.book.sword.SwordBookDriver.instance())
        val module = checkNotNull(metadata.bookType).createBook(metadata)
        Books.installed().addBook(module)
        val logs = Collections.synchronizedList(mutableListOf<String>())
        val originalSink = net.bible.sharedcore.log.Log.sinkOverride
        net.bible.sharedcore.log.Log.sinkOverride = { _, tag, message, failure ->
            logs.add("$tag: $message ${failure?.stackTraceToString() ?: ""}")
        }
        try {
            val stored = Verse(kjv, BibleBook.MAL, 4, 1)
            onMain {
                window.pageManager.currentBible.onlySetCurrentDocument(module)
                window.pageManager.currentBibleVerse.setVerseSelected(kjv, stored)
                window.loadText()
            }
            view.awaitLastRequestEnded()
            try {
                waitUntil("Luther Malachi loaded", 5_000) { session.firstDocument is BibleDocument }
            } catch (e: IllegalStateException) {
                throw AssertionError("Luther fixture failed: ${logs.joinToString("\n")}", e)
            }
            val initial = session.initialKey as Verse
            assertEquals("Luther", initial.versification.name)
            assertEquals(BibleBook.MAL, initial.book)
            assertEquals(3, initial.chapter)
            assertTrue("Luther chapter is admitted", "Mal.3" in admitted()!!.contexts)
        } finally {
            net.bible.sharedcore.log.Log.sinkOverride = originalSink
            Books.installed().removeBook(module)
            dir.deleteRecursively()
        }
    }

    /** Virtual time: the recheck wait is replaced by a controllable one, so no test waits for real backoff delays. */
    private val recheckSleeps = Collections.synchronizedList(mutableListOf<kotlin.time.Duration>())
    private val recheckTicks = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    private fun installVirtualRecheck() {
        recheckSleeps.clear()
        session.admission.recheckSleep = { d -> recheckSleeps.add(d); recheckTicks.receive() }
    }

    /** Completes the current recheck wait and waits until the next one has started. */
    private fun finishRecheckWait() {
        val before = recheckSleeps.size
        recheckTicks.trySend(Unit)
        waitUntil("next recheck wait starts", 3_000) { recheckSleeps.size > before }
    }

    @Test
    fun rework6WaitingPagingLogsOnceAtMaximumBackoff() {
        val generation = show("KJV", verse(BibleBook.JOHN, 6, 1))
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val originalSink = net.bible.sharedcore.log.Log.sinkOverride
        net.bible.sharedcore.log.Log.sinkOverride = { _, tag, message, _ ->
            if (tag == "DocumentAdmission") messages.add(message)
        }
        try {
            installVirtualRecheck()
            onMain { window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011")) }
            post("requestMoreToEnd", "[86]", generation)
            waitUntil("first recheck wait starts", 3_000) { recheckSleeps.isNotEmpty() }
            repeat(4) { finishRecheckWait() }
            assertTrue("no warning before the interval reaches its maximum", messages.isEmpty())
            finishRecheckWait()
            assertEquals("stall is logged once the interval reaches 5 s", 1, messages.size)
            repeat(3) { finishRecheckWait() }
            assertEquals("one warning per continuous stall", 1, messages.size)
            assertEquals(listOf(250L, 500L, 1000L, 2000L, 4000L, 5000L, 5000L, 5000L, 5000L),
                recheckSleeps.map { it.inWholeMilliseconds })
            assertTrue("warning identifies the stalled paging request", messages.single().contains("paging", ignoreCase = true))
            assertTrue("the request remains waiting", response(86).isEmpty())
        } finally {
            net.bible.sharedcore.log.Log.sinkOverride = originalSink
        }
    }

    @Test
    fun reviewV6NewPagingStallRestartsBackoffAfterPreviousWaitIsDropped() {
        val generation = show("KJV", verse(BibleBook.JOHN, 6, 1))
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val originalSink = net.bible.sharedcore.log.Log.sinkOverride
        net.bible.sharedcore.log.Log.sinkOverride = { _, tag, message, _ ->
            if (tag == "DocumentAdmission" && message.contains("paging", ignoreCase = true)) messages.add(message)
        }
        try {
            installVirtualRecheck()
            onMain { window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011")) }
            post("requestMoreToEnd", "[88]", generation)
            waitUntil("first recheck wait starts", 3_000) { recheckSleeps.isNotEmpty() }
            repeat(5) { finishRecheckWait() }
            assertEquals("first stall reaches maximum backoff", 1, messages.size)
            recheckSleeps.clear()
            onMain {
                window.pageManager.currentBible.onlySetCurrentDocument(book("KJV"))
                session.admission.admitCurrentForTest(generation + 1)
                window.pageManager.currentBible.onlySetCurrentDocument(book("ESV2011"))
                loader.runForPageDocument(generation + 1, {}, {})
            }
            waitUntil("second stall starts its backoff again", 3_000) { recheckSleeps.isNotEmpty() }
            assertEquals("the new stall starts at the initial interval", 250L, recheckSleeps.first().inWholeMilliseconds)
            repeat(4) { finishRecheckWait() }
            assertEquals("not yet at the maximum", 1, messages.size)
            finishRecheckWait()
            assertEquals("each separate stall is logged once", 2, messages.size)
        } finally {
            net.bible.sharedcore.log.Log.sinkOverride = originalSink
        }
    }

    /** Review of rework 5 (M1): a general book entry whose read fails is not kept as a context of the page. */
    @Test
    fun aFailedEntryReadIsNotKeptAsAContext() {
        val book = myDocument("MyDoc_3975c", "page_a", "page_b")
        val keys = book.getGlobalKeyList().toList()
        val a = showEntry(book, keys[0])
        loader.entryReadFailureForTest = { true }
        try {
            post("requestMoreToEnd", "[68]", a)
            waitUntil("68 answered") { response(68).isNotEmpty() }
            onMain {}
            assertEquals(listOf(nullResponse(68)), response(68))
            assertFalse("page_b not a context: ${stateText()}", keys[1].getOsisRef() in admitted()!!.contexts)
            assertEquals("the cursor did not move", keys[0].getOsisRef(), session.lastKey?.getOsisRef())
        } finally {
            loader.entryReadFailureForTest = null
        }
    }
}
