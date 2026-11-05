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

import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.Document
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.ConfigInputs
import net.bible.sharedui.reading.session.DocumentLoader
import net.bible.sharedui.reading.session.DocumentLoaderPlatform
import net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
import net.bible.test.DatabaseResetter
import net.bible.test.testCoreStrings
import net.bible.test.testWindowRepository
import org.crosswire.common.util.NetUtilJvm
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
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
 * [DocumentLoader] against real window/page objects and the provisioned KJV module, with only the JS
 * host faked. Covers the superseded-load rule and the paging answers JS waits on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentLoaderTest {
    private class FakeHost : JsHost {
        val evaluated: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            evaluated += js
            callback?.invoke("null")
        }
    }

    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var window: Window
    private lateinit var session: BibleWindowSession
    private lateinit var loader: DocumentLoader
    private val host = FakeHost()
    private val control = WindowControl()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var commentaryRegistered = false
    private var confDir: File? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = testWindowRepository()
        control.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        window = repo.activeWindow
        setUpReloadedSession()
    }

    private fun setUpReloadedSession(clientReady: Boolean = true) {
        val channel = BibleViewJsChannel(host) {}
        if (clientReady) channel.markClientReady()
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
    }

    @After
    fun tearDown() {
        try {
            if (commentaryRegistered) {
                Books.installed().getBook(COMMENTARY)?.let { Books.installed().removeBook(it) }
                confDir?.deleteRecursively()
            }
            sessionScope.cancel()
            DatabaseResetter.resetDatabase(repo.scope, sessionScope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
            Dispatchers.resetMain()
        }
    }

    private fun doc(id: String, entered: CountDownLatch? = null, release: CountDownLatch? = null): Document = object : Document {
        override suspend fun contentMap(): Map<String, Any> {
            entered?.countDown()
            check(release?.await(10, TimeUnit.SECONDS) != false)
            return mapOf("id" to "'$id'")
        }
    }

    private suspend fun awaitEvaluated(predicate: (List<String>) -> Boolean) = withContext(Dispatchers.IO) {
        withTimeout(10_000) { while (!predicate(synchronized(host.evaluated) { host.evaluated.toList() })) Thread.sleep(10) }
    }

    @Test
    fun reloadedMultiReferencePositionIsSentToTheClient() = runBlocking {
        val factory = org.koin.mp.KoinPlatform.getKoin().get<net.bible.service.download.FakeBookFactory>()
        val kjv = Books.installed().getBook("KJV")!!
        val refs = net.bible.service.sword.bookAndKeyListOf(listOf(
            Verse(KJVA, BibleBook.JOHN, 6, 35), Verse(KJVA, BibleBook.JOHN, 6, 58),
            Verse(KJVA, BibleBook.JOHN, 11, 26)
        ).map { net.bible.service.sword.BookAndKey(it, kjv) })
        window.pageManager.setCurrentDocumentAndKey(factory.multiDocument, refs)
        session.firstDocument = window.pageManager.currentPage.currentPageContent()
        session.admission.admitCurrentForTest(1)
        net.bible.sharedui.reading.bridge.ReadingBridge(session).scrolledToOrdinal("", 5)
        val windowId = window.id
        repo.saveIntoDb(false)
        repo.persistence.awaitPersisted()
        repo.loadFromDb(repo.id)
        window = repo.getWindow(windowId)!!
        session.dispose()
        // The new session owns the reloaded window, just as after process restart.
        setUpReloadedSession()
        val page = window.pageManager.currentPage
        loader.loadDocument(page.currentPageContent(), true, page.key, null, null, null)
        awaitEvaluated { calls -> calls.any { it.contains("setup_content") } }
        val setup = host.evaluated.first { it.contains("setup_content") }
        assertTrue(setup, setup.contains("\"jumpToAnchor\": 5"))
    }

    /**
     * osTicket 3508: the chapters of a newly admitted Bible document are exactly its own, also before the page has
     * received it (a booting page defers replaceDocument). Merging them with the previous document's range let a
     * stale scroll report of the previous page (same book, another chapter) pass ReadingBridge's loaded-chapter check.
     */
    @Test
    fun anAdmittedBibleDocumentReplacesTheLoadedChapterRange() = runBlocking {
        session.dispose()
        setUpReloadedSession(clientReady = false)
        session.minChapter = 3
        session.maxChapter = 5
        val key = Verse(window.pageManager.currentBible.currentBibleVerse.versificationOfLastSelectedVerse,
            org.crosswire.jsword.versification.BibleBook.GEN, 10, 1)
        val document = doc("Gen10")
        loader.loadDocument(document, false, key, null, null, null)
        withContext(Dispatchers.IO) { withTimeout(10_000) { while (session.firstDocument !== document) Thread.sleep(10) } }
        assertTrue("the admitted chapter", session.hasChapterLoaded(10))
        assertFalse("a chapter of the previous document", session.hasChapterLoaded(4))
    }

    /**
     * #3975: every admitted document gets its own generation; the page receives it with the document's
     * `clear_document` (also when a restarted page gets the document again) and echoes it in its scroll reports.
     * Handing the page to another window forgets it, so no report of the old page matches.
     */
    @Test
    fun theAdmittedDocumentsGenerationTravelsWithItsClearDocument() = runBlocking {
        loader.loadDocument(doc("A"), false, null, null, null, null)
        val a = session.documentGeneration
        assertTrue("an admitted generation", a != NO_DOCUMENT_GENERATION)
        assertEquals(loader.loadGeneration, a)
        assertTrue(host.evaluated.toString(), host.evaluated.any { it.contains("bibleView.emit(\"clear_document\", $a)") })
        host.evaluated.clear()

        loader.loadDocument(doc("B"), false, null, null, null, null)
        val b = session.documentGeneration
        assertTrue("a new document, a new generation", b > a)
        assertTrue(host.evaluated.toString(), host.evaluated.any { it.contains("bibleView.emit(\"clear_document\", $b)") })
        host.evaluated.clear()

        loader.setClientReady()
        assertTrue("a restarted page gets the same generation: ${host.evaluated}",
            host.evaluated.any { it.contains("bibleView.emit(\"clear_document\", $b)") })

        loader.forgetDocument()
        assertEquals(NO_DOCUMENT_GENERATION, session.documentGeneration)
    }

    /** #3975: the generation changes at admission, before a booting page has received the document, so a report of
     *  the page's previous document never matches the newly admitted one. */
    @Test
    fun theGenerationChangesWhenTheDocumentIsAdmitted() = runBlocking {
        session.dispose()
        setUpReloadedSession(clientReady = false)
        session.admission.admitCurrentForTest(99)
        loader.loadDocument(doc("A"), false, null, null, null, null)
        withContext(Dispatchers.IO) { withTimeout(10_000) { while (session.firstDocument == null) Thread.sleep(10) } }
        assertEquals(loader.loadGeneration, session.documentGeneration)
        assertFalse("nothing sent yet: ${host.evaluated}", host.evaluated.any { it.contains("clear_document") })
    }

    /**
     * #3975 review B (4): a requested document invalidates the outgoing one at once; a request that ends without a new
     * admission (unchanged content, superseded or failed read) admits the page's document again, an older request's end
     * never does while a newer one is pending, and an admission settles every request.
     */
    @Test
    fun aDocumentRequestSuspendsTheAdmittedGenerationUntilItEnds() = runBlocking {
        loader.loadDocument(doc("A"), false, null, null, null, null)
        val a = session.documentGeneration
        val shown = window.pageManager.currentBible.currentBibleVerse.verse

        // A request of another chapter (a different document) closes the page's generation.
        window.pageManager.currentBible.doSetKey(Verse(shown.versification, shown.book, shown.chapter + 1, 1))
        val first = loader.documentRequested()
        assertEquals(NO_DOCUMENT_GENERATION, session.documentGeneration)
        window.pageManager.currentBible.doSetKey(kjvVerse(BibleBook.JOHN, 9, 1))
        val second = loader.documentRequested()
        loader.documentRequestEnded(first)
        assertEquals("a newer request is still pending", NO_DOCUMENT_GENERATION, session.documentGeneration)
        window.pageManager.currentBible.doSetKey(shown)
        loader.documentRequestEnded(second)
        assertEquals("no document came: the page's document counts again", a, session.documentGeneration)

        window.pageManager.currentBible.doSetKey(Verse(shown.versification, shown.book, shown.chapter + 1, 1))
        val third = loader.documentRequested()
        loader.loadDocument(doc("B"), false, null, null, null, null)
        val b = session.documentGeneration
        assertTrue("admitted", b != NO_DOCUMENT_GENERATION && b != a)
        loader.documentRequestEnded(third)
        assertEquals(b, session.documentGeneration)

        loader.documentRequested()
        loader.forgetDocument()
        assertEquals(NO_DOCUMENT_GENERATION, session.documentGeneration)
    }

    /** A page that (re)starts while a newer document is requested still gets its document with that document's generation. */
    @Test
    fun aRestartedPageGetsItsDocumentsGenerationWhileANewerOneIsRequested() = runBlocking {
        loader.loadDocument(doc("A"), false, null, null, null, null)
        val a = session.documentGeneration
        loader.documentRequested()
        host.evaluated.clear()
        loader.setClientReady()
        assertTrue("restart: ${host.evaluated}", host.evaluated.any { it.contains("bibleView.emit(\"clear_document\", $a)") })
    }

    /**
     * The [WindowView] a real `Window.loadText` delivers to, forwarding to [loader] like `BibleView`/`HostWindowView`.
     * [failLoadOf] makes the delivery of a document throw before it is admitted (a failing content or label read).
     */
    private inner class LoaderView(private val failLoadOf: (Document) -> Boolean = { false }) : net.bible.android.control.page.window.WindowView {
        val requestsEnded = java.util.concurrent.LinkedBlockingQueue<Long>()
        override val verseRangeLoaded: VerseRange? = null
        override var lastUpdated: Long = 1L
        override val firstDocument get() = session.firstDocument
        override var listenEvents = true
        override fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) {}
        override fun highlightBibleOrdinalRange(range: IntRange) {}
        override fun destroy() {}
        override suspend fun loadDocument(document: Document, updateLocation: Boolean, key: org.crosswire.jsword.passage.Key?,
                                          anchorOrdinal: net.bible.android.control.page.OrdinalRange?, htmlId: String?, windowContentGeneration: Long?) {
            check(!failLoadOf(document)) { "content of $document unreadable" }
            loader.loadDocument(document, updateLocation, key, anchorOrdinal, htmlId, windowContentGeneration)
        }
        override fun scrollOrJumpToVerse(key: org.crosswire.jsword.passage.Key, forceNow: Boolean) {}
        override fun scrollOrJumpToOrdinal(ordinal: net.bible.android.control.page.OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean) {}
        override fun hasChapterLoaded(chapter: Int) = session.hasChapterLoaded(chapter)
        override fun requestFocus() = false
        override fun updateTextDisplaySettings(onAttach: Boolean) {}
        override fun adjustLoadingCount(adj: Int) = true
        @Volatile var lastRequest = 0L
        override fun documentRequested() = loader.documentRequested().also { lastRequest = it }
        override fun documentRequestEnded(request: Long, unchanged: Boolean, failure: Throwable?) {
            loader.documentRequestEnded(request, unchanged, failure)
            requestsEnded.add(request)
        }

        /** Waits until the newest request (a page change and an explicit loadText each make one) has ended. */
        fun awaitLastRequestEnded() {
            val deadline = System.currentTimeMillis() + 10_000
            while (lastRequest !in requestsEnded) {
                check(System.currentTimeMillis() < deadline) { "request $lastRequest never ended" }
                Thread.sleep(10)
            }
        }
    }

    /** What the page posts, through the production dispatcher and the session's [ReadingBridge] gate. */
    private fun dispatcher() = net.bible.sharedcore.bibleview.BridgeDispatcher(
        net.bible.sharedui.reading.bridge.ReadingBridge(session),
        { callId -> session.jsChannel.execute(BibleViewEvents.response(callId, null)) }, {},
    )

    private fun kjvVerse(book: BibleBook, chapter: Int, verse: Int) =
        Verse((Books.installed().getBook("KJV") as org.crosswire.jsword.book.sword.SwordBook).versification, book, chapter, verse)

    /** Shows [verse] of [initials] through a real `Window.loadText` and waits until its document is admitted. */
    private fun admitThroughLoadText(view: LoaderView, initials: String, verse: Verse): Long {
        val before = session.documentGeneration
        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook(initials), verse)
        window.loadText()
        runBlocking(Dispatchers.IO) {
            withTimeout(10_000) {
                while (session.documentGeneration == NO_DOCUMENT_GENERATION || session.documentGeneration == before) Thread.sleep(10)
            }
        }
        view.requestsEnded.clear()
        return session.documentGeneration
    }

    /**
     * #3975 review C4: a load of ANOTHER document that fails before admission must not admit the page's old document
     * again: the window now holds the new document (and key), and the old page's report would move it.
     */
    @Test
    fun aFailedLoadOfAnotherDocumentDoesNotAdmitThePagesDocumentAgain() {
        val view = LoaderView(failLoadOf = { (it as? net.bible.android.control.page.BibleDocument)?.swordBook?.initials == "ESV2011" })
        window.bibleView = view
        val john = kjvVerse(BibleBook.JOHN, 6, 1)
        val a = admitThroughLoadText(view, "KJV", john)

        val genesis = kjvVerse(BibleBook.GEN, 1, 1)
        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("ESV2011"), genesis)
        window.loadText()
        view.awaitLastRequestEnded()

        dispatcher().dispatch("""{"method":"scrolledToOrdinal","args":["John.6",${kjvVerse(BibleBook.JOHN, 6, 20).ordinal}],"documentGeneration":$a}""")
        assertEquals("the old page's report must not move the window off the requested document",
            genesis.ordinal, window.pageManager.currentBible.currentBibleVerse.verse.ordinal)
        assertTrue("an error document replaced the page", session.firstDocument is net.bible.android.control.page.ErrorDocument)
        assertTrue(session.documentGeneration != a)
    }

    /** A request for the SAME document (a reload of the shown chapter) that fails keeps the page's document admitted. */
    @Test
    fun aRequestOfTheSameDocumentThatEndsWithoutOneAdmitsThePagesDocumentAgain() {
        var fail = false
        val view = LoaderView(failLoadOf = { fail })
        window.bibleView = view
        val a = admitThroughLoadText(view, "KJV", kjvVerse(BibleBook.JOHN, 6, 1))

        fail = true
        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV"), kjvVerse(BibleBook.JOHN, 6, 30))
        window.loadText()
        view.awaitLastRequestEnded()
        assertEquals(a, session.documentGeneration)
    }

    /**
     * #3975 review A3: a chapter request of the page while a new document is requested is not dropped (a dropped one
     * answers null, which makes infinite scroll stop for good when no new document follows, e.g. an unchanged
     * commentary). It waits for the request: answered after it ends without a document, with null once a new document
     * replaces the page's.
     */
    @Test
    fun aChapterRequestDuringADocumentRequestWaitsForItsOutcome() = runBlocking {
        val view = LoaderView()
        window.bibleView = view
        val a = admitThroughLoadText(view, "KJV", kjvVerse(BibleBook.JOHN, 6, 1))
        host.evaluated.clear()

        // A request of another chapter (a different document); it ends without one, back on John 6.
        val john6 = kjvVerse(BibleBook.JOHN, 6, 1)
        window.pageManager.currentBible.doSetKey(kjvVerse(BibleBook.JOHN, 9, 1))
        val request = loader.documentRequested()
        dispatcher().dispatch("""{"method":"requestMoreToEnd","args":[51],"documentGeneration":$a}""")
        assertFalse("not answered while the request is pending: ${host.evaluated}",
            host.evaluated.any { it.startsWith("bibleView.response(51, ") })
        window.pageManager.currentBible.doSetKey(john6)
        loader.documentRequestEnded(request)
        awaitEvaluated { sent -> sent.any { it.startsWith("bibleView.response(51, ") } }
        val answer = host.evaluated.single { it.startsWith("bibleView.response(51, ") }
        assertFalse("the next chapter, not null: $answer", answer == BibleViewEvents.response(51, null).js + ";")

        // A request that ends in a new document: the waiting chapter request is answered with null (its page is cleared).
        window.pageManager.currentBible.doSetKey(kjvVerse(BibleBook.JOHN, 9, 1))
        val second = loader.documentRequested()
        dispatcher().dispatch("""{"method":"requestMoreToEnd","args":[52],"documentGeneration":$a}""")
        loader.loadDocument(doc("B"), false, null, null, null, null)
        loader.documentRequestEnded(second)
        assertTrue(host.evaluated.toString(), host.evaluated.contains(BibleViewEvents.response(52, null).js + ";"))

        // A chapter request of a replaced page is answered with null at once.
        dispatcher().dispatch("""{"method":"requestMoreToEnd","args":[53],"documentGeneration":$a}""")
        assertTrue(host.evaluated.toString(), host.evaluated.contains(BibleViewEvents.response(53, null).js + ";"))
    }

    @Test
    fun staleLoadIsDropped() = runBlocking {
        val aEntered = CountDownLatch(1)
        val aRelease = CountDownLatch(1)
        val a = launch(Dispatchers.IO) { loader.loadDocument(doc("A", aEntered, aRelease), false, null, null, null, null) }
        try {
            withContext(Dispatchers.IO) { assertTrue(aEntered.await(10, TimeUnit.SECONDS)) }
            loader.loadDocument(doc("B"), false, null, null, null, null)
            aRelease.countDown()
            a.join()
        } finally {
            aRelease.countDown()
            a.cancel()
        }
        assertEquals(2L, loader.loadGeneration)
        assertEquals("{'id': 'B'}", loader.latestDocumentStr)
        val sent = host.evaluated.toList()
        assertEquals("exactly one document replacement: $sent", 1, sent.count { it.contains("add_documents") })
        assertTrue(sent.any { it.contains("{'id': 'B'}") })
        assertFalse("the older load must never reach JS: $sent", sent.any { it.contains("'A'") })
    }

    /**
     * A page that starts again on the same session (iOS: WebKit killed the WebContent process while the app was hidden
     * and the page reloaded) reports client ready a second time. It is an empty page: it must get the labels and the
     * document again, or it stays in its loading state for good (the Mac "panes vanish after a virtual desktop
     * switch" bug).
     */
    @Test
    fun restartedPageGetsLabelsAndDocumentAgain() = runBlocking {
        // The fixture's channel is already ready: the load goes straight to the first page.
        loader.loadDocument(doc("A"), false, null, null, null, null)
        val firstDelivery = host.evaluated.toList()
        assertEquals("first page: $firstDelivery", 1, firstDelivery.count { it.contains("add_documents") })
        assertEquals("first page: $firstDelivery", 1, firstDelivery.count { it.contains("update_labels") })
        host.evaluated.clear()

        // The page reloads and its BibleView-JS mounts again.
        loader.setClientReady()

        val sent = host.evaluated.toList()
        assertEquals("restarted page gets labels again: $sent", 1, sent.count { it.contains("update_labels") })
        assertEquals("restarted page gets the document again: $sent", 1, sent.count { it.contains("add_documents") && it.contains("'A'") })
        assertTrue("and its setup_content: $sent", sent.any { it.contains("setup_content") })
    }

    @Test
    fun failingChapterLoadStillAnswersTheJsPromiseWithNull() = runBlocking {
        loader.launchAnswer(77) { throw IllegalStateException("module unreadable") }.join()

        assertEquals(listOf(BibleViewEvents.response(77, null).js + ";"), host.evaluated.toList())
    }

    @Test
    fun succeedingChapterLoadAnswersWithTheJson() = runBlocking {
        loader.launchAnswer(78) { "{'ok': 1}" }.join()

        assertEquals(listOf(BibleViewEvents.response(78, net.bible.sharedcore.bibleview.JsExpr("{'ok': 1}")).js + ";"), host.evaluated.toList())
    }

    @Test
    fun requestMoreToEndAtLastChapterAnswersNull() = runBlocking {
        val kjv = Books.installed().getBook("KJV")!!
        val v11n = (kjv as org.crosswire.jsword.book.sword.SwordBook).versification
        val rev22 = Verse(v11n, BibleBook.REV, 22, 1)
        window.pageManager.currentBible.setCurrentDocumentAndKey(kjv, rev22)
        session.firstDocument = window.pageManager.currentBible.getPageContent(VerseRange(v11n, rev22, rev22))
        assertTrue("the Bible branch is under test, got ${session.firstDocument}", session.firstDocument is net.bible.android.control.page.BibleDocument)
        session.minChapter = 22
        session.maxChapter = 22

        loader.requestMoreToEnd(41)

        assertEquals(listOf(BibleViewEvents.response(41, null).js + ";"), host.evaluated.toList())
        assertEquals(22, session.maxChapter)
    }

    @Test
    fun requestMoreToBeginningOfCommentaryUsesPrevBlockStart() = runBlocking {
        val book = registerCommentary()
        val page = window.pageManager.currentCommentary
        val first = Verse(KJVA, BibleBook.MATT, 5, 3)
        page.onlySetCurrentDocument(book)
        page.doSetKey(first)
        val expectedStart = checkNotNull(page.prevBlockStart(first)) { "walker finds a previous block" }
        assertTrue(expectedStart != first)
        session.firstDocument = page.getPageContent(first)
        session.initialKey = first

        loader.requestMoreToBeginning(42)
        awaitEvaluated { sent -> sent.any { it.startsWith("bibleView.response(42, ") } }

        assertEquals(expectedStart, session.firstKey)
        assertEquals(first, session.lastKey)
        val answer = host.evaluated.single { it.startsWith("bibleView.response(42, ") }
        assertFalse("a real page answers, not null: $answer", answer == BibleViewEvents.response(42, null).js)
    }

    /**
     * A commentary-category module over the provisioned KJV data (real blocks, one verse each). Its conf
     * lives in a temp directory, so the provisioned `~/.sword` stays exactly the exact test module set.
     */
    private fun registerCommentary(): org.crosswire.jsword.book.Book {
        val root = File(System.getProperty("user.home"), ".sword")
        val conf = File(root, "mods.d/kjv.conf").readText().replaceFirst("[KJV]", "[$COMMENTARY]") + "\nCategory=Commentaries\n"
        confDir = java.nio.file.Files.createTempDirectory("doc-loader-test").toFile()
        val confFile = File(confDir, "${COMMENTARY.lowercase()}.conf")
        confFile.writeText(conf)
        commentaryRegistered = true
        val bmd = SwordBookMetaDataJvm.create(confFile, NetUtilJvm.getURI(root))
        bmd.setDriver(SwordBookDriver.instance())
        Books.installed().addBook(checkNotNull(bmd.bookType).createBook(bmd))
        return requireNotNull(Books.installed().getBook(COMMENTARY)) { "$COMMENTARY must be installed" }
    }

    private companion object {
        const val COMMENTARY = "KJVCom"
    }
}
