package net.bible.android.control.bookmark

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.android.database.bookmarks.BookmarkEntities.EditActionMode
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.LabelType
import net.bible.android.database.bookmarks.isAddonId
import net.bible.service.addons.collections.AddonCollectionImporter
import net.bible.service.addons.collections.addonId
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.service.db.queryRows
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.db.writeStudyPadExportDb
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedlogic.platform.CollectionPackRef
import net.bible.test.DatabaseResetter.resetDatabase
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import okio.Path
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Add-on collection rows in [BookmarkControl] (#4162, Tasks 6-8): every write to them is refused; favourite and
 * StudyPad expand state are per device; an own label, StudyPad or note on a collection verse makes one user copy that
 * stands in for it; "Copy to my labels" makes a deep, synced copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class AddonCollectionBookmarkControlTest {
    private lateinit var control: BookmarkControl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seen = mutableListOf<BookmarkChange>()
    private val db get() = DatabaseContainer.instance.bookmarkDb
    private val dao get() = db.bookmarkDao()
    private val env get() = get<AppEnvironment>(AppEnvironment::class.java)
    private val files = mutableListOf<File>()
    private val kjv = Versifications.instance().getVersification("KJV")

    private val module = "Manna"
    private lateinit var srcLabel: IdType
    private lateinit var srcB1: IdType
    private lateinit var srcB2: IdType
    private lateinit var srcEntry: IdType
    private val l get() = addonId(module, srcLabel)
    private val b1 get() = addonId(module, srcB1)
    private val b2 get() = addonId(module, srcB2)
    private val entry get() = addonId(module, srcEntry)
    private lateinit var collection: Path

    private fun range(verse: Int): VerseRange = VerseRangeFactory.fromString(kjv, "Ps 119:$verse")
    private fun run(block: suspend () -> Unit) { runBlocking { block() } }

    @Before fun setUp() = run {
        control = BookmarkControl(mock(WindowControl::class.java), testAppSettings(), testCoreStrings(), OrderedLauncher(scope), net.bible.android.platform.androidSaxTextPolicy())
        control.changes.subscribe { seen += it }
        // The author's StudyPad "Comfort": Ps 119:1 (note), Ps 119:2, a text entry; exported, then removed again.
        val label = control.insertOrUpdateLabel(Label(new = true, name = "Comfort", type = LabelType.EXAMPLE))
        srcLabel = label.id
        srcB1 = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(1), null, true, null).apply { notes = "Fear not" }, setOf(label.id)).id
        srcB2 = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(2), null, true, null), setOf(label.id)).id
        srcEntry = control.createStudyPadEntryWithText(label.id, text = "Intro").id
        val file = File.createTempFile("collection-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        writeStudyPadExportDb(file.toOkioPath(), listOf(label), db, openExportDb = { DatabaseContainer.instance.getBookmarkDb(it) })
        collection = file.toOkioPath()
        control.deleteLabels(listOf(label.id), deleteOrphanedBookmarks = true)
        install()
        // Sync set up on this device: every own write is logged from here on.
        val accessor = DatabaseContainer.databaseAccessorsByCategory[SyncableDatabaseDefinition.BOOKMARKS]!!
        dropTriggers(accessor)
        createTriggers(accessor)
        db.useWriterConnectionMarked { it.exec("DELETE FROM LogEntry") }
        seen.clear()
    }

    @After fun tearDown() {
        scope.cancel()
        files.forEach { it.delete() }
        resetDatabase()
    }

    private suspend fun install(file: Path = collection, version: String = "1.0") =
        AddonCollectionImporter({ db }, env).replace(CollectionPackRef(module, "Manna verses", version, file), "hash-$version-${file.name}")

    private suspend fun rows(sql: String): List<List<String?>> = db.useWriterConnectionMarked { c ->
        c.queryRows(sql) { st -> (0 until st.getColumnCount()).map { if (st.isNull(it)) null else st.getText(it) } }
    }

    /** Every row of the synced bookmark tables, as text. */
    private suspend fun snapshot(): Map<String, List<List<String?>>> =
        SyncableDatabaseDefinition.BOOKMARKS.tables.associate { it.tableName to rows("SELECT * FROM ${it.tableName} ORDER BY 1, 2").map { r -> r.map { v -> v } } }

    private suspend fun log() = db.syncDao().allLogEntries()

    private suspend fun userBookmarksOn(verse: Int) = dao.bookmarksForVerseRange(range(verse)).filterNot { it.id.isAddonId }

    private suspend fun ownLabel(name: String = "Mine") = control.insertOrUpdateLabel(Label(new = true, name = name))

    @Test
    fun everyWriteOnAddonRowsIsRefusedAndChangesNothing() = run {
        val own = ownLabel()
        val ownBookmark = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(5), null, true, null), setOf(own.id))
        db.useWriterConnectionMarked { it.exec("DELETE FROM LogEntry") }
        val before = snapshot()
        val bookmark = { runBlocking { dao.bibleBookmarkById(b1)!! } }
        val label = { runBlocking { dao.labelById(l)!! } }
        val link = { runBlocking { dao.getBibleBookmarkToLabel(b1, l)!! } }
        val writes: List<Pair<String, suspend () -> Unit>> = listOf(
            "deleteBookmark" to { control.deleteBookmark(bookmark()) },
            "deleteBibleBookmarksById" to { control.deleteBibleBookmarksById(listOf(ownBookmark.id, b1)) },
            "rename label" to { control.insertOrUpdateLabel(label().apply { name = "Renamed" }) },
            "label colour" to { control.insertOrUpdateLabel(label().apply { color = 123 }) },
            "deleteLabel" to { control.deleteLabel(label()) },
            "deleteLabels" to { control.deleteLabels(listOf(own.id, l)) },
            "changeLabelsForBookmark" to { control.changeLabelsForBookmark(bookmark(), listOf(own.id)) },
            "changeLabelsForBookmarks with add-on label" to { control.changeLabelsForBookmarks(listOf(ownBookmark), listOf(l)) },
            "updateStudyPadTextEntry" to { control.updateStudyPadTextEntry(dao.studyPadTextEntryById(entry)!!.studyPadTextEntryEntity.apply { indentLevel = 2 }) },
            "deleteStudyPadTextEntry" to { control.deleteStudyPadTextEntry(entry) },
            "updateStudyPadTextEntryText" to { control.updateStudyPadTextEntryText(entry, "changed") },
            "createStudyPadEntry" to { control.createStudyPadEntry(l, 0) },
            "createStudyPadEntryWithText" to { control.createStudyPadEntryWithText(l, text = "x") },
            "updateOrderNumbers" to { control.updateOrderNumbers(l, listOf(link().apply { orderNumber = 9 }), emptyList(), emptyList()) },
            "setAsPrimaryLabelForBible" to { control.setAsPrimaryLabelForBible(b1, own.id) },
            "updateBookmarkEditAction" to { control.updateBookmarkEditAction(b1, EditAction(EditActionMode.APPEND, null)) },
            "update bookmark fields" to { control.addOrUpdateBibleBookmark(bookmark().apply { wholeVerse = false }) },
            "remove the add-on label (verse from collection)" to { control.removeBibleBookmarkLabel(b1, l) },
            "toggle the add-on label" to { control.toggleBookmarkLabel(bookmark(), l.toString()) },
            "add own bookmark to add-on label" to { control.addOrUpdateBibleBookmark(ownBookmark, setOf(own.id, l)) },
            "insert own bookmark into add-on StudyPad" to { control.insertBookmarkToLabel(BibleBookmarkToLabel(ownBookmark.id, l)) },
            "add-on link indent" to { control.updateBookmarkToLabel(link().apply { indentLevel = 3 }) },
        )
        for ((name, write) in writes) {
            val e = runCatching { write() }.exceptionOrNull()
            assertTrue("$name: expected AddonReadOnlyException, got $e", e is AddonReadOnlyException)
            assertEquals(name, module, (e as AddonReadOnlyException).moduleInitials.ifEmpty { module })
            assertEquals("$name changed the database", before, snapshot())
        }
        assertEquals(emptyList<Any>(), log())
    }

    @Test
    fun favouriteOfAddonLabelIsPerDeviceAndSurvivesReplace() = run {
        val label = control.labelById(l)!!
        assertFalse(label.favourite)
        control.insertOrUpdateLabel(label.copy(favourite = true))
        assertTrue(control.labelById(l)!!.favourite)
        assertEquals(listOf(l), control.favouriteLabels().map { it.id })
        assertTrue(control.allLabels().single { it.id == l }.favourite)
        assertEquals("the label row itself is untouched", listOf(listOf("0")), rows("SELECT favourite FROM Label WHERE id = X'${l.toHex()}'"))
        assertTrue(seen.filterIsInstance<BookmarkChange.LabelUpserted>().single { it.label.id == l }.label.favourite)
        install(version = "2.0")
        assertEquals(listOf(l), control.favouriteLabels().map { it.id })
        control.insertOrUpdateLabel(control.labelById(l)!!.copy(favourite = false))
        assertTrue(control.favouriteLabels().isEmpty())
        assertTrue(log().none { it.entityId1.isAddonId })
    }

    @Test
    fun studyPadExpandStateOfAddonLinkIsPerDevice() = run {
        val link = dao.getBibleBookmarkToLabel(b1, l)!!
        control.updateBookmarkToLabel(link.copy(expandContent = !link.expandContent))
        val shown = control.getBibleBookmarksWithLabel(dao.labelById(l)!!, addData = true).single { it.id == b1 }
        assertEquals(!link.expandContent, shown.bookmarkToLabels!!.single { it.labelId == l }.expandContent)
        assertEquals("row unchanged", link.expandContent, dao.getBibleBookmarkToLabel(b1, l)!!.expandContent)
        assertTrue(log().none { it.entityId1.isAddonId })
    }

    @Test
    fun autoAssignedAddonLabelIsDroppedFromANewBookmark() = run {
        val own = ownLabel()
        val saved = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(7), null, true, null), setOf(own.id, l))
        assertEquals(listOf(own.id), dao.labelsForBookmark(saved.id).map { it.id })
    }

    @Test
    fun ownLabelOnCollectionVerseMakesOneCopyThatStandsInForIt() = run {
        val own = ownLabel()
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        assertEquals(setOf(l, own.id), dao.labelsForBookmark(copy.id).map { it.id }.toSet())
        assertEquals("Fear not", copy.notes)
        assertEquals(dao.getBibleBookmarkToLabel(b1, l)!!.orderNumber, dao.getBibleBookmarkToLabel(copy.id, l)!!.orderNumber)
        assertTrue(seen.filterIsInstance<BookmarkChange.BookmarksDeleted>().any { b1 in it.bookmarkIds })

        // A second action reuses it.
        control.saveBibleBookmarkNote(b1, "My own note")
        assertEquals(copy.id, userBookmarksOn(1).single().id)
        assertEquals("My own note", dao.bibleBookmarkById(copy.id)!!.notes)
        assertEquals("the collection is unchanged", "Fear not", dao.bibleBookmarkById(b1)!!.notes)

        // The original is hidden everywhere it would be shown; the other verse is not.
        val inText = control.bookmarksForVerseRange(VerseRangeFactory.fromString(kjv, "Ps 119:1-3")).map { it.id }.toSet()
        assertEquals(setOf(copy.id, b2), inText)
        val inLabel = control.getBibleBookmarksWithLabel(dao.labelById(l)!!, addData = true).map { it.id }.toSet()
        assertEquals(setOf(copy.id, b2), inLabel)

        // The copy and its link to the add-on label are synced; no add-on row is logged.
        val logged = log()
        assertTrue(logged.any { it.tableName == "BibleBookmarkToLabel" && it.entityId1 == copy.id && it.entityId2 == l })
        assertTrue(logged.none { it.entityId1.isAddonId })
    }

    @Test
    fun ownBookmarkOnTheSameRangeWithoutTheLabelHidesNothing() = run {
        val own = ownLabel()
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(1), null, true, null), setOf(own.id))
        assertEquals(2, control.bookmarksForVerseRange(range(1)).size)
        assertTrue(control.getBibleBookmarksWithLabel(dao.labelById(l)!!).any { it.id == b1 })
    }

    @Test
    fun removingTheLabelFromTheCopyOrDeletingItBringsTheOriginalBack() = run {
        val own = ownLabel()
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        seen.clear()
        control.removeBibleBookmarkLabel(copy.id, l)
        assertEquals(setOf(b1, copy.id), control.bookmarksForVerseRange(range(1)).map { it.id }.toSet())
        assertTrue(seen.filterIsInstance<BookmarkChange.BookmarksUpserted>().any { u -> u.bookmarks.any { it.id == b1 } })

        // Now a fresh copy (the old one no longer carries the label), then delete it.
        control.saveBibleBookmarkNote(b1, "again")
        val second = userBookmarksOn(1).single { dao.labelsForBookmark(it.id).any { label -> label.id == l } }
        assertNotEquals(copy.id, second.id)
        control.deleteBookmark(dao.bibleBookmarkById(second.id)!!)
        assertTrue(control.bookmarksForVerseRange(range(1)).any { it.id == b1 })
    }

    @Test
    fun moduleRemovalKeepsTheCopyAndDropsOnlyItsLink() = run {
        val own = ownLabel()
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        db.useWriterConnectionMarked { it.exec("DELETE FROM LogEntry") }
        AddonCollectionImporter({ db }, env).remove(module)
        assertEquals(listOf(own.id), dao.labelsForBookmark(copy.id).map { it.id })
        assertEquals("My own bookmark stays", copy.id, dao.bibleBookmarkById(copy.id)!!.id)
        assertEquals(emptyList<Any>(), log())
    }

    /** Review Focus 5: an update that drops a verse the user copied. */
    @Test
    fun moduleUpdateDroppingACopiedVerseKeepsTheCopyLinked() = run {
        val own = ownLabel()
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        val v2 = File.createTempFile("collection-v2-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        File(collection.toString()).copyTo(v2, overwrite = true)
        DatabaseContainer.instance.getBookmarkDb(v2.absolutePath).also { f ->
            try { f.useWriterConnectionMarked { it.exec("DELETE FROM BibleBookmark WHERE id = X'${srcB1.toHex()}'") } } finally { f.close() }
        }
        install(v2.toOkioPath(), "2.0")
        assertEquals(null, dao.bibleBookmarkById(b1))
        assertEquals(setOf(l, own.id), dao.labelsForBookmark(copy.id).map { it.id }.toSet())
        assertEquals(setOf(copy.id, b2), control.getBibleBookmarksWithLabel(dao.labelById(l)!!).map { it.id }.toSet())
        assertEquals(listOf(b2), control.bookmarksForVerseRange(range(2)).map { it.id })
    }

    /** Review Focus 4: a drag of a collection verse into an own StudyPad goes to the copy. */
    @Test
    fun insertingAnAddonBookmarkIntoAnOwnStudyPadUsesTheCopy() = run {
        val pad = ownLabel("My StudyPad")
        control.insertBookmarkToLabel(BibleBookmarkToLabel(b1, pad.id, orderNumber = 0))
        val copy = userBookmarksOn(1).single()
        assertEquals(setOf(l, pad.id), dao.labelsForBookmark(copy.id).map { it.id }.toSet())
        assertEquals(null, dao.getBibleBookmarkToLabel(b1, pad.id))
    }

    @Test
    fun copyToMyLabelsMakesADeepSyncedCopy() = run {
        ownLabel("Comfort")
        val copy = control.copyAddonLabel(l)
        assertEquals("Comfort (copy)", copy.name)
        assertFalse(copy.id.isAddonId)
        assertEquals(LabelType.EXAMPLE, copy.type)
        val bookmarks = control.getBibleBookmarksWithLabel(copy, addData = true)
        assertEquals(setOf(range(1).start.ordinal, range(2).start.ordinal), bookmarks.map { it.kjvOrdinalStart }.toSet())
        assertTrue(bookmarks.none { it.id.isAddonId })
        assertEquals("Fear not", bookmarks.single { it.kjvOrdinalStart == range(1).start.ordinal }.notes)
        val sourceOrder = dao.getBookmarkToLabelsForLabel(l).associate { dao.bibleBookmarkById(it.bookmarkId)!!.kjvOrdinalStart to it.orderNumber }
        val copyOrder = dao.getBookmarkToLabelsForLabel(copy.id).associate { dao.bibleBookmarkById(it.bookmarkId)!!.kjvOrdinalStart to it.orderNumber }
        assertEquals(sourceOrder, copyOrder)
        val entries = dao.studyPadTextEntriesByLabelId(copy.id)
        assertEquals(listOf("Intro"), entries.map { it.text })
        assertEquals(dao.studyPadTextEntryById(entry)!!.orderNumber, entries.single().orderNumber)
        // Everything new is logged for sync; the add-on label is untouched and still has its rows.
        val logged = log()
        assertTrue(logged.any { it.tableName == "Label" && it.entityId1 == copy.id })
        assertEquals(2, logged.count { it.tableName == "BibleBookmark" })
        assertTrue(logged.any { it.tableName == "StudyPadTextEntry" && it.entityId1 == entries.single().id })
        assertTrue(logged.none { it.entityId1.isAddonId })
        assertEquals("Comfort", dao.labelById(l)!!.name)
        assertEquals(2, dao.getBookmarkToLabelsForLabel(l).size)
        // The copy is an ordinary label: it can be edited.
        control.insertOrUpdateLabel(copy.apply { name = "Mine now" })
        Unit
    }
}
