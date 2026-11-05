package net.bible.android.view.activity.page

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [net.bible.sharedui.reading.bridge.ReadingBridge] lifecycle and navigation family, driven through
 * a real [BibleView] and the adapter ([BibleJavascriptInterface]) as the JS bridge uses it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingBridgeNavigationFamilyTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private val views = mutableListOf<BibleView>()
    private val activities = mutableListOf<org.robolectric.android.controller.ActivityController<android.app.Activity>>()
    private val kjv get() = Books.installed().getBook("KJV")!!

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        repo.activeWindow.pageManager.currentBible.setCurrentDocumentAndKey(kjv, Verse(KJVA, BibleBook.JOHN, 3, 1))
    }

    @After fun tearDown() {
        try {
            views.forEach { runCatching { it.doDestroy() } }
            activities.forEach { runCatching { it.pause().stop().destroy() } }
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    private fun newView(attach: Boolean = false): BibleView {
        val host = mock<ReadingHostActivity>()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        whenever(host.lifecycle).thenReturn(LifecycleRegistry(host).apply { currentState = Lifecycle.State.RESUMED })
        val callbacks = BibleViewHostCallbacks(
            hostActivity = mock<ActivityBase>(), onNext = {}, onPrevious = {}, showLlmPromptSelector = { _, _ -> },
            composeSearchIfHosted = { _, _ -> false }, composeOpenDrawerIfHosted = { false },
            openDrawerAndFocusIt = {}, composeReadingViewHost = { null }, showRegenerate = { _, _ -> },
            crashAllBibleViews = {}, currentNightMode = { false }, imeHeight = { 0 },
            topOffset2 = { 0 }, bottomOffsetForWebView = { 0 }, insetsChanges = { error("Not used") })
        val view = BibleView(host, callbacks, WeakReference(repo.activeWindow), windowControl,
            GlobalContext.get().get(), PageTiltScrollControl(),
            GlobalContext.get().get(), GlobalContext.get().get(),
            GlobalContext.get().get(), GlobalContext.get().get())
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
            isAccessible = true
            set(view, PageTiltScroller(view, PageTiltScrollControl()))
        }
        views += view
        view.setBibleJavascriptInterface(BibleJavascriptInterface(view))
        if (attach) {
            val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).also { activities += it }
            val activity = controller.setup().get()
            activity.setContentView(view)
        }
        return view
    }

    private fun idleUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private val currentChapter get() = repo.activeWindow.pageManager.currentBible.singleKey.chapter

    @Test fun goToNextChapterMovesTheWindowsVerse() {
        val bridge = BibleJavascriptInterface(newView())
        assertEquals(3, currentChapter)
        bridge.goToNextChapter()
        idleUntil { currentChapter != 3 }
        assertEquals(4, currentChapter)
    }

    /** Admits [document] to [view]'s session as the document of [generation] (what DocumentLoader does). */
    private fun admit(view: BibleView, document: net.bible.android.control.page.Document?, generation: Long) {
        view.firstDocument = document
        view.session.admission.admitCurrentForTest(generation)
    }

    /** A scroll report as the page posts it: through the dispatcher, with the page's document generation. */
    private fun report(view: BibleView, keyStr: String, ordinal: Int, generation: Long) =
        net.bible.sharedcore.bibleview.BridgeDispatcher(BibleJavascriptInterface(view), {}, {}).dispatch(
            """{"method":"scrolledToOrdinal","args":[${org.json.JSONObject.quote(keyStr)},$ordinal],"documentGeneration":$generation}""")

    private fun bibleDocument(key: Verse): net.bible.android.control.page.BibleDocument {
        val book = kjv as org.crosswire.jsword.book.sword.SwordBook
        return net.bible.android.control.page.BibleDocument(
            emptyList(), org.crosswire.jsword.passage.VerseRange(key.versification, key),
            net.bible.android.misc.OsisFragment(
                net.bible.service.sword.SwordContentFacade.readOsisFragment(book, key, strings = net.bible.test.testCoreStrings()), key, book),
            book, key)
    }

    @Test fun scrolledToOrdinalUpdatesCurrentVerse() {
        val view = newView()
        val bible = repo.activeWindow.pageManager.currentBible
        val key = Verse(KJVA, BibleBook.GEN, 1, 1)
        admit(view, bibleDocument(key), 7)
        view.initialKey = key
        view.session.minChapter = 1
        view.session.maxChapter = 1
        report(view, "Gen.1", 5, 7)
        assertEquals(Verse(KJVA, 5).ordinal, bible.singleKey.ordinal)
    }

    /**
     * osTicket 3508 / #3975: the page reports its position asynchronously, so a report can describe the page's
     * previous document (or, after a workspace switch handed the web view over, the outgoing window's page). Only a
     * report carrying the generation of the admitted document moves the window, even when the stale verse lies in the
     * very chapter the new document shows (outgoing Genesis 2:20, incoming Genesis 2:5); a chapter infinite scroll added
     * to the admitted document keeps its generation and still counts.
     */
    @Test fun aBibleReportOfAnotherDocumentGenerationIsIgnoredEvenInTheSameChapter() {
        val view = newView()
        val bible = repo.activeWindow.pageManager.currentBible
        val incoming = Verse(KJVA, BibleBook.GEN, 2, 5)
        bible.setCurrentDocumentAndKey(kjv, incoming)
        admit(view, bibleDocument(incoming), 2)
        view.initialKey = incoming
        view.session.minChapter = 2
        view.session.maxChapter = 2
        val bridge = BibleJavascriptInterface(view)

        report(view, "Gen.2", Verse(KJVA, BibleBook.GEN, 2, 20).ordinal, 1)
        assertEquals("the outgoing page's Genesis 2:20 must not move the window", incoming.ordinal, bible.singleKey.ordinal)
        report(view, "Gen.2", Verse(KJVA, BibleBook.GEN, 2, 20).ordinal, 3)
        assertEquals("a generation native never admitted", incoming.ordinal, bible.singleKey.ordinal)

        report(view, "Gen.2", Verse(KJVA, BibleBook.GEN, 2, 7).ordinal, 2)
        assertEquals(Verse(KJVA, BibleBook.GEN, 2, 7).ordinal, bible.singleKey.ordinal)
        // Infinite scroll added chapter 3 to the same document.
        view.session.maxChapter = 3
        report(view, "Gen.3", Verse(KJVA, BibleBook.GEN, 3, 2).ordinal, 2)
        assertEquals(Verse(KJVA, BibleBook.GEN, 3, 2).ordinal, bible.singleKey.ordinal)
    }

    /** Nothing admitted (a parked view after forgetDocument): even a report echoing the initial generation is ignored. */
    @Test fun aReportWithoutAnAdmittedDocumentIsIgnored() {
        val view = newView()
        val bible = repo.activeWindow.pageManager.currentBible
        val before = bible.singleKey.ordinal
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION)
        view.initialKey = Verse(KJVA, BibleBook.GEN, 1, 1)
        report(view, "Gen.1", Verse(KJVA, BibleBook.GEN, 1, 5).ordinal, net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION)
        assertEquals(before, bible.singleKey.ordinal)
    }

    /** Any bridge call as the page posts it, with the page's document generation. */
    private fun call(view: BibleView, method: String, argsJson: String, generation: Long) =
        net.bible.sharedcore.bibleview.BridgeDispatcher(BibleJavascriptInterface(view), {}, {}).dispatch(
            """{"method":"$method","args":$argsJson,"documentGeneration":$generation}""")

    /**
     * #3975 audit: besides scroll reports, a replaced page's calls that act on the window's current state implicitly
     * (its restore state, chapter navigation, workspace settings) are dropped; the admitted page's are not.
     */
    @Test fun staleWindowStateCallsAreIgnored() {
        val view = newView()
        val window = repo.activeWindow
        // The window shows John 3 (setUp); the document's text is irrelevant here (the test module has Genesis only).
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), 2)
        view.initialKey = Verse(KJVA, BibleBook.GEN, 1, 1)
        window.pageManager.jsState = "incoming"

        call(view, "saveState", "[\"outgoing\"]", 1)
        assertEquals("a stale page state must not be stored on the window", "incoming", window.pageManager.jsState)
        call(view, "toggleCompareDocument", "[\"NASB\"]", 1)
        assertFalse("nor change the workspace's settings", "NASB" in repo.workspaceSettings.hideCompareDocuments)
        call(view, "goToNextChapter", "[]", 1)
        idleUntil(timeoutMs = 500) { currentChapter != 3 }
        assertEquals("nor move the window a chapter", 3, currentChapter)

        call(view, "saveState", "[\"current\"]", 2)
        assertEquals("current", window.pageManager.jsState)
        call(view, "goToNextChapter", "[]", 2)
        idleUntil { currentChapter != 3 }
        assertEquals(4, currentChapter)
    }

    /** #3975: a view parked for reuse (Android `retire`) admits no document, so its old page cannot act on the window. */
    @Test fun aParkedViewsPageCannotMoveItsOldWindow() {
        val view = newView()
        val bible = repo.activeWindow.pageManager.currentBible
        val key = Verse(KJVA, BibleBook.GEN, 1, 1)
        bible.setCurrentDocumentAndKey(kjv, key)
        admit(view, bibleDocument(key), 2)
        view.initialKey = key
        val before = bible.singleKey.ordinal
        view.retire()
        report(view, "Gen.1", Verse(KJVA, BibleBook.GEN, 1, 9).ordinal, 2)
        assertEquals(before, bible.singleKey.ordinal)
    }

    /** osTicket 3508: the MyNotes document (KJVA ordinals) is guarded by its generation too. */
    @Test fun aMyNotesReportOfAnotherDocumentGenerationIsIgnored() {
        val view = newView()
        val bible = repo.activeWindow.pageManager.currentBible
        val key = Verse(KJVA, BibleBook.GEN, 2, 1)
        admit(view, net.bible.android.control.page.MyNotesDocument(
            emptyList(), org.crosswire.jsword.passage.VerseRange(KJVA, key, Verse(KJVA, BibleBook.GEN, 2, 25))), 4)
        view.initialKey = key
        view.session.minChapter = 2
        view.session.maxChapter = 2
        val bridge = BibleJavascriptInterface(view)

        report(view, "", Verse(KJVA, BibleBook.GEN, 2, 7).ordinal, 4)
        assertEquals(Verse(KJVA, BibleBook.GEN, 2, 7).ordinal, bible.singleKey.ordinal)
        report(view, "", Verse(KJVA, BibleBook.GEN, 2, 9).ordinal, 3)
        assertEquals("a stale MyNotes report", Verse(KJVA, BibleBook.GEN, 2, 7).ordinal, bible.singleKey.ordinal)
    }

    /**
     * #3975: a commentary window is moved by the osisRef key its page reports (and synced windows follow through
     * PassageChangeMediator). A stale page's key must neither move it nor overwrite its restore anchor.
     */
    @Test fun aStaleCommentaryReportMovesNeitherTheKeyNorTheAnchor() {
        val window = repo.activeWindow
        val page = window.pageManager.currentCommentary
        val key = Verse(KJVA, BibleBook.GEN, 1, 1)
        page.onlySetCurrentDocument(kjv)
        page.doSetKey(key)
        // The real commentary page logic over KJV data, selected without a module-category switch.
        net.bible.android.control.page.CurrentPageManager::class.java.getDeclaredField("currentPage").apply {
            isAccessible = true
            set(window.pageManager, page)
        }
        page.anchorOrdinal = net.bible.android.control.page.OrdinalRange(3)
        val view = newView()
        admit(view, net.bible.android.control.page.OsisDocument(net.bible.android.misc.OsisFragment(
            net.bible.service.sword.SwordContentFacade.readOsisFragment(kjv, key, strings = net.bible.test.testCoreStrings()), key, kjv), kjv, key), 5)
        val changes = mutableListOf<net.bible.android.control.PageChange>()
        val subscription = net.bible.android.control.PassageChangeMediator.changes.subscribe {
            if (it is net.bible.android.control.PageChange.VerseChanged) changes.add(it)
        }
        try {
            report(view, Verse(KJVA, BibleBook.EXOD, 4, 2).getOsisRef(), 9, 4)
            assertEquals("a stale key must not move the commentary", key.getOsisRef(), page.key.getOsisRef())
            assertTrue("nor notify synced windows: $changes", changes.isEmpty())
            assertEquals("nor overwrite the restore anchor", 3, page.anchorOrdinal?.start)

            report(view, Verse(KJVA, BibleBook.GEN, 1, 2).getOsisRef(), 9, 5)
            assertEquals(Verse(KJVA, BibleBook.GEN, 1, 2).getOsisRef(), page.key.getOsisRef())
            assertEquals(9, page.anchorOrdinal?.start)
        } finally {
            subscription.cancel()
        }
    }

    /** #3975: a stale report must not overwrite a general book's (multi-reference) restore anchor. */
    @Test fun aStaleGeneralBookReportDoesNotOverwriteTheAnchor() {
        val manager = repo.activeWindow.pageManager
        val factory = GlobalContext.get().get<net.bible.service.download.FakeBookFactory>()
        val refs = net.bible.service.sword.bookAndKeyListOf(listOf(
            Verse(KJVA, BibleBook.JOHN, 6, 35), Verse(KJVA, BibleBook.JOHN, 11, 26),
        ).map { net.bible.service.sword.BookAndKey(it, kjv) })
        manager.setCurrentDocumentAndKey(factory.multiDocument, refs)
        manager.currentPage.anchorOrdinal = net.bible.android.control.page.OrdinalRange(2)
        val view = newView()
        admit(view, kotlinx.coroutines.runBlocking { manager.currentPage.currentPageContent() }, 8)
        val bridge = BibleJavascriptInterface(view)

        report(view, "", 40, 7)
        assertEquals("a stale report", 2, manager.currentPage.anchorOrdinal?.start)
        report(view, "", 4, 8)
        assertEquals(4, manager.currentPage.anchorOrdinal?.start)
    }

    /** #3975: a stale report must not overwrite a StudyPad's restore anchor. */
    @Test fun aStaleStudyPadReportDoesNotOverwriteTheAnchor() {
        val manager = repo.activeWindow.pageManager
        val journal = GlobalContext.get().get<net.bible.service.download.FakeBookFactory>().journalDocument
        val label = net.bible.android.database.bookmarks.BookmarkEntities.Label(name = "3975 pad")
        kotlinx.coroutines.runBlocking {
            net.bible.service.db.DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(label)
        }
        manager.setCurrentDocumentAndKey(journal, net.bible.service.sword.StudyPadKey(label))
        manager.currentPage.anchorOrdinal = net.bible.android.control.page.OrdinalRange(2)
        val view = newView()
        val document = kotlinx.coroutines.runBlocking { manager.currentPage.currentPageContent() }
        assertTrue("fixture: a StudyPad document, was $document", document is net.bible.android.control.page.StudyPadDocument)
        admit(view, document, 3)
        val bridge = BibleJavascriptInterface(view)

        report(view, "", 40, 2)
        assertEquals("a stale report", 2, manager.currentPage.anchorOrdinal?.start)
        report(view, "", 6, 3)
        assertEquals(6, manager.currentPage.anchorOrdinal?.start)
    }

    @Test fun multiReferenceScrollSurvivesWorkspaceReload() = kotlinx.coroutines.runBlocking {
        val manager = repo.activeWindow.pageManager
        val factory = GlobalContext.get().get<net.bible.service.download.FakeBookFactory>()
        val refs = net.bible.service.sword.bookAndKeyListOf(listOf(
            Verse(KJVA, BibleBook.JOHN, 6, 35), Verse(KJVA, BibleBook.JOHN, 6, 58),
            Verse(KJVA, BibleBook.JOHN, 11, 26)
        ).map { net.bible.service.sword.BookAndKey(it, kjv) })
        manager.setCurrentDocumentAndKey(factory.multiDocument, refs)
        val view = newView()
        admit(view, manager.currentPage.currentPageContent(), 1)
        val windowId = repo.activeWindow.id
        val bridge = BibleJavascriptInterface(view)
        // The third fragment's verse has a document-local scroll ordinal, not a Bible ordinal.
        report(view, "", 5, 1)
        assertEquals(5, manager.currentPage.anchorOrdinal?.start)
        repo.saveIntoDb(false)
        repo.persistence.awaitPersisted()
        repo.loadFromDb(repo.id)
        val restored = repo.getWindow(windowId)!!.pageManager.currentGeneralBook
        assertEquals(refs.getOsisRef(), restored.key?.getOsisRef())
        assertEquals(5, restored.anchorOrdinal?.start)
    }

    @Test fun multiReferenceRestoreReadsAnAlreadyPersistedAnchor() {
        val manager = repo.activeWindow.pageManager
        val factory = GlobalContext.get().get<net.bible.service.download.FakeBookFactory>()
        manager.currentGeneralBook.restoreFrom(net.bible.android.database.WorkspaceEntities.Page(
            factory.multiDocument.initials, "KJV:John.6.35||KJV:John.6.58||KJV:John.11.26", 5))
        assertEquals(5, manager.currentGeneralBook.anchorOrdinal?.start)
    }

    @Test fun parseRefAnswersOsisOrNull() {
        val view = newView(attach = true)
        val bridge = BibleJavascriptInterface(view)
        view.setClientReady()
        shadowOf(Looper.getMainLooper()).idle()
        bridge.parseRef(3, "Joh 3:16")
        shadowOf(Looper.getMainLooper()).idle()
        val ok = shadowOf(view).lastEvaluatedJavascript!!
        assertTrue(ok, ok.startsWith("bibleView.response(3, ") && ok.contains("John.3.16"))
        bridge.parseRef(4, "zzz")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("bibleView.response(4, null);", shadowOf(view).lastEvaluatedJavascript)
    }

    @Test fun callsAfterSessionDisposeAreIgnored() {
        val view = newView(attach = true)
        val bridge = BibleJavascriptInterface(view)
        view.setClientReady()
        shadowOf(Looper.getMainLooper()).idle()
        view.session.dispose()
        bridge.goToNextChapter()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(300)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, currentChapter)
        bridge.requestMoreToEnd(7)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("bibleView.response(7, null);", shadowOf(view).lastEvaluatedJavascript)
    }

    @Test fun onKeyDownAltArrowsFocusWindows() {
        val first = repo.activeWindow
        val second = repo.addNewWindow(first)
        repo.activeWindow = first
        val view = newView()
        val bridge = BibleJavascriptInterface(view)
        bridge.onKeyDown("AltArrowRight")
        idleUntil { repo.activeWindow !== first }
        assertNotSame(first, repo.activeWindow)
        val moved = repo.activeWindow
        bridge.onKeyDown("AltArrowLeft")
        idleUntil { repo.activeWindow !== moved }
        assertNotSame(moved, repo.activeWindow)
        assertTrue(second.id == moved.id)
    }

    /** Records what the session asks the reading host to show. */
    private class RecordingSheets : net.bible.sharedui.reading.session.ReadingSheets {
        val prompts = mutableListOf<net.bible.android.view.activity.page.Selection>()
        var confirmDelete: (() -> Unit)? = null
        override fun showReadHistory(bookId: String, chapter: Int) {}
        override fun showCompare(range: org.crosswire.jsword.passage.VerseRange) {}
        override fun showShare(input: net.bible.sharedcore.reading.ShareVersesInput) {}
        override fun showPromptSelector(selection: net.bible.android.view.activity.page.Selection,
                                        context: net.bible.service.llm.PromptContext,
                                        category: net.bible.android.control.page.DocumentCategory?) { prompts += selection }
        override fun showRegenerate(pageId: net.bible.android.database.IdType, windowId: net.bible.android.database.IdType) {}
        override fun showHelp(title: String?, html: String) {}
        override fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit) { confirmDelete = onConfirm }
    }

    /**
     * #3975 review C2: the note editor's AI button reads the bookmark (a suspending database read) before it opens the
     * prompt selector with the ACTIVE window's document category. If the page's document is replaced during that read,
     * the selector must not open: checking only before the read let the old page's tap open it for the new document.
     */
    @Test fun theNoteEditorPromptSelectorIsNotShownForAPageReplacedDuringTheBookmarkRead() {
        val view = newView()
        val sheets = RecordingSheets().also { view.session.hostSheets = it }
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), 2)
        val context = """{"entityType":"BOOKMARK_NOTE","entityId":"${net.bible.android.database.IdType()}","currentText":"x","contentType":"HTML"}"""
        val main = shadowOf(Looper.getMainLooper())

        // Starts on the main thread and suspends in the bookmark read; the page is replaced while the read is in
        // flight. A read that completes inside the first main-thread task leaves no such window: try again.
        var raced = false
        for (attempt in 1..20) {
            main.idle()
            sheets.prompts.clear()
            call(view, "noteEditorLlmAction", "[${org.json.JSONObject.quote(context)}]", 2)
            main.runOneTask()
            if (sheets.prompts.isNotEmpty()) continue
            admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION)
            raced = true
            break
        }
        assertTrue("the bookmark read was never in flight after the first main-thread task", raced)
        idleUntil(timeoutMs = 1_500) { false }
        assertTrue("no prompt selector for a replaced page: ${sheets.prompts}", sheets.prompts.isEmpty())

        // Control: the same tap of the admitted page opens it.
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), 3)
        call(view, "noteEditorLlmAction", "[${org.json.JSONObject.quote(context)}]", 3)
        idleUntil { sheets.prompts.isNotEmpty() }
        assertEquals(1, sheets.prompts.size)
    }

    /**
     * #3975 review C3: after a confirmed AI page deletion the asking window is closed or switched to its Bible only while
     * it still shows the deleted page. A window that meanwhile shows another document keeps it.
     */
    @Test fun aDeleteFollowUpLeavesAWindowThatNowShowsAnotherDocument() {
        val view = newView()
        val sheets = RecordingSheets().also { view.session.hostSheets = it }
        val window = repo.activeWindow
        val dictionary = Books.installed().getBook("StrongsGreek")!!
        val main = shadowOf(Looper.getMainLooper())

        // Control: the window still shows the page when the deletion completes, so it leaves it (for its Bible).
        window.pageManager.setCurrentDocument(dictionary)
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), 2)
        call(view, "deleteMyDocumentPage", "[\"${net.bible.android.database.IdType()}\"]", 2)
        idleUntil { sheets.confirmDelete != null }
        sheets.confirmDelete!!.invoke()
        idleUntil { window.pageManager.currentPage.currentDocument != dictionary }
        assertEquals("the window left the deleted page", kjv, window.pageManager.currentPage.currentDocument)

        // The window shows another document by the time the deletion completes: it stays there.
        sheets.confirmDelete = null
        window.pageManager.setCurrentDocument(dictionary)
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), 3)
        call(view, "deleteMyDocumentPage", "[\"${net.bible.android.database.IdType()}\"]", 3)
        idleUntil { sheets.confirmDelete != null }
        sheets.confirmDelete!!.invoke()
        val other = Books.installed().getBook("StrongsHebrew")!!
        window.pageManager.setCurrentDocument(other)
        main.idle()
        idleUntil(timeoutMs = 1_500) { false }
        assertEquals("a window that shows another document keeps it", other, window.pageManager.currentPage.currentDocument)
    }

    /**
     * #3975 rework 4 (codex I2): the page the window showed when the user ASKED is the one whose deletion closes or
     * switches it. Navigating to another document while the dialog is open, then confirming, leaves the window there.
     */
    @Test fun aDeleteConfirmedAfterTheWindowMovedOnLeavesTheWindow() {
        val view = newView()
        val sheets = RecordingSheets().also { view.session.hostSheets = it }
        val window = repo.activeWindow
        val dictionary = Books.installed().getBook("StrongsGreek")!!
        window.pageManager.setCurrentDocument(dictionary)
        admit(view, bibleDocument(Verse(KJVA, BibleBook.GEN, 1, 1)), 2)
        call(view, "deleteMyDocumentPage", "[\"${net.bible.android.database.IdType()}\"]", 2)
        idleUntil { sheets.confirmDelete != null }
        val other = Books.installed().getBook("StrongsHebrew")!!
        window.pageManager.setCurrentDocument(other) // while the dialog is open
        sheets.confirmDelete!!.invoke()
        idleUntil(timeoutMs = 1_500) { false }
        assertEquals("the window moved on before the confirmation: it stays", other, window.pageManager.currentPage.currentDocument)
    }
}
