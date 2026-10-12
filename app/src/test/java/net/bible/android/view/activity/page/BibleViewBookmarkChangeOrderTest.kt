package net.bible.android.view.activity.page

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceDao
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import net.bible.sharedcore.event.EventSource

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BibleViewBookmarkChangeOrderTest {
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun label_update_precedes_bookmark_update_while_override_read_is_suspended() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repoJob = SupervisorJob()
        val repo = WindowRepository(CoroutineScope(repoJob + Dispatchers.Main))
        val originalRepo = CommonUtils.windowControl.windowRepository
        val container = DatabaseContainer.instance
        val originalDb = container.workspaceDb
        var view: BibleView? = null
        var lifecycle: LifecycleRegistry? = null
        val gate = CompletableDeferred<Unit>()
        try {
            CommonUtils.windowControl.windowRepository = repo
            repo.initialize()
            val control = WindowControl().apply { windowRepository = repo }
            val host = mock<ReadingHostActivity>()
            whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
            val hostLifecycle = LifecycleRegistry.createUnsafe(host)
            lifecycle = hostLifecycle
            hostLifecycle.currentState = Lifecycle.State.STARTED
            whenever(host.lifecycle).thenReturn(hostLifecycle)
            val callbacks = BibleViewHostCallbacks(mock(), {}, {}, { _, _ -> }, { _, _ -> false },
                { false }, {}, { null }, { _, _ -> }, {}, { false }, { 0 }, { 0 }, { 0 }, { EventSource() })
            val bookmarks = GlobalContext.get().get<BookmarkControl>()
            view = BibleView(host, callbacks, WeakReference(repo.activeWindow), control,
                GlobalContext.get().get(), PageTiltScrollControl(), GlobalContext.get().get(),
                bookmarks, GlobalContext.get().get(), GlobalContext.get().get())
            BibleView::class.java.getDeclaredField("pageTiltScroller").apply {
                isAccessible = true
                set(view, PageTiltScroller(view, PageTiltScrollControl()))
            }
            BibleView::class.java.getDeclaredField("htmlLoadingOngoing").apply {
                isAccessible = true; set(view, false)
            }
            // Hold the real subscriber's DAO read, not an imitation of the event handler.
            val entered = CompletableDeferred<Unit>()
            val realDao = originalDb.workspaceDao()
            val heldDao = object : WorkspaceDao by realDao {
                override suspend fun labelOverrides(workspaceId: IdType): List<WorkspaceEntities.WorkspaceLabelOverride> {
                    entered.complete(Unit)
                    gate.await()
                    return emptyList() // This workspace has no style overrides.
                }
            }
            val heldDb = mock<WorkspaceDatabase>()
            whenever(heldDb.workspaceDao()).thenReturn(heldDao)
            container.workspaceDb = heldDb
            val label = BookmarkEntities.Label(name = "FIFO favourite", favourite = true)
            val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
                VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Gen 1:1"),
                null, true, null,
            ).apply { labelIds = listOf(label.id) }
            view.listenEvents = true
            val events = bookmarks.changes as EventSource<net.bible.android.control.bookmark.BookmarkChange>
            events.emit(net.bible.android.control.bookmark.BookmarkChange.LabelUpserted(label))
            runCurrent()
            assertTrue("Label DAO must really be suspended", entered.isCompleted)
            // Favourites must change on the emitter thread even while JS handling is suspended.
            val favourites = BibleView::class.java.getDeclaredField("favouriteLabels").run {
                isAccessible = true; get(view) as FavouriteLabelIdsCache
            }
            assertEquals(listOf(label.id), favourites.ids)
            events.emit(net.bible.android.control.bookmark.BookmarkChange.BookmarksUpserted(listOf(bookmark)))
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            ShadowLog.clear()
            BibleView::class.java.getDeclaredMethod("flushTasks").apply { isAccessible = true; invoke(view) }
            val scripts = ShadowLog.getLogs().map { it.msg }.filter { it.startsWith("Executing JS:") }
            val labelIndex = scripts.indexOfFirst { it.contains("update_labels") }
            val bookmarkIndex = scripts.indexOfFirst { it.contains("add_or_update_bookmarks") }
            assertTrue("Both actual WebView commands must execute: $scripts", labelIndex >= 0 && bookmarkIndex >= 0)
            assertTrue("Labels must precede their bookmarks: $scripts", labelIndex < bookmarkIndex)
        } finally {
            gate.complete(Unit)
            try {
                lifecycle?.currentState = Lifecycle.State.DESTROYED
                view?.listenEvents = false
                view?.destroy()
                repo.windowList.forEach { it.destroy(destroyView = false) }
            } finally {
                try { repoJob.cancelAndJoin() } finally {
                    container.workspaceDb = originalDb
                    CommonUtils.windowControl.windowRepository = originalRepo
                    DatabaseResetter.resetDatabase(repo.scope)
                }
            }
        }
    }
}
