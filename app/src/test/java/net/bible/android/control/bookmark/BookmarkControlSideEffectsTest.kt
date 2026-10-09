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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
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
import java.io.ByteArrayOutputStream
import java.io.PrintStream

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

    @Before fun setUp() {
        control = BookmarkControl(windowControl, mock(AndroidResourceProvider::class.java))
        subscriptions += control.changes.subscribe { seen += it }
    }

    @After fun tearDown() {
        subscriptions.forEach { it.cancel() }
        resetDatabase()
    }

    private fun bookmark(labels: Set<IdType>? = null) =
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Ps 119:1"),
            null, true, null,
        ), labels)

    @Test fun aSubscriberThatBridgesFromInsideItsHandlerNeitherDeadlocksNorThrows() {
        control.insertOrUpdateLabel(Label(new = true).apply { name = "existing" })
        var result: Result<List<Label>>? = null
        subscriptions += control.changes.subscribe { if (it is BookmarkChange.LabelUpserted) result = runCatching { control.allLabels } }

        val worker = Thread { control.insertOrUpdateLabel(Label(new = true).apply { name = "second" }) }
        worker.start()
        worker.join(15_000)

        assertFalse("the bridge deadlocked", worker.isAlive)
        assertTrue("subscriber's bridged read failed: ${result?.exceptionOrNull()}", result?.isSuccess == true)
        assertTrue(result!!.getOrThrow().any { it.name == "second" })
    }

    /** A sync entry for a StudyPadTextEntryText row that does not exist makes the core throw (NPE) after it queued two events. */
    @Test fun whenTheCoreThrowsTheEffectsQueuedBeforeItAreStillFlushedAndTheOriginalSurfaces() {
        val label = control.insertOrUpdateLabel(Label(new = true).apply { name = "L" })
        seen.clear()
        val entries = listOf(
            LogEntry("Label", label.id, IdType.empty(), LogEntryTypes.UPSERT, 0L, "other-device"),
            LogEntry("StudyPadTextEntryText", IdType(), IdType.empty(), LogEntryTypes.UPSERT, 0L, "other-device"),
        )
        val err = ByteArrayOutputStream()
        val oldErr = System.err
        System.setErr(PrintStream(err, true))
        try {
            // The sync event source catches and prints a throwing subscriber: the printed trace is the surfaced exception.
            val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
                .map { it() }.single { it.category == SyncableDatabaseDefinition.BOOKMARKS }
            accessor._reactToUpdates!!.invoke(entries)
        } finally {
            System.setErr(oldErr)
        }
        assertEquals(label.id, seen.filterIsInstance<BookmarkChange.LabelUpserted>().single().label.id)
        assertEquals(2, seen.filterIsInstance<BookmarkChange.BookmarksDeleted>().size) // both queued deletes flushed
        assertTrue("original exception expected, got: $err", "NullPointerException" in err.toString())
    }

    @Test fun anEffectThatThrowsDoesNotDropTheEffectsAfterItNorFailTheCall() {
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
