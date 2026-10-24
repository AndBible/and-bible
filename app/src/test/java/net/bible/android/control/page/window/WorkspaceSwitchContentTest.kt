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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.Robolectric
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

    private fun scenario(animationsOff: Boolean) {
        CommonUtils.settings.setBoolean("disable_animations", animationsOff)
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.apply {
            create().start().resume().visible()
            awaitReadingGraph(get()) { compose.waitForIdle() }
        }
        val activity = controller.get()
        val repo = activity.hostWindowRepository
        val commands = activity.readingCommands
        val factory = commands.bibleViewFactory
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
        val oldIds = oldWindows.map { it.id }
        val aId = repo.id
        oldWindows.forEach { factory.getOrCreateBibleView(it) }
        assertEquals("fixture: three windows with a live view each", 3, oldWindows.count { it.bibleView != null })

        val bId = IdType()
        runBlocking { workspaceDao.insertWorkspace(WorkspaceEntities.Workspace("B").also { it.id = bId }) }
        repo.saveIntoDb(false)
        runBlocking { repo.persistence.awaitPersisted() }

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
