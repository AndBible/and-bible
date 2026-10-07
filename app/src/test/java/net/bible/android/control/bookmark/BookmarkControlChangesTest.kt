package net.bible.android.control.bookmark

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.event.Subscription
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarkControlChangesTest {
    private lateinit var control: BookmarkControl
    private lateinit var subscription: Subscription
    private val seen = mutableListOf<BookmarkChange>()
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    @Before fun setUp() {
        control = BookmarkControl(mock(WindowControl::class.java), mock(AndroidResourceProvider::class.java))
        subscription = control.changes.subscribe { seen += it }
    }

    @After fun tearDown() {
        subscription.cancel()
        resetDatabase()
    }

    private fun bookmark(verse: Int = 1, labels: Set<IdType>? = null): BibleBookmarkWithNotes =
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Ps 119:$verse"),
            null, true, null,
        ), labels)

    private fun label() = control.insertOrUpdateLabel(Label(new = true).apply { name = "X" })

    @Test fun addingABookmarkEmitsBookmarksUpserted() {
        val saved = bookmark()
        assertEquals(saved.id, seen.filterIsInstance<BookmarkChange.BookmarksUpserted>().single().bookmarks.single().id)
    }

    @Test fun deletingBookmarksEmitsBookmarksDeleted() {
        val b = bookmark()
        seen.clear()
        control.deleteBookmark(b)
        assertEquals(listOf(b.id), seen.filterIsInstance<BookmarkChange.BookmarksDeleted>().single().bookmarkIds)
        val b1 = bookmark(2)
        val b2 = bookmark(3)
        seen.clear()
        control.deleteBibleBookmarksById(listOf(b1.id, b2.id))
        assertEquals(setOf(b1.id, b2.id), seen.filterIsInstance<BookmarkChange.BookmarksDeleted>().single().bookmarkIds.toSet())
    }

    @Test fun insertingALabelEmitsLabelUpserted() {
        val saved = label()
        assertEquals(saved.id, seen.filterIsInstance<BookmarkChange.LabelUpserted>().single().label.id)
    }

    @Test fun specialLabelCreationEmitsOnlyWhenCreated() {
        assertNull(dao.labelById(UNLABELED_LABEL_ID))
        val saved = control.labelUnlabelled
        assertEquals(UNLABELED_LABEL_ID, saved.id)
        assertEquals(UNLABELED_LABEL_ID, seen.filterIsInstance<BookmarkChange.LabelUpserted>().single().label.id)
        assertNotNull(dao.labelById(UNLABELED_LABEL_ID))
        seen.clear()
        control.labelUnlabelled
        assertTrue(seen.isEmpty())
    }

    // SDK 33's native test SQLite does not support the DAO's UPSERT syntax.
    @Config(sdk = [35])
    @Test fun savingANoteEmitsNoteModified() {
        val b = bookmark()
        seen.clear()
        control.saveBibleBookmarkNote(b.id, "n")
        val event = seen.filterIsInstance<BookmarkChange.NoteModified>().single()
        assertEquals(b.id, event.bookmarkId)
        assertEquals("n", event.notes)
        assertEquals(dao.bibleBookmarkById(b.id)!!.lastUpdatedOn.time, event.lastUpdatedOn)
        seen.clear()
        control.saveBibleBookmarkNote(b.id, null)
        assertNull(seen.filterIsInstance<BookmarkChange.NoteModified>().single().notes)
        assertNull(dao.bibleBookmarkById(b.id)!!.notes)
    }

    @Config(sdk = [35])
    @Test fun savingAGenericNoteEmitsNoteModified() {
        val b = control.addOrUpdateGenericBookmark(GenericBookmarkWithNotes(
            key = "test-key", bookInitials = "missing-test-document", ordinalStart = null, ordinalEnd = null,
            startOffset = null, endOffset = null, playbackSettings = null, new = true,
        ))
        seen.clear()
        control.saveGenericBookmarkNote(b.id, "generic note")
        val event = seen.filterIsInstance<BookmarkChange.NoteModified>().single()
        assertEquals(b.id, event.bookmarkId)
        assertEquals("generic note", event.notes)
        assertEquals("generic note", dao.genericBookmarkById(b.id)!!.notes)
        assertEquals(dao.genericBookmarkById(b.id)!!.lastUpdatedOn.time, event.lastUpdatedOn)
        seen.clear()
        control.saveGenericBookmarkNote(b.id, null)
        assertNull(seen.filterIsInstance<BookmarkChange.NoteModified>().single().notes)
        assertNull(dao.genericBookmarkById(b.id)!!.notes)
    }

    @Test fun deleteLabelsEmitsBookmarksBeforeLabelsDeleted() {
        val l = label()
        val b = bookmark(labels = setOf(l.id))
        seen.clear()
        control.deleteLabels(listOf(l.id))
        assertEquals(listOf(BookmarkChange.BookmarksUpserted::class, BookmarkChange.LabelsDeleted::class), seen.map { it::class }.takeLast(2))
        assertEquals(listOf(l.id), (seen.last() as BookmarkChange.LabelsDeleted).labelIds)
        val refreshed = (seen[seen.lastIndex - 1] as BookmarkChange.BookmarksUpserted).bookmarks.single()
        assertEquals(b.id, refreshed.id)
        assertEquals(emptyList<IdType>(), refreshed.labelIds)
    }

    @Test fun updateBookmarkToLabelEmitsBookmarkToLabelUpserted() {
        val l = label()
        val b = bookmark(labels = setOf(l.id))
        val link = control.getBibleBookmarkToLabel(b.id, l.id)!!.apply { indentLevel = 2 }
        seen.clear()
        control.updateBookmarkToLabel(link)
        assertEquals(link, seen.filterIsInstance<BookmarkChange.BookmarkToLabelUpserted>().single().bookmarkToLabel)
        assertEquals(2, control.getBibleBookmarkToLabel(b.id, l.id)!!.indentLevel)
    }

    @Test fun studyPadMutationsEmitStudyPadOrder() {
        val l = label()
        seen.clear()
        control.createStudyPadEntry(l.id, 0)
        val entry = dao.studyPadTextEntriesByLabelId(l.id).single()
        assertTrue(seen.filterIsInstance<BookmarkChange.StudyPadOrder>().any { it.labelId == l.id && it.newStudyPadTextEntry?.id == entry.id })
        seen.clear()
        control.updateStudyPadTextEntryText(entry.id, "t")
        assertEquals("t", seen.filterIsInstance<BookmarkChange.StudyPadOrder>().single().newStudyPadTextEntry!!.text)
        control.deleteStudyPadTextEntry(entry.id)
        assertEquals(entry.id, seen.filterIsInstance<BookmarkChange.StudyPadTextEntryDeleted>().single().studyPadTextEntryId)
        assertNull(dao.studyPadTextEntryById(entry.id))
    }

    @Test fun changeLabelsForBookmarksEmitsOnceWithHydratedLabels() {
        val l = label()
        val ids = listOf(bookmark().id, bookmark(2).id)
        val loaded = dao.bibleBookmarksByIds(ids)
        assertTrue(loaded.all { it.labelIds == null })
        seen.clear()
        control.changeLabelsForBookmarks(loaded, listOf(l.id))
        val emitted = seen.filterIsInstance<BookmarkChange.BookmarksUpserted>().single().bookmarks
        assertEquals(2, emitted.size)
        assertEquals(ids.toSet(), emitted.map { it.id }.toSet())
        assertTrue(emitted.all { (it as BibleBookmarkWithNotes).labelIds == listOf(l.id) })
        assertTrue(loaded.all { control.labelsForBookmark(it).map { label -> label.id } == listOf(l.id) })
    }

    @Test fun notifyLabelChangedEmitsLabelUpserted() {
        val l = label()
        seen.clear()
        control.notifyLabelChanged(l)
        assertEquals(l.id, seen.filterIsInstance<BookmarkChange.LabelUpserted>().single().label.id)
    }

    @Test fun insertBookmarkToLabelWritesAndEmits() {
        val l = label()
        val b = bookmark()
        val link = BibleBookmarkToLabel(bookmarkId = b.id, labelId = l.id, orderNumber = 0, indentLevel = 0)
        seen.clear()
        control.insertBookmarkToLabel(link)
        assertEquals(link, seen.filterIsInstance<BookmarkChange.BookmarkToLabelUpserted>().single().bookmarkToLabel)
        assertEquals(link, control.getBibleBookmarkToLabel(b.id, l.id))
    }

    @Test fun insertGenericBookmarkToLabelWritesAndEmits() {
        val l = label()
        val b = control.addOrUpdateGenericBookmark(GenericBookmarkWithNotes(
            key = "test-key", bookInitials = "missing-test-document", ordinalStart = null, ordinalEnd = null,
            startOffset = null, endOffset = null, playbackSettings = null, new = true,
        ))
        val link = GenericBookmarkToLabel(bookmarkId = b.id, labelId = l.id, orderNumber = 3, indentLevel = 2)
        seen.clear()
        control.insertBookmarkToLabel(link)
        val emitted = seen.filterIsInstance<BookmarkChange.BookmarkToLabelUpserted>().single().bookmarkToLabel
        assertEquals(b.id, emitted.bookmarkId)
        assertEquals(l.id, emitted.labelId)
        val stored = control.getGenericBookmarkToLabel(b.id, l.id)!!
        assertEquals(3, stored.orderNumber)
        assertEquals(2, stored.indentLevel)
    }

    @Test fun syncEmitsInLabelDeleteStudyPadBookmarkOrder() {
        val l = label()
        val b = bookmark(labels = setOf(l.id))
        control.createStudyPadEntry(l.id, 0)
        val e = dao.studyPadTextEntriesByLabelId(l.id).single()
        val deletedBookmark = IdType()
        val deletedEntry = IdType()
        val entries = listOf(
            Triple("Label", l.id, LogEntryTypes.UPSERT),
            Triple("BibleBookmark", deletedBookmark, LogEntryTypes.DELETE),
            Triple("StudyPadTextEntry", deletedEntry, LogEntryTypes.DELETE),
            Triple("StudyPadTextEntry", e.id, LogEntryTypes.UPSERT),
            Triple("BibleBookmark", b.id, LogEntryTypes.UPSERT),
        ).map { (table, id, type) -> LogEntry(tableName = table, entityId1 = id,
            entityId2 = IdType.empty(), type = type, lastUpdated = 0L, sourceDevice = "other-device") }
        seen.clear()
        var emitterThread: Thread? = null
        val syncSubscription = DatabaseContainer.bookmarksSynced.subscribe { emitterThread = Thread.currentThread() }
        try {
            val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
                .map { it() }.single { it.category == SyncableDatabaseDefinition.BOOKMARKS }
            accessor._reactToUpdates!!.invoke(entries)
        } finally {
            syncSubscription.cancel()
        }
        assertSame(Thread.currentThread(), emitterThread)
        val labelIndex = seen.indexOfFirst { it is BookmarkChange.LabelUpserted }
        val deleteIndex = seen.indexOfFirst { it is BookmarkChange.BookmarksDeleted }
        val entryDeleteIndex = seen.indexOfFirst { it is BookmarkChange.StudyPadTextEntryDeleted }
        val orderIndex = seen.indexOfFirst { it is BookmarkChange.StudyPadOrder }
        val bookmarkIndex = seen.indexOfLast { it is BookmarkChange.BookmarksUpserted }
        assertTrue(labelIndex >= 0 && labelIndex < deleteIndex && deleteIndex < entryDeleteIndex && entryDeleteIndex < orderIndex && orderIndex < bookmarkIndex)
        assertEquals(listOf(listOf(deletedBookmark), emptyList()), seen.filterIsInstance<BookmarkChange.BookmarksDeleted>().map { it.bookmarkIds })
        assertEquals(deletedEntry, seen.filterIsInstance<BookmarkChange.StudyPadTextEntryDeleted>().single().studyPadTextEntryId)
        val orders = seen.filterIsInstance<BookmarkChange.StudyPadOrder>()
        assertEquals(e.id, orders.first().newStudyPadTextEntry!!.id)
        assertTrue(orders.size >= 2)
        assertTrue(orders.all { it.labelId == l.id })
        assertEquals(b.id, seen.filterIsInstance<BookmarkChange.BookmarksUpserted>().single().bookmarks.single().id)
    }
}
