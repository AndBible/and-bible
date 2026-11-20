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
import net.bible.android.control.page.ClientBibleBookmark
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.view.activity.bookmark.assignableLabelItems
import net.bible.android.database.bookmarks.ADDON_ROW_NO_ID2_SQL
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
    fun unchangedLabelsOrNoteMakeNoCopy() = run {
        val b = dao.bibleBookmarkById(b1)!!
        control.addOrUpdateBibleBookmark(b, setOf(l))
        control.saveBibleBookmarkNote(b1, "Fear not")
        control.addOrUpdateBibleBookmark(b.apply { notes = "Fear not" }, null, updateNotes = true)
        assertEquals(emptyList<Any>(), userBookmarksOn(1))
        assertEquals(emptyList<Any>(), log())
    }

    /** A verse in two add-on labels: removing one of them from the copy brings the original back in that label only. */
    @Test
    fun hidingIsPerLabel() = run {
        val l2 = addonId(module, IdType("99999999-0000-4000-8000-000000000001"))
        db.useWriterConnectionMarked { c ->
            c.exec("INSERT INTO Label (id, name) VALUES (X'${l2.toHex()}', 'Second')")
            c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (X'${b1.toHex()}', X'${l2.toHex()}')")
            c.exec("INSERT INTO AddonCollectionRow (moduleInitials, tableName, id1, id2) VALUES ('$module', 'Label', X'${l2.toHex()}', $ADDON_ROW_NO_ID2_SQL)")
            c.exec("INSERT INTO AddonCollectionRow (moduleInitials, tableName, id1, id2) VALUES ('$module', 'BibleBookmarkToLabel', X'${b1.toHex()}', X'${l2.toHex()}')")
        }
        val own = ownLabel()
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        assertEquals(setOf(l, l2, own.id), dao.labelsForBookmark(copy.id).map { it.id }.toSet())
        control.removeBibleBookmarkLabel(copy.id, l2)
        assertTrue("back in the second label", control.getBibleBookmarksWithLabel(dao.labelById(l2)!!).any { it.id == b1 })
        assertTrue("still stood in for in the first", control.getBibleBookmarksWithLabel(dao.labelById(l)!!).none { it.id == b1 })
        assertTrue("shown in the text: not all its labels are on the copy",
            control.bookmarksForVerseRange(range(1)).any { it.id == b1 })

        // Logic review IMPORTANT 1: an edit on the re-shown original (here: another own label, through the label list
        // of the second StudyPad) goes to the copy as it is now, keeping its own label, and is not refused.
        val own2 = ownLabel("Mine 2")
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own2.id.toString())
        assertEquals(copy.id, userBookmarksOn(1).single().id)
        assertEquals(setOf(l, own.id, own2.id), dao.labelsForBookmark(copy.id).map { it.id }.toSet())
        control.addOrUpdateBibleBookmark(dao.bibleBookmarkById(b1)!!.apply { notes = "edited" }, setOf(l, l2), updateNotes = true)
        assertEquals("edited", dao.bibleBookmarkById(copy.id)!!.notes)
        assertEquals(setOf(l, own.id, own2.id), dao.labelsForBookmark(copy.id).map { it.id }.toSet())
        assertTrue(log().none { it.entityId1.isAddonId })
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

    /**
     * Sync review IMPORTANT 1: the module goes (briefly, or on a device that never had it): the copy keeps its link to
     * the add-on label and its primary label, untouched and unlogged; it can be edited (no foreign-key failure, no
     * NULL primary label logged), the views never see the missing label, and the module's return puts it back.
     */
    @Test
    fun moduleRemovalKeepsTheCopyWithItsLinkAndPrimaryLabel() = run {
        val own = ownLabel()
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        db.useWriterConnectionMarked { it.exec("UPDATE BibleBookmark SET primaryLabelId = X'${l.toHex()}' WHERE id = X'${copy.id.toHex()}'") }
        db.useWriterConnectionMarked { it.exec("DELETE FROM LogEntry") }
        AddonCollectionImporter({ db }, env).run { remove(module); sweep() }
        assertEquals(emptyList<Any>(), log())
        assertEquals(listOf(listOf(l.toHex())), rows("SELECT lower(hex(labelId)) FROM BibleBookmarkToLabel WHERE bookmarkId = X'${copy.id.toHex()}' AND labelId = X'${l.toHex()}'"))
        assertEquals(l, dao.bibleBookmarkById(copy.id)!!.primaryLabelId)

        // Shown without the missing label (BibleView looks every label id up).
        val shown = control.bookmarksForVerseRange(range(1), withLabels = true).single()
        assertEquals(listOf(own.id), shown.labelIds)
        assertEquals("null", net.bible.android.control.page.ClientBibleBookmark(shown).asHashMap["primaryLabelId"])

        // Edits work and log the row as it is: the primary label is not lost.
        control.saveBibleBookmarkNote(copy.id, "edited")
        val other = ownLabel("Other")
        control.addOrUpdateBibleBookmark(dao.bibleBookmarkById(copy.id)!!, setOf(own.id, other.id))
        assertEquals(l, dao.bibleBookmarkById(copy.id)!!.primaryLabelId)
        assertEquals(setOf(l.toHex(), own.id.toHex(), other.id.toHex()),
            rows("SELECT lower(hex(labelId)) FROM BibleBookmarkToLabel WHERE bookmarkId = X'${copy.id.toHex()}'").map { it[0] }.toSet())
        assertTrue(log().any { it.tableName == "BibleBookmark" && it.entityId1 == copy.id })
        assertTrue(log().none { it.entityId1.isAddonId })
        assertEquals("foreign keys are back on", listOf(listOf("1")), rows("PRAGMA foreign_keys"))

        // The module is back: so is the copy in its label, standing in for the original.
        install(version = "2.0")
        assertEquals(setOf(copy.id, b2), control.getBibleBookmarksWithLabel(dao.labelById(l)!!).map { it.id }.toSet())
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
        // Per-device state and the author's AI provenance: the expand state is carried, the prompt ids are not.
        val link2 = dao.getBibleBookmarkToLabel(b2, l)!!
        control.updateBookmarkToLabel(link2.copy(expandContent = !link2.expandContent))
        val prompt = IdType()
        db.useWriterConnectionMarked {
            it.exec("UPDATE BibleBookmark SET sourcePromptId = X'${prompt.toHex()}' WHERE id = X'${b1.toHex()}'")
            it.exec("UPDATE BibleBookmarkNotes SET sourcePromptId = X'${prompt.toHex()}' WHERE bookmarkId = X'${b1.toHex()}'")
        }
        db.useWriterConnectionMarked { it.exec("DELETE FROM LogEntry") }
        // The add-on label's own name counts as taken: two labels of one name would be confusing.
        val copy = control.copyAddonLabel(l)
        assertEquals("Comfort (copy)", copy.name)
        assertEquals("Comfort (copy) (copy)", control.copyAddonLabel(l).name)
        val copied = control.getBibleBookmarksWithLabel(copy, addData = true)
        assertTrue(copied.all { it.sourcePromptId == null && it.notesSourcePromptId == null })
        assertEquals(!link2.expandContent, dao.getBookmarkToLabelsForLabel(copy.id).single { dao.bibleBookmarkById(it.bookmarkId)!!.kjvOrdinalStart == range(2).start.ordinal }.expandContent)
        val ownLabel = ownLabel("Own")
        assertTrue(runCatching { control.copyAddonLabel(ownLabel.id) }.exceptionOrNull() is IllegalArgumentException)
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
        assertEquals(4, logged.count { it.tableName == "BibleBookmark" })
        assertTrue(logged.any { it.tableName == "StudyPadTextEntry" && it.entityId1 == entries.single().id })
        assertTrue(logged.none { it.entityId1.isAddonId })
        assertEquals("Comfort", dao.labelById(l)!!.name)
        assertEquals(2, dao.getBookmarkToLabelsForLabel(l).size)
        assertEquals(link2.expandContent, dao.getBibleBookmarkToLabel(b2, l)!!.expandContent)
        // The copy is an ordinary label: it can be edited.
        control.insertOrUpdateLabel(copy.apply { name = "Mine now" })
        Unit
    }

    /** The per-device expand state also applies when the whole add-on StudyPad is sent again (a module update, a sync). */
    @Test
    fun studyPadOrderOfAnAddonStudyPadCarriesTheExpandState() = run {
        val link = dao.getBibleBookmarkToLabel(b1, l)!!
        control.updateBookmarkToLabel(link.copy(expandContent = !link.expandContent))
        seen.clear()
        control.notifyAddonRowsChanged(listOf(net.bible.android.database.LogEntry("Label", l, IdType.empty(), net.bible.android.database.LogEntryTypes.UPSERT, 0, "addon:$module")))
        val deadline = System.currentTimeMillis() + 10_000
        while (seen.filterIsInstance<BookmarkChange.StudyPadOrder>().none { it.labelId == l } && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val order = seen.filterIsInstance<BookmarkChange.StudyPadOrder>().single { it.labelId == l }
        assertEquals(!link.expandContent, order.bookmarkToLabelsOrderChanged.single { it.bookmarkId == b1 }.expandContent)
    }

    /** A primary label must be one of the bookmark's labels when it is an add-on label (the add-on side is read-only). */
    @Test
    fun primaryLabelOfAnOwnBookmarkCannotBeAnAddonLabelItDoesNotCarry() = run {
        val own = ownLabel()
        val bookmark = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(9), null, true, null), setOf(own.id))
        db.useWriterConnectionMarked { it.exec("DELETE FROM LogEntry") }
        val e = runCatching { control.setAsPrimaryLabelForBible(bookmark.id, l) }.exceptionOrNull()
        assertTrue("$e", e is AddonReadOnlyException)
        assertEquals(own.id, dao.bibleBookmarkById(bookmark.id)!!.primaryLabelId ?: own.id)
        assertEquals(emptyList<Any>(), log())
        // On the copy, which carries it, it is fine.
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, own.id.toString())
        val copy = userBookmarksOn(1).single()
        control.setAsPrimaryLabelForBible(copy.id, own.id)
        control.setAsPrimaryLabelForBible(copy.id, l)
        assertEquals(l, dao.bibleBookmarkById(copy.id)!!.primaryLabelId)
    }

    /** The per-bookmark copy keeps the original's paragraph break in the add-on StudyPad. */
    @Test
    fun perBookmarkCopyCarriesTheParagraphBreak() = run {
        val pb = control.paragraphBreakLabel()
        db.useWriterConnectionMarked { c ->
            c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber) VALUES (X'${b1.toHex()}', X'${pb.id.toHex()}', 4)")
            c.exec("INSERT INTO AddonCollectionRow (moduleInitials, tableName, id1, id2) VALUES ('$module', 'BibleBookmarkToLabel', X'${b1.toHex()}', X'${pb.id.toHex()}')")
        }
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, ownLabel().id.toString())
        val copy = userBookmarksOn(1).single()
        assertEquals(4, dao.getBibleBookmarkToLabel(copy.id, pb.id)?.orderNumber)
    }

    /** The AI tool lists a label's bookmarks as the label's list does: a stood-in original is left out. */
    @Test
    fun getBookmarksWithLabelToolLeavesOutStoodInOriginals() = run {
        control.toggleBookmarkLabel(dao.bibleBookmarkById(b1)!!, ownLabel().id.toString())
        val copy = userBookmarksOn(1).single()
        val args = kotlinx.serialization.json.buildJsonObject { put("labelId", kotlinx.serialization.json.JsonPrimitive(l.toString())) }
        val result = net.bible.service.llm.tools.read.GetBookmarksWithLabelTool.execute(args, net.bible.service.llm.agent.AgentContext(promptId = IdType()))
        val data = (result as net.bible.service.llm.tools.ToolResult.Success).data as net.bible.service.llm.tools.read.GetBookmarksWithLabelTool.Result
        assertEquals(setOf(copy.id, b2), data.bookmarks.map { it.id }.toSet())
    }

    /** The app's wiring: the importer creates a missing paragraph-break label through BookmarkControl (its lock, its event). */
    @Test
    fun importThroughBookmarkControlCreatesTheParagraphBreakLabel() = run {
        db.useWriterConnectionMarked { it.exec("DELETE FROM Label WHERE id = X'${net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID.toHex()}'") }
        val v2 = File.createTempFile("collection-pb-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
        File(collection.toString()).copyTo(v2, overwrite = true)
        DatabaseContainer.instance.getBookmarkDb(v2.absolutePath).also { f ->
            try {
                f.useWriterConnectionMarked {
                    it.exec("INSERT INTO Label (id, name) VALUES (X'AAAAAAAAAAAA4AAA8AAAAAAAAAAAAAAA', '${net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME}')")
                    it.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (X'${srcB1.toHex()}', X'AAAAAAAAAAAA4AAA8AAAAAAAAAAAAAAA')")
                }
            } finally { f.close() }
        }
        seen.clear()
        AddonCollectionImporter({ db }, env, ensureSpecialLabels = { control.ensureSpecialLabels(it) })
            .replace(CollectionPackRef(module, "Manna verses", "2.0", v2.toOkioPath()), "hash-pb")
        val pb = net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
        assertEquals(net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME, dao.labelById(pb)?.name)
        assertTrue(dao.getBibleBookmarkToLabel(b1, pb) != null)
        assertTrue(seen.filterIsInstance<BookmarkChange.LabelUpserted>().any { it.label.id == pb })
        assertTrue(log().any { it.tableName == "Label" && it.entityId1 == pb })
        assertTrue(log().none { it.entityId1.isAddonId })
    }

    // --- UI contract (Task 9) ---------------------------------------------------------------------------------------

    @Test
    fun addonLabelsAreListedWithTheirModuleName() = run {
        val own = ownLabel()
        assertEquals(mapOf(l to "Manna"), control.addonLabelModuleNames()) // not an installed book: the initials
        assertFalse(own.id in control.addonLabelModuleNames())
        assertEquals("Manna", control.addonModuleName("Manna"))
        // assignableLabels keeps the add-on label (a copy keeps L) and the label manager marks it from the module map.
        val items = control.assignableLabels().assignableLabelItems(control.addonLabelModuleNames())
        assertEquals("Manna", items.single { it.id == l.toString() }.addonModuleName)
        assertEquals(null, items.single { it.id == own.id.toString() }.addonModuleName)
    }

    @Test
    fun readerObjectsMarkAddonRowsAndOwnRowsAreUnmarked() = run {
        val own = ownLabel()
        val ownBookmark = control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(range(5), null, true, null), setOf(own.id))
        assertEquals("", ClientBookmarkLabel(dao.labelById(l)!!).addonModule)
        assertEquals(null, ClientBookmarkLabel(own).addonModule)
        val addonBookmark = control.getBibleBookmarksWithLabel(dao.labelById(l)!!, addData = true).single { it.id == b1 }
        val ownLoaded = control.getBibleBookmarksWithLabel(own, addData = true).single { it.id == ownBookmark.id }
        assertNotEquals("null", ClientBibleBookmark(addonBookmark).asHashMap["addonModule"])
        assertEquals("null", ClientBibleBookmark(ownLoaded).asHashMap["addonModule"])
    }

    @Test
    fun aRefusedJsWriteToAddonContentToastsInsteadOfFailingSilently() = run {
        val toasts = mutableListOf<String?>()
        val sub = net.bible.android.control.event.UserMessages.messages.subscribe { (it as? net.bible.android.control.event.UserMessage.Toast)?.let { t -> toasts += t.text } }
        try {
            val actions = BookmarkJsActions(OrderedLauncher(scope), control)
            actions.deleteBibleBookmarks("w", listOf(b1)).join()
            assertEquals(1, toasts.size)
            assertTrue(toasts.single()!!.contains("Manna"))
            assertTrue("the bookmark is still there", dao.bibleBookmarkById(b1) != null)
        } finally { sub.cancel() }
    }

    private fun widgetSources() = net.bible.android.control.widget.ControlVerseWidgetSources(
        control, mock(WindowControl::class.java), org.koin.core.context.GlobalContext.get().get<net.bible.android.control.readingplan.ReadingPlanControl>(),
        testCoreStrings(), net.bible.android.platform.androidSaxTextPolicy(), net.bible.android.platform.AndroidLocalCalendar(),
        mock(net.bible.android.control.widget.WidgetStrings::class.java),
    )

    @Test
    fun addonLabelsAreVerseWidgetSourcesShownWithTheirModuleAndAnOwnCopyIsNotDoubled() = run {
        val sources = widgetSources()
        assertEquals("Comfort · Manna", sources.labels().single { it.first == l }.second)
        val before = sources.label(l)!!
        assertEquals("Comfort · Manna", before.name)
        assertEquals(2, before.refs.size)

        // An own bookmark of exactly Ps 119:1 that keeps the collection label stands in for the original (same verse, once).
        control.ownCopyOf(b1)
        val after = sources.label(l)!!
        assertEquals(2, after.refs.size)
    }

    @Test
    fun savingTheLabelsOfAnOwnCopyKeepsItsAddonLabelAndFavouriteShowsInTheAssignableList() = run {
        val copy = control.ownCopyOf(b1)
        val own = ownLabel()
        control.changeLabelsForBookmarks(listOf(copy), listOf(l, own.id)) // the chooser keeps L ticked: not a new add-on label
        assertTrue(dao.labelsForBookmark(dao.bibleBookmarkById(copy.id)!!).map { it.id }.containsAll(listOf(l, own.id)))
        control.insertOrUpdateLabel(control.labelById(l)!!.copy(favourite = true))
        assertTrue(control.assignableLabels().single { it.id == l }.favourite)
    }
}
