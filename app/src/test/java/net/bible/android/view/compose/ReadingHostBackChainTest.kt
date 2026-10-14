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

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * F55. The reading host's BACK chain.
 *
 * Against the pre-fix tree [backDoesNotFinishTheHostWhileHistoryRemains] FAILS: the dispatcher's
 * fallback finishes the Activity, because the chain lives in the reading destination's
 * `PassThroughBackHandler` and nothing else steps the history (the host's `isIntegrateWithHistoryManager`
 * is only ever true on a Search or ReadingPlan destination), leaving the history stack still full.
 *
 * Long-press BACK does nothing special (spec 2026-10-08 API 36, decisions 1 and 5: with predictive back
 * on the platform never delivers it); History is reached from the menu only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingHostBackChainTest {

    @get:org.junit.Rule
    val compose = createEmptyComposeRule()

    /**
     * Fix round 1, MINOR: every controller `host()` builds is kept here so [tearDown] can
     * `.close()` it -- the same `hostControllers` pattern `ReadingDestinationInGraphTest.buildHost`
     * uses, and for the identical reason its kdoc gives: an undestroyed host can leak state
     * (`ReadingHostPresence`'s foreground token, a published `ReadingViewHostCallbacks`) into the
     * NEXT test, in this file or another one sharing the JVM.
     */
    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    /**
     * `firstTime` is pinned false first, for the same reason `SelfLaunchAwaitIntentTest.host()`
     * does: it is a file-level `var` in `ActivityBase.kt` that Robolectric does not reset between
     * test METHODS in this JVM, and `ActivityBase.fixNightMode()` arms a delayed `recreate()` while
     * it is true. A recreate mid-test would confuse the `isFinishing` assertions below.
     */
    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.READING,
            ),
        ).also { hostControllers += it }.setup().get().also {
            net.bible.test.awaitReadingGraph(it) { compose.waitForIdle() }
        }
    }

    @Before fun readingViewIsWhatTheUserSees() = ReadingViewVisibility.setVisible(true)

    /**
     * `ReadingViewVisibility.setVisible(false)` alone (as the brief's fixture shows) resets the
     * flag, but `CommonUtils.settings`/`DatabaseContainer` -- and with them the workspace/window
     * `readingAppBootstrap.createWindowRepository()` loads inside `host()` -- are Koin/JVM-wide
     * singletons Robolectric does NOT reset between test methods (see `DatabaseResetter`'s own kdoc,
     * and `ReadingDestinationInGraphTest`/`ReadingHistoryAnchorTest`, which both reset for the same
     * reason). Without this, a later method's `host()` would reload the SAME window id a previous
     * method pushed history onto, and [backOnAnEmptyHistoryWarnsBeforeItExits]'s "no history pushed"
     * premise would be false depending on JUnit's method order. Added beyond the brief's literal
     * fixture for that reason.
     */
    @After
    fun resetReadingSeams() {
        net.bible.test.closeReadingHostsBeforeReset(hostControllers)
    }

    @Test fun resetJoinsStoppedPrivateHostQueryBeforeNativeClose() {
        val boundary = net.bible.service.llm.WorkspaceNativeLifetimeWitnessTest.Boundary(21, true)
        DatabaseResetter.resetDatabase()
        val originalFactory = net.bible.service.db.DatabaseContainer.containerFactory
        val witness = net.bible.service.llm.WorkspaceNativeLifetimeWitnessTest()
        var cleanupOwner: net.bible.service.llm.WorkspaceNativeLifetimeWitnessTest.CleanupOwner? = null
        val oldDrain = DatabaseResetter.drainObserver
        val oldClose = DatabaseResetter.beforeCloseObserver
        var primary: Throwable? = null
        var attempted = false
        val factory: () -> net.bible.service.db.DatabaseContainer = {
            originalFactory().also { container ->
                boundary.container = container
                cleanupOwner = witness.retainCleanupOwner(boundary, container)
                container.workspaceDb.close()
                val db = androidx.room3.Room.databaseBuilder<net.bible.android.database.WorkspaceDatabase>(
                    ApplicationProvider.getApplicationContext(), net.bible.android.database.WorkspaceDatabase.dbFileName
                ).setDriver(boundary).setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
                    .setJournalMode(androidx.room3.RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(*net.bible.android.database.migrations.workspacesMigrations).build()
                cleanupOwner!!.database = db
                boundary.database = db
                container.workspaceDb = db
            }
        }
        net.bible.service.db.DatabaseContainer.containerFactory = factory
        try {
            val activity = host()
            cleanupOwner!!.published = true
            val view = requireNotNull(activity.composeReadingViewHost)
            val scope = view.javaClass.getDeclaredField("hostScope").apply { isAccessible = true }.get(view) as kotlinx.coroutines.CoroutineScope
            val parent = requireNotNull(scope.coroutineContext[kotlinx.coroutines.Job])
            assertTrue("private host is independent of lifecycle", parent !== activity.lifecycleScope.coroutineContext[kotlinx.coroutines.Job])
            hostControllers.single().pause().stop()
            boundary.armed.set(true)
            val child = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { CommonUtils.globalTextDisplaySettings }
                finally { boundary.completed.set(true); boundary.event("private-host-query-complete") }
            }
            cleanupOwner!!.producer = child
            assertTrue(boundary.entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            DatabaseResetter.drainObserver = { jobs, drain ->
                assertTrue("exact private Job must be retained even after stop", jobs.any { it === parent })
                assertFalse("attempted join must suspend on real query", drain.isCompleted)
                assertFalse(child.isCompleted)
                attempted = true
                boundary.release.countDown()
            }
            DatabaseResetter.beforeCloseObserver = {
                // Reject omission BEFORE native reset, even though Boundary also defers exact handles.
                assertTrue("safe pre-close: private query join was attempted", attempted)
                assertTrue("safe pre-close: actual private owner completed", parent.isCompleted)
                assertTrue(boundary.completed.get())
            }
            DatabaseResetter.resetDatabase()
            assertTrue(child.isCompleted)
            assertTrue(boundary.index("delegate-step-exit") < boundary.index("connection-close-attempt"))
        } catch (e: Throwable) { primary = e; throw e }
        finally {
            boundary.release.countDown()
            DatabaseResetter.drainObserver = oldDrain
            DatabaseResetter.beforeCloseObserver = oldClose
            // Complete exact work before restoring any factory/container globals.
            try {
                DatabaseResetter.runResetBlocking { net.bible.test.ReadingFixtureOwners.drain() }
                net.bible.service.db.DatabaseContainer.containerFactory = originalFactory
                cleanupOwner?.finish()
            } catch (e: Throwable) { if (primary == null) throw e else if (primary !== e) primary.addSuppressed(e) }
        }
    }

    /** Pre-paused host-owned getter mechanism, not a Window.loadText or historical crash reproduction. */
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    @Test
    fun resetDrainsExactHostGetterBeforeSameHandleClose() {
        val boundary = net.bible.service.llm.WorkspaceNativeLifetimeWitnessTest.Boundary(20, true)
        DatabaseResetter.resetDatabase()
        val originalFactory = net.bible.service.db.DatabaseContainer.containerFactory
        val witness = net.bible.service.llm.WorkspaceNativeLifetimeWitnessTest()
        var owner: net.bible.service.llm.WorkspaceNativeLifetimeWitnessTest.CleanupOwner? = null
        var originalFailure: Throwable? = null
        var producer: kotlinx.coroutines.Job? = null
        val originalObserver = DatabaseResetter.drainObserver
        var installedObserver: ((List<kotlinx.coroutines.Job>, kotlinx.coroutines.Deferred<Unit>) -> Unit)? = null
        var observedDrain = false
        val factory: () -> net.bible.service.db.DatabaseContainer = {
            originalFactory().also { container ->
                boundary.container = container
                owner = witness.retainCleanupOwner(boundary, container)
                container.workspaceDb.close()
                val replacement = androidx.room3.Room.databaseBuilder<net.bible.android.database.WorkspaceDatabase>(
                    ApplicationProvider.getApplicationContext(), net.bible.android.database.WorkspaceDatabase.dbFileName
                ).setDriver(boundary).setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
                    .setJournalMode(androidx.room3.RoomDatabase.JournalMode.TRUNCATE)
                    .addMigrations(*net.bible.android.database.migrations.workspacesMigrations).build()
                owner!!.database = replacement
                boundary.database = replacement
                container.workspaceDb = replacement
            }
        }
        net.bible.service.db.DatabaseContainer.containerFactory = factory
        try {
            val activity = host()
            owner!!.published = true
            val controller = hostControllers.single()
            val parent = checkNotNull(activity.lifecycleScope.coroutineContext[kotlinx.coroutines.Job])
            assertTrue(activity.hostWindowRepository.scope.coroutineContext[kotlinx.coroutines.Job] === parent)
            controller.pause()
            boundary.armed.set(true)
            producer = activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { CommonUtils.globalTextDisplaySettings }
                finally { boundary.completed.set(true); boundary.event("host-getter-completion") }
            }
            owner!!.producer = producer
            val child = producer
            assertTrue("HARNESS FAILURE: target hold absent", boundary.entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(parent.children.any { it === child })
            installedObserver = { jobs, drain ->
                assertTrue("actual drain includes exact lifecycle parent", jobs.any { it === parent })
                assertFalse("UNDISPATCHED join really suspended on held parent", drain.isCompleted)
                assertFalse(child.isCompleted)
                assertEquals(-1, boundary.index("connection-close-attempt"))
                observedDrain = true
                boundary.event("actual-reset-join-suspended exact-parent-incomplete")
                boundary.release.countDown()
            }
            DatabaseResetter.drainObserver = installedObserver
            controller.stop().destroy()
            // Keep the actual scopes retained even after host destruction; use the same reset seam as @After.
            DatabaseResetter.resetDatabase(activity.lifecycleScope)
            assertTrue("observer reached actual suspended drain", observedDrain)
            assertTrue(child.isCompleted)
            assertTrue(boundary.index("host-getter-completion") < boundary.index("connection-close-attempt"))
            assertTrue(boundary.index("delegate-step-exit") < boundary.index("connection-close-attempt"))
        } catch (e: Throwable) {
            originalFailure = e
            throw e
        } finally {
            boundary.release.countDown()
            if (DatabaseResetter.drainObserver === installedObserver) DatabaseResetter.drainObserver = originalObserver
            if (net.bible.service.db.DatabaseContainer.containerFactory === factory) net.bible.service.db.DatabaseContainer.containerFactory = originalFactory
            try { owner?.finish() } catch (e: Throwable) {
                if (originalFailure == null) throw e
                if (originalFailure !== e) originalFailure.addSuppressed(e)
            }
        }
    }


    @Test
    fun theChainIsClassicsSixBranchesInClassicsOrder() {
        val names = host().readingBackChain.map { it.name }
        assertEquals(
            "the chain must mirror MainBibleActivity.onBackPressed's branch order; the double-back " +
                "exit warning is handled after the loop, not as a step",
            listOf("drawer", "search", "fullscreen", "webViewModal", "history"),
            names,
        )
    }

    @Test
    fun onlyTheModalAndHistoryStepsClearTheExitWarning() {
        val clearing = host().readingBackChain.filter { it.clearsExitWarning }.map { it.name }
        assertEquals(
            "classic clears lastBackPressed only after the WebView-modal and history branches; its " +
                "drawer/search/fullscreen branches return before that assignment",
            listOf("webViewModal", "history"),
            clearing,
        )
    }

    /** The Koin singleton `historyTraversal.historyManager` resolves to (`CoreModule.kt`'s
     *  `singleOf(::HistoryManager)`), so pushing into it is pushing into what the host reads. */
    private fun historyManager(): HistoryManager = GlobalContext.get().get()

    private fun historyDepth(): Int =
        historyManager().getEntities(CommonUtils.windowControl.activeWindow.id).size

    private var depthBeforeTheBackPress: Int = 0

    /**
     * Pushes two distinct KJV reading positions into the host's OWN window -- the one
     * `readingAppBootstrap.createWindowRepository()` installs as `windowControl.windowRepository`
     * during `host()`'s `onCreate` -- exactly as a real reading session would. Fixture style copied
     * from `ReadingHistoryAnchorTest.historyManagerWithOneWindow`.
     */
    private fun pushTwoReadingPositions() {
        val kjv = requireNotNull(Books.installed().getBook("KJV")) { "KJV test module must be installed" }
        val versification = Versifications.instance().getVersification("KJV")
        val manager = historyManager()
        val window = CommonUtils.windowControl.activeWindow

        window.pageManager.currentBible.setCurrentDocumentAndKey(
            kjv, Verse(versification, BibleBook.PS, 139, 2),
        )
        manager.addHistoryItem(window)

        window.pageManager.currentBible.setCurrentDocumentAndKey(
            kjv, Verse(versification, BibleBook.PS, 23, 1),
        )
        manager.addHistoryItem(window)

        depthBeforeTheBackPress = historyDepth()
    }

    @Test
    fun backDoesNotFinishTheHostWhileHistoryRemains() {
        val activity = host()
        // Fixture: push at least two reading positions into the real HistoryManager, exactly as
        // ReadingHistoryAnchorTest does, so canGoBack() is true.
        pushTwoReadingPositions()

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(
            "BACK with a non-empty reading history must walk the history, not finish the app (F55)",
            activity.isFinishing,
        )
        assertTrue(
            "and the history stack must have shrunk -- not finishing is only half the assertion",
            historyDepth() < depthBeforeTheBackPress,
        )
    }

    @Test
    fun backOnAnEmptyHistoryWarnsBeforeItExits() {
        val activity = host()
        // No history pushed: the chain falls through to the double-back warning.
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse("the first press must only warn", activity.isFinishing)

        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("the second press within the window exits", activity.isFinishing)
    }
}
