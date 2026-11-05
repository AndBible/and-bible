package net.bible.android.control.page.window

import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.ChapterVerse
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.BibleView
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.StudyPadKey
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import net.bible.test.awaitHostUntil
import net.bible.test.awaitReadingGraph
import net.bible.test.closeReadingHostsBeforeReset
import net.bible.test.settleWorkspaceSwitches
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import android.os.Looper
import java.time.Duration
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A loader whose read stops until the test lets it go. */
private class HeldRead(real: WorkspaceLoader, persistence: WorkspacePersistence) :
    WorkspaceLoader(
        persistence, { workspaceDao },
        get<net.bible.android.control.bookmark.BookmarkControl>(net.bible.android.control.bookmark.BookmarkControl::class.java),
        "no-journal",
    ) {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    private val delegate = real
    override suspend fun read(workspaceId: IdType?, createIfMissing: Boolean): LoadedWorkspace {
        entered.complete(Unit)
        release.await()
        return delegate.read(workspaceId, createIfMissing)
    }
}

/**
 * SU-2: a workspace switch must keep the outgoing workspace's views alive for the whole IO read phase
 * and destroy them only in the same Main step that swaps the owners. Destroying them before the read
 * leaves blank panes on screen for as long as the read takes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceSwitchContentTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After fun tearDown() {
        CommonUtils.settings.setBoolean("disable_animations", false)
        closeReadingHostsBeforeReset(controllers)
    }

    @Test fun oldViewsSurviveTheHeldReadAndTheSwitchRestoresEveryWindow() = scenario(animationsOff = false)

    @Test fun oldViewsSurviveTheHeldReadWithAnimationsDisabled() = scenario(animationsOff = true)

    private fun cachedViews(factory: net.bible.android.view.activity.page.BibleViewFactory): Map<*, *> =
        factory.javaClass.getDeclaredField("windowBibleViewMap").apply { isAccessible = true }.get(factory) as Map<*, *>

    /** The live host with workspace A of three windows (KJV, NASB, a StudyPad), each with a cached view. */
    private class WorkspaceA(
        val activity: NavHostComposeActivity,
        val repo: WindowRepository,
        val factory: net.bible.android.view.activity.page.BibleViewFactory,
        val oldWindows: List<Window>,
        val label: BookmarkEntities.Label,
        val journal: net.bible.service.download.FakeBookFactory,
    )

    private fun workspaceA(): WorkspaceA {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply {
            create().start().resume().visible()
            awaitReadingGraph(get()) { compose.waitForIdle() }
        }
        val activity = controller.get()
        val repo = activity.hostWindowRepository
        val factory = activity.readingCommands.bibleViewFactory
        val books = Books.installed()
        val journal = get<net.bible.service.download.FakeBookFactory>(net.bible.service.download.FakeBookFactory::class.java)

        // Workspace 1: KJV 1:7, a second Bible (NASB 2:3; no commentary module is installed in the test set), a StudyPad.
        val w1 = repo.activeWindow
        w1.pageManager.setCurrentDocument(books.getBook("KJV"))
        w1.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 7)
        val w2 = repo.addNewWindow(w1)
        w2.pageManager.setCurrentDocument(books.getBook("NASB"))
        w2.pageManager.currentBible.currentChapterVerse = ChapterVerse(2, 3)
        val w3 = repo.addNewWindow(w2)
        val label = BookmarkEntities.Label(name = "SU-2 pad")
        runBlocking { DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(label) }
        w3.pageManager.setCurrentDocumentAndKey(journal.journalDocument, StudyPadKey(label))
        val oldWindows = listOf(w1, w2, w3)
        oldWindows.forEach { factory.getOrCreateBibleView(it) }
        assertEquals("fixture: three windows with a live view each", 3, oldWindows.count { it.bibleView != null })
        return WorkspaceA(activity, repo, factory, oldWindows, label, journal)
    }

    /** Inserts an empty workspace B (the loader gives it one default window) and persists A. */
    private fun newWorkspaceB(repo: WindowRepository): IdType {
        val bId = IdType()
        runBlocking { workspaceDao.insertWorkspace(WorkspaceEntities.Workspace("B").also { it.id = bId }) }
        repo.saveIntoDb(false)
        runBlocking { repo.persistence.awaitPersisted() }
        return bId
    }

    /**
     * #3950: a switch must not throw the outgoing web views away and boot new ones (a WebView load plus the page's
     * Vue start-up, about a second of blank content on the emulator). The incoming windows take the outgoing views
     * over, so each pane keeps its old page on screen until the new document replaces it; only the views no incoming
     * window takes are destroyed, once the new panes are composed.
     */
    @Test fun switchHandsTheOutgoingViewsToTheIncomingWindows() {
        val a = workspaceA()
        val oldViews = a.oldWindows.map { it.bibleView as BibleView }
        val bId = newWorkspaceB(a.repo)

        a.activity.lifecycleScope.launch { a.activity.readingCommands.switchWorkspace(bId) }
        settleWorkspaceSwitches()
        compose.waitForIdle()

        assertEquals(bId, a.repo.id)
        val incoming = a.repo.windowList.single()
        val view = a.factory.getOrCreateBibleView(incoming)
        assertTrue("workspace B's window must reuse an outgoing view, not boot a new WebView", oldViews.any { it === view })
        assertFalse("the reused view must stay alive", view.session.isDisposed)
        assertSame(incoming, view.window)
        assertSame(view, incoming.bibleView)
        a.oldWindows.forEach { assertNull("${it.displayId} must not keep a view it handed over", it.bibleView) }

        // The two views workspace B has no window for are destroyed once the new panes are composed.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        compose.waitForIdle()
        assertEquals("unused outgoing views must be destroyed", 2, oldViews.count { it.session.isDisposed })
    }

    /**
     * osTicket 3508 (regression of #3950): an adopted view still shows the outgoing window's page until the incoming
     * window's document replaces it, and that old page keeps reporting its scroll position (a forced re-measure on
     * `config_changed`, scroll events of the resize into the new pane). A report that reaches Android after the new
     * document has been admitted must not move the incoming window to the outgoing window's verse: that moved
     * window 1 of the new workspace to the Bible location of the workspace the user came from (and synced it).
     */
    @Test fun anAdoptedViewsOldPageCannotMoveTheIncomingWindow() {
        val a = workspaceA()
        val oldViews = a.oldWindows.map { it.bibleView as BibleView }
        val oldGenerations = oldViews.associateWith { it.session.documentGeneration }
        val bId = newWorkspaceB(a.repo)

        a.activity.lifecycleScope.launch { a.activity.readingCommands.switchWorkspace(bId) }
        settleWorkspaceSwitches()
        compose.waitForIdle()

        val incoming = a.repo.windowList.single()
        val view = a.factory.getOrCreateBibleView(incoming)
        assertTrue("fixture: workspace B's window adopts an outgoing view", oldViews.any { it === view })
        awaitHostUntil("workspace B's document must be admitted to the adopted view") {
            view.session.firstDocument is net.bible.android.control.page.BibleDocument
        }
        val before = incoming.pageManager.currentBible.currentBibleVerse.verse
        // Where the outgoing page was (as window 1 of the reported workspace): Luke 12:49.
        val oldVerse = org.crosswire.jsword.passage.Verse(before.versification, org.crosswire.jsword.versification.BibleBook.LUKE, 12, 49)
        assertFalse("fixture: B's window must not already be in Luke 12", before.book == oldVerse.book && before.chapter == 12)

        // The old page (Luke 12) reports its top verse after B's document was admitted, before JS cleared it. It
        // echoes the generation of the outgoing window's document.
        staleReport(view, "Luke.12", oldVerse.ordinal, oldGenerations.getValue(view))

        val after = incoming.pageManager.currentBible.currentBibleVerse.verse
        assertEquals("the outgoing page's verse must not move workspace B's window", before, after)
    }

    /**
     * #3975: the outgoing page's stale report must be ignored even when the incoming window shows the very chapter the
     * outgoing page was in (outgoing John 6:58, incoming John 6:5): a loaded-chapter check cannot tell those apart,
     * only the generation of the document the report was rendered from can.
     */
    @Test fun anAdoptedViewsOldPageCannotMoveTheIncomingWindowWithinTheSameChapter() {
        val a = workspaceA()
        val kjv = Books.installed().getBook("KJV")
        val outgoing = org.crosswire.jsword.passage.Verse(
            a.oldWindows[0].pageManager.currentBible.currentBibleVerse.verse.versification,
            org.crosswire.jsword.versification.BibleBook.JOHN, 6, 58)
        a.oldWindows.forEach { it.pageManager.setCurrentDocumentAndKey(kjv, outgoing) }
        val oldViews = a.oldWindows.map { it.bibleView as BibleView }
        awaitHostUntil("fixture: the outgoing views hold John 6") {
            oldViews.all { (it.session.initialKey as? org.crosswire.jsword.passage.Verse)?.chapter == 6 }
        }
        val oldGenerations = oldViews.associateWith { it.session.documentGeneration }
        val bId = newWorkspaceB(a.repo)

        a.activity.lifecycleScope.launch { a.activity.readingCommands.switchWorkspace(bId) }
        settleWorkspaceSwitches()
        compose.waitForIdle()

        val incoming = a.repo.windowList.single()
        val view = a.factory.getOrCreateBibleView(incoming)
        assertTrue("fixture: workspace B's window adopts an outgoing view", oldViews.any { it === view })
        val incomingVerse = org.crosswire.jsword.passage.Verse(outgoing.versification, outgoing.book, 6, 5)
        incoming.pageManager.setCurrentDocumentAndKey(kjv, incomingVerse)
        awaitHostUntil("workspace B's John 6 document must be admitted to the adopted view") {
            (view.session.initialKey as? org.crosswire.jsword.passage.Verse)?.let { it.book == outgoing.book && it.chapter == 6 } == true &&
                view.session.documentGeneration != oldGenerations.getValue(view)
        }
        val before = incoming.pageManager.currentBible.currentBibleVerse.verse
        assertEquals("fixture", incomingVerse, before)

        staleReport(view, "John.6", outgoing.ordinal, oldGenerations.getValue(view))

        assertEquals("the outgoing page's John 6:58 must not move workspace B's window off John 6:5",
            before, incoming.pageManager.currentBible.currentBibleVerse.verse)
    }

    /**
     * A call as the page posts it, through the `androidBridge` object [BibleView] registers with the WebView (the
     * production entry: endpoint, dispatcher, BibleJavascriptInterface and its gate), echoing [generation] (#3975).
     */
    private fun post(view: BibleView, method: String, args: String, generation: Long) =
        checkNotNull(view.androidBridgeEndpoint) { "the view registered its androidBridge" }
            .postMessage("""{"method":"$method","args":[$args],"documentGeneration":$generation}""")

    private fun staleReport(view: BibleView, keyStr: String, ordinal: Int, generation: Long) =
        post(view, "scrolledToOrdinal", "\"$keyStr\",$ordinal", generation)

    private fun verseOf(window: Window) = window.pageManager.currentBible.currentBibleVerse.verse

    /** Shows [chapter]:1 of John in [window] and waits until its view has admitted that document. */
    private fun admitJohn(window: Window, chapter: Int, verse: Int = 1): org.crosswire.jsword.passage.Verse {
        val view = window.bibleView as BibleView
        val v11n = verseOf(window).versification
        val target = org.crosswire.jsword.passage.Verse(v11n, org.crosswire.jsword.versification.BibleBook.JOHN, chapter, verse)
        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV"), target)
        awaitHostUntil("John $chapter must be admitted to ${window.displayId}'s view") {
            (view.session.initialKey as? org.crosswire.jsword.passage.Verse)?.let { it.book == target.book && it.chapter == chapter } == true &&
                view.session.documentGeneration != net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
        }
        return target
    }

    private fun john(window: Window, chapter: Int, verse: Int) = org.crosswire.jsword.passage.Verse(
        verseOf(window).versification, org.crosswire.jsword.versification.BibleBook.JOHN, chapter, verse)

    /**
     * #3975 review A: through the production entry, a report of the admitted document moves the window. Together with
     * the stale-report tests above (also through the production entry) this fails if the gate is removed or replaced:
     * an open gate lets the stale reports through, a missing (closed) one drops this one.
     */
    @Test fun aReportOfTheAdmittedDocumentMovesTheWindowThroughTheProductionEntry() {
        val window = workspaceA().oldWindows[0]
        val view = window.bibleView as BibleView
        admitJohn(window, 6)

        staleReport(view, "John.6", john(window, 6, 20).ordinal, view.session.documentGeneration)

        assertEquals(john(window, 6, 20), verseOf(window))
    }

    /**
     * #3975 review B (3): the dispatcher's check runs on the JavaBridge thread, but the window changes on the main
     * thread. The call runs on the main thread, and only if its document is still admitted THERE: a navigation queued
     * between the dispatch and the run drops it.
     */
    @Test fun aCallFromTheBridgeThreadActsOnTheMainThreadOnlyIfItsDocumentIsStillAdmittedThere() {
        val window = workspaceA().oldWindows[0]
        val view = window.bibleView as BibleView
        val start = admitJohn(window, 6)
        val generation = view.session.documentGeneration

        kotlin.concurrent.thread { staleReport(view, "John.6", john(window, 6, 20).ordinal, generation) }.join()
        assertEquals("the report must not act on the bridge thread", start, verseOf(window))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a report of the admitted document acts once it runs on the main thread", john(window, 6, 20), verseOf(window))

        kotlin.concurrent.thread { staleReport(view, "John.6", john(window, 6, 30).ordinal, generation) }.join()
        val requested = john(window, 8, 1)
        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV"), requested)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the report queued before the navigation must not undo it", requested, verseOf(window))
    }

    /**
     * #3975 review B (4): navigating requests a new document, which is admitted only after an asynchronous read. Until
     * then the page still shows (and reports) the outgoing chapter; such a report must not move the window off the
     * requested verse. A jump inside the loaded document requests nothing, so the page's reports keep counting.
     */
    @Test fun aReportOfTheOutgoingDocumentCannotUndoANavigationBeforeItsDocumentIsAdmitted() {
        val window = workspaceA().oldWindows[0]
        val view = window.bibleView as BibleView
        admitJohn(window, 6)
        val outgoing = view.session.documentGeneration

        val requested = john(window, 8, 1)
        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV"), requested)
        staleReport(view, "John.6", john(window, 6, 20).ordinal, outgoing)
        assertEquals("the outgoing page's report must not move the window off the requested verse", requested, verseOf(window))

        awaitHostUntil("John 8 must be admitted") {
            (view.session.initialKey as? org.crosswire.jsword.passage.Verse)?.chapter == 8 &&
                view.session.documentGeneration != net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION
        }
        assertEquals(requested, verseOf(window))
        val admitted = view.session.documentGeneration
        assertTrue("a new document, a new generation", admitted != outgoing)

        window.pageManager.setCurrentDocumentAndKey(Books.installed().getBook("KJV"), john(window, 8, 10))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a jump inside the loaded chapter requests no new document", admitted, view.session.documentGeneration)
        staleReport(view, "John.8", john(window, 8, 12).ordinal, admitted)
        assertEquals(john(window, 8, 12), verseOf(window))
    }

    private fun scenario(animationsOff: Boolean) {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        val a = workspaceA()
        val activity = a.activity
        val repo = a.repo
        val commands = activity.readingCommands
        val factory = a.factory
        val label = a.label
        val oldWindows = a.oldWindows
        val (w1, w2, w3) = oldWindows
        val oldIds = oldWindows.map { it.id }
        val aId = repo.id

        val bId = newWorkspaceB(repo)

        val held = HeldRead(realLoader(repo.persistence), repo.persistence)
        repo.loaderOverride = held
        activity.lifecycleScope.launch { commands.switchWorkspace(bId) }
        awaitHostUntil("the switch must reach the read phase") { held.entered.isCompleted }

        // The invariant: while the read is held the user still sees workspace 1, so no view is gone.
        assertTrue("the load is mid-read", repo.loadingFromDb)
        oldWindows.forEach { assertNotNull("${it.displayId} lost its view before the new workspace was read", it.bibleView) }
        val cachedDuringRead = cachedViews(factory)
        oldIds.forEach { assertTrue("$it was dropped from the view cache during the read", cachedDuringRead.containsKey(it)) }
        assertEquals(aId, repo.id)

        held.release.complete(Unit)
        settleWorkspaceSwitches()

        assertEquals(bId, repo.id)
        oldWindows.forEach { assertNull("${it.displayId}'s view must be destroyed once workspace 2 is applied", it.bibleView) }
        val cachedAfter = cachedViews(factory)
        oldIds.forEach { assertFalse("$it still cached after the swap", cachedAfter.containsKey(it)) }
        assertTrue("workspace 2 has a window", repo.windowList.isNotEmpty())
        repo.windowList.forEach {
            assertFalse("workspace 2 shows a workspace 1 window", it.id in oldIds)
            assertNotNull("${it.displayId} has no document", it.pageManager.currentPage.currentDocument)
        }

        // And back: every window of workspace 1 returns with its document and position.
        repo.saveIntoDb(false)
        runBlocking { repo.persistence.awaitPersisted() }
        repo.loaderOverride = null // the held loader's journal id is a stand-in; the default one restores StudyPads
        activity.lifecycleScope.launch { commands.switchWorkspace(aId) }
        settleWorkspaceSwitches()
        assertEquals(aId, repo.id)
        assertEquals(oldIds.toSet(), repo.windowList.map { it.id }.toSet())
        val r1 = repo.getWindow(w1.id)!!.pageManager.currentBible
        assertEquals("KJV", r1.currentDocument?.initials)
        assertEquals(7, r1.currentBibleVerse.verse.verse)
        val r2 = repo.getWindow(w2.id)!!.pageManager.currentBible
        assertEquals("NASB", r2.currentDocument?.initials)
        assertEquals(2, r2.currentBibleVerse.verse.chapter)
        assertEquals(3, r2.currentBibleVerse.verse.verse)
        val r3 = repo.getWindow(w3.id)!!.pageManager.currentGeneralBook
        assertEquals(label.id, (r3.key as StudyPadKey).label.id)
    }
}
