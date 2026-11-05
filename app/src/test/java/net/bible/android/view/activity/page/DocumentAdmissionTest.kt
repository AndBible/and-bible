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
import net.bible.test.testWindowRepository
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
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
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

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        repo = testWindowRepository()
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
}
