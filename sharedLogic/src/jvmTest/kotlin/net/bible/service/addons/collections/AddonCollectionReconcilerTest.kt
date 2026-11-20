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

/** SQL: the row's id is an add-on id. */
private const val ADDON = "substr(id, 5, 6) = X'ADD08C01A011'"

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
        /** Copies opened for an import (one per import attempt). */
        var copiesOpened = 0
        val importer = AddonCollectionImporter({ local }, testEnv(dir), openCopy = { copiesOpened++; openBookmarkDb(it) })
        val reconciler = AddonCollectionReconciler(source, importer, { local.addonCollectionDao() }) { notified += it }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runTest {
        val dir = newDir()
        val local = newLocalDb(dir)
        // The device has its paragraph-break label already (created on a fresh device: AddonCollectionImporterTest).
        local.sql("INSERT INTO Label (id, name) VALUES (${hex(net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID)}, '${net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME}')", "DELETE FROM LogEntry")
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
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label WHERE $ADDON"))
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
            assertEquals(0, local.count("SELECT COUNT(*) FROM $table" + if (table == "Label") " WHERE $ADDON" else ""), table)
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
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label WHERE $ADDON"))
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
        assertEquals(0, local.count("SELECT COUNT(*) FROM Label WHERE $ADDON"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM BibleBookmark"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM StudyPadTextEntryText"))
        // Nothing left to sweep: the next run writes nothing.
        assertFalse(reconciler.reconcile())
    }

    /** An installed module whose file cannot be read now (storage not mounted) is not treated as uninstalled. */
    @Test
    fun unreadableFileKeepsTheImportAndTheUsersLinks() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        val l = addonId("Manna", Src.comfort)
        local.sql(
            "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt) VALUES (X'0F', 1, 1, 1, 1, 'KJV', 9)",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (X'0F', ${hex(l)})",
            "INSERT INTO AddonLabelLocalState (labelId, favourite) VALUES (${hex(l)}, 1)",
        )
        source.refs = listOf(ref("Manna", pathOf(dir, "not-mounted/manna.sqlite3")))
        assertFalse(reconciler.reconcile())
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label WHERE $ADDON"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE bookmarkId = X'0F'"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM AddonLabelLocalState"))
    }

    /** Two modules after a restore emptied the ownership tables: neither import drops the user's links into the other. */
    @Test
    fun twoModulesAfterARestoreKeepTheUsersLinks() = withFixture {
        source.refs = listOf(ref("Manna", file), ref("Other", file))
        reconciler.reconcile()
        val manna = addonId("Manna", Src.comfort)
        val other = addonId("Other", Src.comfort)
        local.sql(
            "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt) VALUES (X'0F', 1, 1, 1, 1, 'KJV', 9)",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (X'0F', ${hex(manna)})",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (X'0F', ${hex(other)})",
            // A restore of a backup taken before the modules were installed: no add-on rows, no records (a whole-file
            // swap: no cascade).
            "PRAGMA foreign_keys=OFF",
            "DELETE FROM BibleBookmarkToLabel WHERE substr(bookmarkId, 5, 6) = X'ADD08C01A011'",
            "DELETE FROM Label WHERE substr(id, 5, 6) = X'ADD08C01A011'",
            "DELETE FROM AddonCollectionRow", "DELETE FROM AddonCollectionModule",
            "PRAGMA foreign_keys=ON",
        )
        assertTrue(reconciler.reconcile())
        assertEquals(setOf(manna.toHex(), other.toHex()), local.rows("SELECT lower(hex(labelId)) FROM BibleBookmarkToLabel WHERE bookmarkId = X'0F'").map { it[0] }.toSet())
        // The module goes: the user's links stay (it may come back), only its own rows go.
        source.refs = listOf(ref("Other", file))
        reconciler.reconcile()
        assertEquals(setOf(manna.toHex(), other.toHex()), local.rows("SELECT lower(hex(labelId)) FROM BibleBookmarkToLabel WHERE bookmarkId = X'0F'").map { it[0] }.toSet())
        assertEquals(0, local.count("SELECT COUNT(*) FROM Label WHERE id = ${hex(manna)}"))
    }

    /**
     * Sync review IMPORTANT 1: a module that is briefly missing (an update that uninstalls first, storage not mounted at
     * start, a reinstall) must not cost the user their links to its labels or the primary label of their bookmark, and
     * nothing is written unlogged to the user's rows.
     */
    @Test
    fun briefMissingModuleKeepsTheUsersLinkAndPrimaryLabel() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        val l = addonId("Manna", Src.comfort)
        val own = net.bible.android.database.IdType()
        local.sql(
            "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt, primaryLabelId) VALUES (${hex(own)}, 100, 101, 100, 101, 'KJV', 9, ${hex(l)})",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(own)}, ${hex(l)})",
            "DELETE FROM LogEntry",
        )
        val before = local.rows("SELECT * FROM BibleBookmark WHERE id = ${hex(own)}")

        source.refs = emptyList()
        assertTrue(reconciler.reconcile())
        assertFalse(reconciler.reconcile())
        assertEquals(before, local.rows("SELECT * FROM BibleBookmark WHERE id = ${hex(own)}"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE bookmarkId = ${hex(own)} AND labelId = ${hex(l)}"))

        source.refs = listOf(ref("Manna", file))
        assertTrue(reconciler.reconcile())
        assertEquals(before, local.rows("SELECT * FROM BibleBookmark WHERE id = ${hex(own)}"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel b JOIN Label l ON l.id = b.labelId WHERE b.bookmarkId = ${hex(own)}"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    /**
     * Logic review IMPORTANT 3: a module that can never be imported (here: nothing but special labels) is tried once per
     * version and content, not on every run, and does not keep the sweep of unowned add-on rows from running.
     */
    @Test
    fun neverImportableModuleIsNotRetriedAndDoesNotBlockTheSweep() = withFixture {
        val empty = pathOf(dir, "empty.sqlite3")
        openBookmarkDb(empty.toString()).apply {
            try { sql("INSERT INTO Label (id, name) VALUES (${hex(net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID)}, '${net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME}')") } finally { close() }
        }
        source.refs = listOf(ref("Manna", file), ref("Empty", empty))
        reconciler.reconcile()
        val stale = addonId("Gone", Src.comfort)
        local.sql("INSERT INTO Label (id, name) VALUES (${hex(stale)}, 'Stale')")
        copiesOpened = 0
        assertTrue(reconciler.reconcile(), "the stale add-on row is swept")
        assertEquals(0, local.count("SELECT COUNT(*) FROM Label WHERE id = ${hex(stale)}"))
        assertEquals(0, copiesOpened, "the empty module is not imported again")
        assertFalse(reconciler.reconcile())
        assertEquals(0, copiesOpened)
        // A new version is tried again.
        source.refs = listOf(ref("Manna", file), ref("Empty", empty, version = "2.0"))
        reconciler.reconcile()
        assertEquals(1, copiesOpened)
        reconciler.reconcile()
        assertEquals(1, copiesOpened)
        // The add-ons were reloaded (e.g. the module installed again): tried again.
        (source.reloaded as EventSource<Unit>).emit(Unit)
        reconciler.reconcile()
        assertEquals(2, copiesOpened)
    }

    /** Sync review Minor 4: an unchanged file (same size and modification time) is not hashed again. */
    @Test
    fun unchangedFileIsNotHashedAgain() = withFixture {
        source.refs = listOf(ref("Manna", file))
        reconciler.reconcile()
        val f = java.io.File(file.toString())
        val modified = f.lastModified()
        val bytes = f.readBytes()
        // Same size and time, other content (the header's application id): taken as unchanged (the pre-check).
        val i = 68
        bytes[i] = (bytes[i] + 1).toByte()
        f.writeBytes(bytes)
        f.setLastModified(modified)
        assertFalse(reconciler.reconcile())
        assertEquals(0, copiesOpened - 1, "no new import")
        // A new modification time: hashed, and the change is found.
        f.setLastModified(modified + 2000)
        assertTrue(reconciler.reconcile())
        assertEquals(sha256(file), local.addonCollectionDao().module("Manna")!!.contentHash)
    }

    @Test
    fun concurrentReconcilesRunOneAtATime() = withFixture {
        source.refs = listOf(ref("Manna", file))
        val results = kotlinx.coroutines.coroutineScope { (1..3).map { async { yield(); reconciler.reconcile() } }.awaitAll() }
        assertEquals(3, source.calls)
        assertEquals(1, source.maxInside)
        assertEquals(listOf(true, false, false), results)
        assertEquals(2, local.count("SELECT COUNT(*) FROM Label WHERE $ADDON"))
    }
}
