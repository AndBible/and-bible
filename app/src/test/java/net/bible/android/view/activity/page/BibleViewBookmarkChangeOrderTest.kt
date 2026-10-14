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
    private val bodyJobs = mutableListOf<Job>()
    private val fixtureJobs = mutableListOf<Job>()

    @After fun resetMain() {
        check(bodyJobs.all { it.isCompleted } && fixtureJobs.all { it.isCompleted }) {
            "Exact bookmark fixture children must complete before Main restoration/reset"
        }
        Dispatchers.resetMain()
        DatabaseResetter.resetDatabase()
    }

    @Test fun label_update_precedes_bookmark_update_while_override_read_is_suspended() = bookmarkScenario(false)

    @Test fun failedDisposalAndHeldJoinRetainExactRestorationUntilSafeRetry() = bookmarkScenario(true)

    private fun bookmarkScenario(exceptionalCleanup: Boolean) = runTest {
        bodyJobs += checkNotNull(currentCoroutineContext()[Job])
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repoJob = SupervisorJob().also { fixtureJobs += it }
        val repo = WindowRepository(CoroutineScope(repoJob + Dispatchers.Main))
        val originalRepo = CommonUtils.windowControl.windowRepository
        val container = DatabaseContainer.instance
        val originalDb = container.workspaceDb
        var view: BibleView? = null
        var lifecycle: LifecycleRegistry? = null
        val gate = CompletableDeferred<Unit>()
        var primary: Throwable? = null
        val assertionFailure = AssertionError("original bookmark assertion failure")
        val disposalFailure = IllegalArgumentException("injected first view disposal failure")
        var disposalAttempts = 0
        val joinRelease = CompletableDeferred<Unit>()
        val joinEntered = CompletableDeferred<Unit>()
        var heldChild: Job? = null
        var replacementDb: WorkspaceDatabase? = null
        net.bible.test.ReadingFixtureOwners.retain(listOf(repo.scope))
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
            fixtureJobs += checkNotNull(view.scope.coroutineContext[Job])
            val ownedView = view
            net.bible.test.ReadingFixtureOwners.retain(listOf(ownedView.scope)) {
                disposalAttempts++
                if (exceptionalCleanup && disposalAttempts == 1) throw disposalFailure
                ownedView.listenEvents = false
                ownedView.destroy()
            }
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
            replacementDb = heldDb
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
            if (exceptionalCleanup) {
                heldChild = view.scope.launch {
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) { joinEntered.complete(Unit); joinRelease.await() }
                    }
                }
                runCurrent()
                throw assertionFailure
            }
        } catch (e: Throwable) {
            primary = e
            if (!exceptionalCleanup || e !== assertionFailure) throw e
        }
        finally {
            gate.complete(Unit)
            var failure = primary
            fun attempt(action: () -> Unit) {
                try { action() } catch (e: Throwable) {
                    if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
                }
            }
            val restore = {
                container.workspaceDb = originalDb
                CommonUtils.windowControl.windowRepository = originalRepo
            }
            // Retain restoration before disposal; failed joins must not restore the live DAO/graph.
            net.bible.test.ReadingFixtureOwners.retainRestoration(restore)
            attempt { lifecycle?.currentState = Lifecycle.State.DESTROYED }
            attempt { view?.listenEvents = false }
            attempt { net.bible.test.ReadingFixtureOwners.disposeAndSnapshot() }
            repo.windowList.forEach { window -> attempt { window.destroy(destroyView = false) } }
            fixtureJobs.forEach { it.cancel() }
            try {
                withContext(NonCancellable) {
                    if (exceptionalCleanup) {
                        val exactWorker = java.util.concurrent.atomic.AtomicReference<Job>()
                        var fakeCommit = false
                        try {
                            val timeout = runCatching {
                                DatabaseResetter.runResetBlocking(timeoutMs = 100) {
                                    exactWorker.set(checkNotNull(checkNotNull(currentCoroutineContext()[Job]).parent))
                                    // Join the exact lifecycle/repository owners; never close while held.
                                    fixtureJobs.forEach { it.join() }
                                    commitReset { check(fixtureJobs.all { it.isCompleted }); fakeCommit = true }
                                }
                            }.exceptionOrNull()
                            assertTrue("held exact join must abort preparation", timeout is IllegalStateException)
                            if (timeout != null) failure!!.addSuppressed(timeout)
                            runCurrent()
                            assertTrue("real lifecycle child entered cancellation finally", joinEntered.isCompleted)
                            assertFalse(heldChild!!.isCompleted)
                            assertFalse(fakeCommit)
                            assertSame("DAO restoration must remain retained under held work", replacementDb, container.workspaceDb)
                            assertSame("repository restoration must remain retained under held work", repo, CommonUtils.windowControl.windowRepository)
                            assertThrows(IllegalStateException::class.java) { net.bible.test.ReadingFixtureOwners.requireEmpty() }
                            assertThrows(IllegalStateException::class.java) { resetMain() }
                            val app = RuntimeEnvironment.getApplication() as TestBibleApplication
                            assertThrows(IllegalStateException::class.java) { app.onCreate() }
                            assertSame(assertionFailure, failure)
                            assertTrue(failure!!.suppressed.any { it === disposalFailure })
                            assertTrue(failure!!.suppressed.any { it === timeout })
                        } finally {
                            joinRelease.complete(Unit)
                            exactWorker.get()?.let { exact ->
                                val deadline = System.nanoTime() + 5_000_000_000L
                                while (!exact.isCompleted && System.nanoTime() < deadline) {
                                    testScheduler.runCurrent()
                                    Thread.yield()
                                }
                                check(exact.isCompleted) { "Exact aborted parent must finish on original scheduler" }
                                exact.join()
                            }
                            DatabaseResetter.requireNoRetainedWorkers()
                        }
                    }
                    fixtureJobs.forEach { it.join() }
                    // Suspending here keeps runTest driving the exact Standard scheduler on Main.
                    net.bible.test.ReadingFixtureOwners.drain()
                }
            } catch (e: Throwable) {
                if (failure == null) failure = e else if (failure !== e) failure!!.addSuppressed(e)
            }
            if (exceptionalCleanup) {
                // A failed oracle is not an expected fixture failure; cleanup safely before reporting it.
                joinRelease.complete(Unit)
                withContext(NonCancellable) {
                    fixtureJobs.forEach { it.join() }
                    net.bible.test.ReadingFixtureOwners.drain()
                }
                assertSame(originalDb, container.workspaceDb)
                assertSame(originalRepo, CommonUtils.windowControl.windowRepository)
                net.bible.test.ReadingFixtureOwners.requireEmpty()
                assertTrue("failed disposal must be retried", disposalAttempts >= 2)
                // Only the injected disposal failure and the bounded-reset timeout are expected;
                // anything else (including a failed oracle assertion) must fail the test.
                assertSame("primary failure must be preserved", assertionFailure, failure)
                failure!!.suppressed.firstOrNull { it !== disposalFailure && !(it is IllegalStateException && it.message?.startsWith("Database fixture cleanup timed out") == true) }
                    ?.let { throw it }
            }
            if (primary == null) failure?.let { throw it }
        }
    }
}
