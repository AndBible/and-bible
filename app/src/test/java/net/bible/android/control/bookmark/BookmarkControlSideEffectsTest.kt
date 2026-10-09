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

package net.bible.android.control.bookmark

import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.blockingDb
import net.bible.sharedcore.event.Subscription
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The emit-after-bridge contract of [BookmarkControl]'s bridged calls: effects queued while the DAO calls run are
 * flushed after the bridge returned, all of them, and a failing effect or core never masks the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarkControlSideEffectsTest {
    private val windowControl = mock(WindowControl::class.java)
    private lateinit var control: BookmarkControl
    private val subscriptions = mutableListOf<Subscription>()
    private val seen = mutableListOf<BookmarkChange>()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Waits for the `bookmark-sync` blocks the control launched (a sync notification is handled asynchronously). */
    private fun awaitLaunchedSyncBlocks() = runBlocking { syncScope.coroutineContext[Job]!!.children.toList().joinAll() }

    @Before fun setUp() {
        control = BookmarkControl(windowControl, testAppSettings(), testCoreStrings(), OrderedLauncher(syncScope))
        subscriptions += control.changes.subscribe { seen += it }
    }

    @After fun tearDown() {
        subscriptions.forEach { it.cancel() }
        syncScope.cancel()
        resetDatabase()
    }

    private suspend fun bookmark(labels: Set<IdType>? = null) =
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Ps 119:1"),
            null, true, null,
        ), labels)

    @Test fun aSubscriberThatBridgesFromInsideItsHandlerNeitherDeadlocksNorThrows() = runTest {
        control.insertOrUpdateLabel(Label(new = true).apply { name = "existing" })
        var result: Result<List<Label>>? = null
        subscriptions += control.changes.subscribe { if (it is BookmarkChange.LabelUpserted) result = runCatching { blockingDb { control.allLabels() } } }

        val worker = Thread { runBlocking { control.insertOrUpdateLabel(Label(new = true).apply { name = "second" }) } }
        worker.start()
        worker.join(15_000)

        assertFalse("the bridge deadlocked", worker.isAlive)
        assertTrue("subscriber's bridged read failed: ${result?.exceptionOrNull()}", result?.isSuccess == true)
        assertTrue(result!!.getOrThrow().any { it.name == "second" })
    }

    /**
     * Review Focus #2: a subscriber that bridges (as BibleView's handler does) must run only once the outermost call is
     * over, never inside its transaction/bridge. [BookmarkControl.deleteLabels] with orphan deletion emits
     * BookmarksDeleted from a nested step BEFORE it deletes the label; the handler's own read must already see the
     * label gone (effects run at the end of the outermost call), every change must arrive, in emission order, and the
     * handler's bridge must not throw BlockingDbInTransaction.
     */
    @Test fun aBridgingSubscriberRunsAfterTheOutermostCallAndSeesItsFinalStateInEmissionOrder() = runTest {
        val label = control.insertOrUpdateLabel(Label(new = true).apply { name = "doomed" })
        val b = bookmark(labels = setOf(label.id))
        seen.clear()
        val labelSeenByHandler = mutableListOf<Label?>()
        val handlerFailures = mutableListOf<Throwable>()
        subscriptions += control.changes.subscribe {
            try {
                labelSeenByHandler += blockingDb { control.labelById(label.id) }
            } catch (e: Throwable) { handlerFailures += e }
        }

        control.deleteLabels(listOf(label.id), deleteOrphanedBookmarks = true)

        assertEquals("handler bridge failed: $handlerFailures", emptyList<Throwable>(), handlerFailures)
        assertEquals(
            listOf(BookmarkChange.BookmarksDeleted::class, BookmarkChange.BookmarksUpserted::class, BookmarkChange.LabelsDeleted::class),
            seen.map { it::class },
        )
        assertEquals(b.id, (seen[0] as BookmarkChange.BookmarksDeleted).bookmarkIds.single())
        assertEquals("handlers saw the label before the outermost call finished", listOf<Label?>(null, null, null), labelSeenByHandler)
    }

    /**
     * A public call made from inside another public call joins the OUTER call's queue: its effects are not flushed when
     * the nested call returns but, after everything the outer call itself queued, once the outermost call returns.
     */
    @Test fun aNestedCallJoinsTheOuterQueueAndItsEffectsFollowTheOuterCallsOwnInOrder() = runTest {
        val log = mutableListOf<String>()
        control.deferringEffects {
            control.afterBridge { log += "outer-effect" }
            control.deferringEffects {
                control.afterBridge { log += "nested-effect" }
                log += "nested-core-done"
            }
            log += "nested-returned"
        }
        assertEquals(listOf("nested-core-done", "nested-returned", "outer-effect", "nested-effect"), log)
    }

    /** The real case: `allLabels` creates the Unlabelled label through a nested special-label getter on a fresh database. */
    @Test fun aSpecialLabelCreatedInsideAnOuterCallAnnouncesItExactlyOnceAfterTheOuterCallsEffects() = runTest {
        val order = mutableListOf<String>()
        subscriptions += control.changes.subscribe { order += it::class.simpleName!! }

        val labels = control.deferringEffects {
            control.afterBridge { order += "outer-effect" }
            control.allLabels()
        }

        assertTrue(labels.any { it.isUnlabeledLabel })
        assertEquals(listOf("outer-effect", "LabelUpserted"), order)
        assertEquals(1, seen.filterIsInstance<BookmarkChange.LabelUpserted>().size)
    }

    /** A sync entry for a StudyPadTextEntryText row that does not exist makes the core throw (NPE) after it queued two events. */
    @Test fun whenTheCoreThrowsTheEffectsQueuedBeforeItAreStillFlushedAndTheOriginalSurfaces() = runTest {
        val label = control.insertOrUpdateLabel(Label(new = true).apply { name = "L" })
        seen.clear()
        val entries = listOf(
            LogEntry("Label", label.id, IdType.empty(), LogEntryTypes.UPSERT, 0L, "other-device"),
            LogEntry("StudyPadTextEntryText", IdType(), IdType.empty(), LogEntryTypes.UPSERT, 0L, "other-device"),
        )
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val oldSink = Log.sinkOverride
        Log.sinkOverride = { level, _, _, tr -> if (level == LogLevel.ERROR && tr != null) errors += tr }
        try {
            // The ordered launcher logs a failing block as an ERROR with its exception: that is the surfaced exception.
            val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
                .map { it() }.single { it.category == SyncableDatabaseDefinition.BOOKMARKS }
            accessor._reactToUpdates!!.invoke(entries)
            awaitLaunchedSyncBlocks()
        } finally {
            Log.sinkOverride = oldSink
        }
        assertEquals(label.id, seen.filterIsInstance<BookmarkChange.LabelUpserted>().single().label.id)
        assertEquals(2, seen.filterIsInstance<BookmarkChange.BookmarksDeleted>().size) // both queued deletes flushed
        assertTrue("original exception expected, got: $errors", errors.any { it is NullPointerException })
    }

    @Test fun anEffectThatThrowsDoesNotDropTheEffectsAfterItNorFailTheCall() = runTest {
        val repo = WindowRepository(CoroutineScope(Dispatchers.Unconfined)).apply {
            workspaceSettings = WorkspaceEntities.WorkspaceSettings(recentLabels = ThrowingList())
        }
        `when`(windowControl.windowRepository).thenReturn(repo)
        val label = control.insertOrUpdateLabel(Label(new = true).apply { name = "L" })
        seen.clear()

        // addLabels queues updateRecentLabels (which throws on the list), then the BookmarksUpserted event.
        val saved = bookmark(labels = setOf(label.id))

        assertNotNull(saved)
        assertEquals(saved.id, seen.filterIsInstance<BookmarkChange.BookmarksUpserted>().single().bookmarks.single().id)
    }

    private class ThrowingList : ArrayList<WorkspaceEntities.RecentLabel>() {
        override fun iterator(): MutableIterator<WorkspaceEntities.RecentLabel> = throw IllegalStateException("recentLabels boom")
        override fun add(element: WorkspaceEntities.RecentLabel): Boolean = throw IllegalStateException("recentLabels boom")
    }
}
