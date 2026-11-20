package net.bible.service.addons.collections

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedlogic.platform.CollectionPackRef
import net.bible.sharedlogic.platform.CollectionPackSource
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonCollectionReconcilerTest {
    private class FakeSource : CollectionPackSource {
        var refs: List<CollectionPackRef> = emptyList()
        var calls = 0
        var inside = 0
        var maxInside = 0
        override fun collections(): List<CollectionPackRef> {
            calls++
            inside++
            maxInside = maxOf(maxInside, inside)
            return refs.also { inside-- }
        }
        override val reloaded: Events<Unit> = EventSource()
    }

    private class Fixture(val dir: String, val local: BookmarkDatabase, val file: Path) {
        val source = FakeSource()
        val notified = mutableListOf<List<LogEntry>>()
        val importer = AddonCollectionImporter({ local }, testEnv(dir), openCopy = { openBookmarkDb(it) })
        val reconciler = AddonCollectionReconciler(source, importer, { local.addonCollectionDao() }) { notified += it }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runTest {
        val dir = newDir()
        val local = newLocalDb(dir)
        val file = pathOf(dir, "manna.sqlite3")
        writeAuthorCollection(dir, file)
        try { Fixture(dir, local, file).block() } finally { local.close() }
    }

    private suspend fun Fixture.importedAt() = local.addonCollectionDao().module("Manna")?.importedAt

    /** Every add-on row in the synced tables is owned by a module. */
    private suspend fun Fixture.unownedAddonRows(): Long =
        listOf("Label" to "id", "BibleBookmark" to "id", "GenericBookmark" to "id", "StudyPadTextEntry" to "id",
            "BibleBookmarkNotes" to "bookmarkId", "StudyPadTextEntryText" to "studyPadTextEntryId").sumOf { (table, column) ->
            local.count("SELECT COUNT(*) FROM $table t WHERE substr(t.$column, 5, 6) = X'ADD08C01A011' AND NOT EXISTS " +
                "(SELECT 1 FROM AddonCollectionRow a WHERE a.tableName = '$table' AND a.id1 = t.$column)")
        } + local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel t WHERE substr(t.bookmarkId, 5, 6) = X'ADD08C01A011' AND NOT EXISTS " +
            "(SELECT 1 FROM AddonCollectionRow a WHERE a.tableName = 'BibleBookmarkToLabel' AND a.id1 = t.bookmarkId AND a.id2 = t.labelId)")

    @Test
    fun newModuleIsImportedAndReported() = withFixture {
        source.refs = listOf(ref("Manna", file))
        assertTrue(reconciler.reconcile())
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label"))
        assertEquals(sha256(file), local.addonCollectionDao().module("Manna")!!.contentHash)
        val entries = notified.single()
        assertTrue(entries.all { it.type == LogEntryTypes.UPSERT })
        assertEquals(setOf(addonId("Manna", Src.comfort), addonId("Manna", Src.study)),
            entries.filter { it.tableName == "Label" }.map { it.entityId1 }.toSet())
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    @Test
    fun unchangedModuleIsLeftAlone() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        val at = importedAt()
        notified.clear()
        assertFalse(reconciler.reconcile())
        assertEquals(at, importedAt())
        assertTrue(notified.isEmpty())
    }

    @Test
    fun sameVersionWithNewContentIsReplaced() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        openBookmarkDb(file.toString()).apply { try { sql("UPDATE Label SET name = 'Comfort 2' WHERE id = ${hex(Src.comfort)}") } finally { close() } }
        assertTrue(reconciler.reconcile())
        assertEquals("Comfort 2", local.rows("SELECT name FROM Label WHERE id = ${hex(addonId("Manna", Src.comfort))}").single()[0])
        assertEquals(sha256(file), local.addonCollectionDao().module("Manna")!!.contentHash)
    }

    @Test
    fun newVersionIsReplacedAndDroppedVerseReported() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        // Version 2 drops b1 (but the file content is otherwise the same).
        val v2 = pathOf(dir, "manna-v2.sqlite3")
        FileSystem.SYSTEM.copy(file, v2)
        openBookmarkDb(v2.toString()).apply { try { sql("DELETE FROM BibleBookmark WHERE id = ${hex(Src.b1)}") } finally { close() } }
        source.refs = listOf(ref("Manna", v2, version = "2.0"))
        notified.clear()
        assertTrue(reconciler.reconcile())
        assertEquals("2.0", local.addonCollectionDao().module("Manna")!!.version)
        assertEquals(setOf(addonId("Manna", Src.b2).toHex()), local.hexIds("BibleBookmark"))
        val deleted = notified.single().filter { it.type == LogEntryTypes.DELETE }
        assertTrue(deleted.any { it.tableName == "BibleBookmark" && it.entityId1 == addonId("Manna", Src.b1) })
        assertEquals(0, local.count("SELECT COUNT(*) FROM BibleBookmarkNotes"))
    }

    @Test
    fun goneModuleIsRemoved() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        source.refs = emptyList()
        notified.clear()
        assertTrue(reconciler.reconcile())
        for (table in listOf("Label", "BibleBookmark", "GenericBookmark", "StudyPadTextEntry", "AddonCollectionRow", "AddonCollectionModule")) {
            assertEquals(0, local.count("SELECT COUNT(*) FROM $table"), table)
        }
        assertTrue(notified.single().any { it.type == LogEntryTypes.DELETE && it.tableName == "Label" })
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    @Test
    fun brokenFileKeepsPreviousContent() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        val broken = pathOf(dir, "broken.sqlite3")
        FileSystem.SYSTEM.write(broken) { writeUtf8("not a database") }
        source.refs = listOf(ref("Manna", broken, version = "2.0"))
        assertFalse(reconciler.reconcile())
        assertEquals("1.0", local.addonCollectionDao().module("Manna")!!.version)
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label"))
    }

    /**
     * Review Focus 2: a restored backup that has the add-on rows but no ownership records (and a stale add-on row
     * the module no longer provides). After reconciliation: exactly one copy, nothing owned by nobody.
     */
    @Test
    fun restoredAddonRowsWithoutOwnershipAreTakenOverAndStaleOnesRemoved() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        val labels = local.hexIds("Label")
        val bookmarks = local.hexIds("BibleBookmark")
        val links = local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel")
        val stale = addonId("Manna", net.bible.android.database.IdType("44444444-0000-4000-8000-000000000001"))
        local.sql(
            "DELETE FROM AddonCollectionModule", "DELETE FROM AddonCollectionRow",
            "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt) VALUES (${hex(stale)}, 7, 7, 7, 7, 'KJV', 1)",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(stale)}, ${hex(addonId("Manna", Src.comfort))})",
        )
        assertTrue(unownedAddonRows() > 0)

        assertTrue(reconciler.reconcile())
        assertEquals(labels, local.hexIds("Label"))
        assertEquals(bookmarks, local.hexIds("BibleBookmark"))
        assertEquals(links, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel"))
        assertEquals(0, unownedAddonRows())
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    @Test
    fun unownedAddonRowsWithoutAnyModuleAreSwept() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        local.sql("DELETE FROM AddonCollectionModule", "DELETE FROM AddonCollectionRow")
        source.refs = emptyList()
        assertTrue(reconciler.reconcile())
        assertEquals(0, local.count("SELECT COUNT(*) FROM Label"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM BibleBookmark"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM StudyPadTextEntryText"))
        // Nothing left to sweep: the next run writes nothing.
        assertFalse(reconciler.reconcile())
    }

    @Test
    fun concurrentReconcilesRunOneAtATime() = withFixture {
        source.refs = listOf(ref("Manna", file))
        val results = kotlinx.coroutines.coroutineScope { (1..3).map { async { yield(); reconciler.reconcile() } }.awaitAll() }
        assertEquals(3, source.calls)
        assertEquals(1, source.maxInside)
        assertEquals(listOf(true, false, false), results)
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label"))
    }
}
