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
    }

    @After
    fun tearDown() {
        try {
            if (commentaryRegistered) {
                Books.installed().getBook(COMMENTARY)?.let { Books.installed().removeBook(it) }
                confDir?.deleteRecursively()
            }
            sessionScope.cancel()
            DatabaseResetter.resetDatabase(repo.scope)
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
