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
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.android.database.bookmarks.BookmarkEntities.EditActionMode
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.LabelType
import net.bible.android.control.page.ClientBibleBookmark
import net.bible.android.control.page.ClientBookmarkLabel
import net.bible.android.view.activity.bookmark.assignableLabelItems
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
 * Add-on collection rows in [BookmarkControl] (#4162): strictly read-only. Every write to them, and every new
 * association with them (a label, note, StudyPad entry or primary label on an add-on bookmark; an add-on label on an
 * own bookmark), is refused and leaves no user row referencing an add-on id; favourite and StudyPad expand state are
 * per device; "Bookmark this verse" makes an ordinary own bookmark beside the collection verse; "Copy to my labels"
 * makes a deep, synced copy.
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
            // No new association of any kind with an add-on row.
            "own label on an add-on bookmark" to { control.addOrUpdateBibleBookmark(bookmark(), setOf(l, own.id)) },
            "own label on an add-on bookmark (toggle)" to { control.toggleBookmarkLabel(bookmark(), own.id.toString()) },
            "own labels on add-on bookmarks (batch)" to { control.changeLabelsForBookmarks(listOf(bookmark()), listOf(l, own.id)) },
            "note on an add-on bookmark" to { control.saveBibleBookmarkNote(b1, "My thoughts") },
            "note removed from an add-on bookmark" to { control.saveBibleBookmarkNote(b1, null) },
            "note through addOrUpdate" to { control.addOrUpdateBibleBookmark(bookmark().apply { notes = "x" }, updateNotes = true) },
            "unchanged add-on bookmark saved" to { control.addOrUpdateBibleBookmark(bookmark(), setOf(l)) },
            "add-on bookmark into an own StudyPad" to { control.insertBookmarkToLabel(BibleBookmarkToLabel(b1, own.id)) },
            "add-on primary label on an own bookmark" to { control.setAsPrimaryLabelForBible(ownBookmark.id, l) },
            "add-on primary label through addOrUpdate" to { control.addOrUpdateBibleBookmark(ownBookmark.copy(primaryLabelId = l)) },
            "add-on label on an own bookmark (batch)" to { control.changeLabelsForBookmark(ownBookmark, listOf(own.id, l)) },
            "own bookmark into an add-on StudyPad" to { control.insertBookmarkToLabel(BibleBookmarkToLabel(ownBookmark.id, l)) },
            "custom icon" to { control.addOrUpdateBibleBookmark(bookmark().apply { customIcon = "heart" }) },
        )
        for ((name, write) in writes) {
            val e = runCatching { write() }.exceptionOrNull()
            assertTrue("$name: expected AddonReadOnlyException, got $e", e is AddonReadOnlyException)
            assertEquals(name, module, (e as AddonReadOnlyException).moduleInitials.ifEmpty { module })
            assertEquals("$name changed the database", before, snapshot())
        }
        assertEquals(emptyList<Any>(), log())
        assertNoUserRowReferencesAddonIds()
    }

    /** The sync invariant: add-on rows never leave the device and no user row references them. */
    private suspend fun assertNoUserRowReferencesAddonIds() {
        fun addon(column: String) = net.bible.android.database.bookmarks.isAddonIdSql(column)
        fun own(column: String) = "NOT (${addon(column)})"
        val queries = listOf(
            "SELECT bookmarkId FROM BibleBookmarkToLabel WHERE ${own("bookmarkId")} AND ${addon("labelId")}",
            "SELECT bookmarkId FROM GenericBookmarkToLabel WHERE ${own("bookmarkId")} AND ${addon("labelId")}",
            "SELECT id FROM BibleBookmark WHERE ${own("id")} AND primaryLabelId IS NOT NULL AND ${addon("primaryLabelId")}",
            "SELECT id FROM GenericBookmark WHERE ${own("id")} AND primaryLabelId IS NOT NULL AND ${addon("primaryLabelId")}",
            "SELECT id FROM StudyPadTextEntry WHERE ${own("id")} AND ${addon("labelId")}",
            "SELECT labelId FROM BibleBookmarkToLabel WHERE ${addon("bookmarkId")} AND ${own("labelId")} AND labelId != X'${net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID.toHex()}' AND labelId != X'${net.bible.android.database.bookmarks.UNLABELED_LABEL_ID.toHex()}'",
        )
        for (q in queries) assertEquals(q, emptyList<List<String?>>(), rows(q))
        assertTrue(log().none { it.entityId1.isAddonId || it.entityId2.isAddonId })
    }

    /** "Bookmark this verse": an ordinary own bookmark of the same range, shown beside the untouched collection verse. */
    @Test
    fun bookmarkThisVerseMakesAnOwnBookmarkBesideTheCollectionVerse() = run {
        db.useWriterConnectionMarked { it.exec("UPDATE BibleBookmark SET customIcon = 'heart' WHERE id = X'${b1.toHex()}'") }
        val before = dao.bibleBookmarkById(b1)!!
        val own = ownLabel()
        val made = control.newOwnBookmarkLike(b1) as BibleBookmarkWithNotes
        assertTrue(made.new)
        assertFalse(made.id.isAddonId)
        val saved = control.addOrUpdateBibleBookmark(made, setOf(own.id, l)) // add-on labels of auto-assign are dropped
        val loaded = dao.bibleBookmarkById(saved.id)!!
        assertEquals(before.kjvOrdinalStart, loaded.kjvOrdinalStart)
        assertEquals(before.kjvOrdinalEnd, loaded.kjvOrdinalEnd)
        assertEquals(before.ordinalStart, loaded.ordinalStart)
        assertEquals(before.ordinalEnd, loaded.ordinalEnd)
        assertEquals(before.v11n, loaded.v11n)
        assertEquals(before.book?.initials, loaded.book?.initials)
        assertEquals(before.startOffset, loaded.startOffset)
        assertEquals(before.endOffset, loaded.endOffset)
        assertEquals(before.wholeVerse, loaded.wholeVerse)
        assertEquals(null, loaded.notes)
        assertEquals(null, loaded.customIcon)
        assertEquals(listOf(own.id), dao.labelsForBookmark(saved.id).map { it.id })
        // Both are shown on the verse; the collection verse is untouched; the new bookmark is the user's (logged).
        assertEquals(setOf(b1, saved.id), control.bookmarksForVerseRange(range(1)).map { it.id }.toSet())
        assertEquals(before.copy(), dao.bibleBookmarkById(b1)!!.copy())
        assertTrue(log().any { it.tableName == "BibleBookmark" && it.entityId1 == saved.id })
        assertEquals(null, control.newOwnBookmarkLike(saved.id))
        assertNoUserRowReferencesAddonIds()
    }

    /** The generic-document variant (a MyDocument/commentary page key): same range data, own id, no add-on label. */
    @Test
    fun bookmarkThisVerseOnAGenericCollectionBookmarkMakesAnOwnGenericBookmark() = run {
        val genericId = addonId(module, IdType())
        dao.insert(BookmarkEntities.GenericBookmark(
            id = genericId, key = "Gen.1", bookInitials = "MyDoc", ordinalStart = 4, ordinalEnd = 6,
            startOffset = 2, endOffset = 7, customIcon = "heart",
        ))
        val before = dao.genericBookmarkById(genericId)!!
        val own = ownLabel()
        val made = control.newOwnBookmarkLike(genericId) as GenericBookmarkWithNotes
        assertTrue(made.new)
        assertFalse(made.id.isAddonId)
        val saved = control.addOrUpdateGenericBookmark(made, setOf(own.id, l))
        val loaded = dao.genericBookmarkById(saved.id)!!
        assertEquals(before.key, loaded.key)
        assertEquals(before.bookInitials, loaded.bookInitials)
        assertEquals(4, loaded.ordinalStart)
        assertEquals(6, loaded.ordinalEnd)
        assertEquals(2, loaded.startOffset)
        assertEquals(7, loaded.endOffset)
        assertEquals(null, loaded.customIcon)
        assertEquals(listOf(own.id), dao.labelsForBookmark(loaded).map { it.id })
        assertEquals("the collection bookmark is untouched", before, dao.genericBookmarkById(genericId))
        assertNoUserRowReferencesAddonIds()
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
        assertNoUserRowReferencesAddonIds()
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
        // assignableLabels keeps the add-on label (the chooser shows it, read-only) and the label manager marks it.
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
    fun addonLabelsAreVerseWidgetSourcesShownWithTheirModule() = run {
        val sources = widgetSources()
        assertEquals("Comfort · Manna", sources.labels().single { it.first == l }.second)
        val before = sources.label(l)!!
        assertEquals("Comfort · Manna", before.name)
        assertEquals(2, before.refs.size)
        // An own bookmark on a collection verse does not join the collection (the add-on label cannot be put on it).
        control.addOrUpdateBibleBookmark(control.newOwnBookmarkLike(b1) as BibleBookmarkWithNotes, setOf(ownLabel().id))
        assertEquals(2, sources.label(l)!!.refs.size)
    }

    @Test
    fun favouriteOfAnAddonLabelShowsInTheAssignableList() = run {
        control.insertOrUpdateLabel(control.labelById(l)!!.copy(favourite = true))
        assertTrue(control.assignableLabels().single { it.id == l }.favourite)
    }
}
