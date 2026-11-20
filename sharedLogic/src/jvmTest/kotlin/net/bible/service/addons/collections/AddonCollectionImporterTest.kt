package net.bible.service.addons.collections

import kotlinx.coroutines.test.runTest
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.bookmarks.isAddonId
import net.bible.service.db.openSqlite
import net.bible.service.db.exec
import net.bible.shareddata.fixtures.createFromExport
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonCollectionImporterTest {
    private class Fixture(val dir: String, val local: BookmarkDatabase, val file: Path) {
        val importer = AddonCollectionImporter({ local }, testEnv(dir), openCopy = { openBookmarkDb(it) })
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runTest {
        val dir = newDir()
        val local = newLocalDb(dir)
        val file = pathOf(dir, "manna.sqlite3")
        writeAuthorCollection(dir, file)
        try {
            Fixture(dir, local, file).block()
        } finally {
            local.close()
        }
    }

    private fun d(id: net.bible.android.database.IdType, module: String = "Manna") = addonId(module, id).toHex()

    @Test
    fun addonTablesAreNotSyncedTables() {
        val synced = net.bible.service.cloudsync.SyncableDatabaseDefinition.ALL.flatMap { c -> c.tables.map { it.tableName } }
        for (table in net.bible.service.cloudsync.ADDON_LOCAL_TABLES) assertFalse(table in synced, table)
    }

    @Test
    fun addonIdIsTaggedDeterministicAndModuleSpecific() {
        val a = addonId("Manna", Src.b1)
        assertEquals(a, addonId("Manna", Src.b1))
        assertTrue(a.isAddonId)
        assertFalse(Src.b1.isAddonId)
        assertFalse(addonId("Other", Src.b1) == a)
        assertFalse(addonId("Manna", Src.b2) == a)
    }

    @Test
    fun importAddsLabelsBookmarksNotesEntriesUnderDerivedIds() = withFixture {
        local.sql("INSERT INTO Label (id, name) VALUES (${hex(PARAGRAPH_BREAK_LABEL_ID)}, '$PARAGRAH_BREAK_LABEL_NAME')")
        importer.replace(ref("Manna", file), "h1")

        assertEquals(setOf(d(Src.comfort), d(Src.study), paragraphBreakHex.lowercase()), local.hexIds("Label"))
        assertEquals(setOf(d(Src.b1), d(Src.b2)), local.hexIds("BibleBookmark"))
        assertEquals(setOf(d(Src.g1)), local.hexIds("GenericBookmark"))
        assertEquals(listOf(listOf(d(Src.b1).uppercase(), "Fear not")), local.rows("SELECT hex(bookmarkId), notes FROM BibleBookmarkNotes"))
        assertEquals(
            setOf(
                listOf(d(Src.b1), d(Src.comfort), "-1"),
                listOf(d(Src.b2), d(Src.comfort), "-1"),
                listOf(d(Src.b2), d(Src.study), "1"),
                listOf(d(Src.b2), paragraphBreakHex.lowercase(), "-1"),
            ),
            local.rows("SELECT lower(hex(bookmarkId)), lower(hex(labelId)), orderNumber FROM BibleBookmarkToLabel").toSet(),
        )
        assertEquals(listOf(listOf(d(Src.g1).uppercase(), d(Src.study).uppercase(), "3")),
            local.rows("SELECT hex(bookmarkId), hex(labelId), orderNumber FROM GenericBookmarkToLabel"))
        assertEquals(listOf(listOf(d(Src.entry).uppercase(), d(Src.study).uppercase(), "Intro text")),
            local.rows("SELECT hex(e.id), hex(e.labelId), t.text FROM StudyPadTextEntry e JOIN StudyPadTextEntryText t ON t.studyPadTextEntryId = e.id"))
        assertEquals(d(Src.comfort).uppercase(), local.rows("SELECT hex(primaryLabelId) FROM BibleBookmark WHERE id = X'${d(Src.b1)}'").single()[0])
        // Label fields survive.
        assertEquals(listOf(listOf("Study", "6", "EXAMPLE")), local.rows("SELECT name, color, type FROM Label WHERE id = X'${d(Src.study)}'"))

        // Ownership: every imported row, the special label itself not.
        val dao = local.addonCollectionDao()
        assertTrue(dao.isAddonLabel(addonId("Manna", Src.comfort)))
        assertFalse(dao.isAddonLabel(PARAGRAPH_BREAK_LABEL_ID))
        assertTrue(dao.isAddonBookmark(addonId("Manna", Src.g1)))
        assertTrue(dao.isAddonLink("BibleBookmarkToLabel", addonId("Manna", Src.b2), PARAGRAPH_BREAK_LABEL_ID))
        assertEquals("Manna", dao.moduleOfLabel(addonId("Manna", Src.study)))
        // 2 labels, 2 Bible bookmarks, 1 note, 4 links, 1 generic bookmark, 1 generic link, 1 entry, 1 entry text.
        assertEquals(13, local.count("SELECT COUNT(*) FROM AddonCollectionRow"))
        val module = dao.module("Manna")!!
        assertEquals("1.0", module.version)
        assertEquals("h1", module.contentHash)
    }

    @Test
    fun sameFileOnTwoFreshDatabasesGivesSameIds() = withFixture {
        importer.replace(ref("Manna", file), "h")
        val other = newLocalDb(dir, "other.sqlite3", deviceId = "other")
        try {
            AddonCollectionImporter({ other }, testEnv(dir), openCopy = { openBookmarkDb(it) }).replace(ref("Manna", file), "h")
            for (table in listOf("Label", "BibleBookmark", "GenericBookmark", "StudyPadTextEntry")) {
                assertEquals(local.hexIds(table), other.hexIds(table), table)
            }
            assertEquals(local.rows("SELECT hex(bookmarkId), hex(labelId) FROM BibleBookmarkToLabel ORDER BY 1, 2"),
                other.rows("SELECT hex(bookmarkId), hex(labelId) FROM BibleBookmarkToLabel ORDER BY 1, 2"))
        } finally {
            other.close()
        }
    }

    /** Review Focus 1: the author owns label X and installs the module made from X. */
    @Test
    fun authorsOwnLabelAndAddonCopyCoexist() = withFixture {
        seedAuthor(local)
        val logBefore = local.count("SELECT COUNT(*) FROM LogEntry")
        importer.replace(ref("Manna", file), "h")

        assertEquals(setOf(Src.comfort.toHex(), Src.study.toHex(), d(Src.comfort), d(Src.study)), local.hexIds("Label"))
        assertEquals(4, local.count("SELECT COUNT(*) FROM BibleBookmark"))
        assertEquals(3, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE labelId IN (${hex(Src.comfort)}, ${hex(Src.study)})"))
        assertEquals(logBefore, local.count("SELECT COUNT(*) FROM LogEntry"))

        // Editing the own label does not change the add-on copy.
        local.sql("UPDATE Label SET name = 'Comfort edited' WHERE id = ${hex(Src.comfort)}")
        assertEquals("Comfort", local.rows("SELECT name FROM Label WHERE id = X'${d(Src.comfort)}'").single()[0])

        // Removing the module never touches the own label, its bookmarks, notes or links.
        importer.remove("Manna")
        assertEquals(setOf(Src.comfort.toHex(), Src.study.toHex()), local.hexIds("Label"))
        assertEquals(setOf(Src.b1.toHex(), Src.b2.toHex()), local.hexIds("BibleBookmark"))
        assertEquals(3, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkNotes"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM StudyPadTextEntry"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM AddonCollectionRow"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM AddonCollectionModule"))
    }

    @Test
    fun twoModulesSharingASourceBookmarkOwnSeparateRows() = withFixture {
        importer.replace(ref("Manna", file), "h")
        importer.replace(ref("Other", file), "h")
        assertEquals(4, local.count("SELECT COUNT(*) FROM BibleBookmark"))
        assertEquals(4, local.count("SELECT COUNT(*) FROM Label"))

        importer.remove("Manna")
        assertEquals(setOf(d(Src.b1, "Other"), d(Src.b2, "Other")), local.hexIds("BibleBookmark"))
        assertEquals(setOf(d(Src.comfort, "Other"), d(Src.study, "Other")), local.hexIds("Label"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkNotes"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM StudyPadTextEntryText"))
    }

    @Test
    fun specialLabelsSkippedAndLinksMappedToLocalSpecial() = withFixture {
        // Without the device's own paragraph-break label the link is dropped, not inserted dangling.
        importer.replace(ref("Manna", file), "h")
        assertEquals(0, local.count("SELECT COUNT(*) FROM Label WHERE name = '$PARAGRAH_BREAK_LABEL_NAME'"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE labelId = ${hex(PARAGRAPH_BREAK_LABEL_ID)}"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM AddonCollectionRow WHERE id2 = ${hex(PARAGRAPH_BREAK_LABEL_ID)}"))

        // With it, the link points at the device's own label, and the label is not taken over.
        local.sql("INSERT INTO Label (id, name, color) VALUES (${hex(PARAGRAPH_BREAK_LABEL_ID)}, '$PARAGRAH_BREAK_LABEL_NAME', 42)")
        importer.replace(ref("Manna", file), "h2")
        assertEquals(listOf(listOf(d(Src.b2).uppercase())),
            local.rows("SELECT hex(bookmarkId) FROM BibleBookmarkToLabel WHERE labelId = ${hex(PARAGRAPH_BREAK_LABEL_ID)}"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM Label WHERE name = '$PARAGRAH_BREAK_LABEL_NAME'"))
        importer.remove("Manna")
        assertEquals(listOf(listOf("42")), local.rows("SELECT color FROM Label WHERE id = ${hex(PARAGRAPH_BREAK_LABEL_ID)}"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel"))
    }

    @Test
    fun fileWithOnlySpecialLabelsProvidesNothing() = withFixture {
        val only = pathOf(dir, "special.sqlite3")
        openBookmarkDb(only.toString()).apply {
            try { sql("INSERT INTO Label (id, name) VALUES (${hex(PARAGRAPH_BREAK_LABEL_ID)}, '$PARAGRAH_BREAK_LABEL_NAME')") } finally { close() }
        }
        assertFailsWith<EmptyAddonCollectionException> { importer.replace(ref("Empty", only), "h") }
        assertEquals(0, local.count("SELECT COUNT(*) FROM AddonCollectionModule"))
    }

    @Test
    fun noLogEntryWrittenDuringImportUpdateAndRemove() = withFixture {
        // A user bookmark with an own link to the add-on label: module removal cascades nothing that is logged.
        importer.replace(ref("Manna", file), "h")
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM SyncConfiguration WHERE keyName = 'triggersDisabled' AND booleanValue = 1"))
        importer.replace(ref("Manna", file, version = "2.0"), "h2")
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
        importer.remove("Manna")
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
        // Triggers are back on afterwards.
        local.sql("INSERT INTO Label (id, name) VALUES (X'0102', 'mine')")
        assertEquals(1, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    @Test
    fun replaceIsAtomic() = withFixture {
        importer.replace(ref("Manna", file), "h1")
        val before = local.rows("SELECT hex(id), name FROM Label ORDER BY 1")
        val rowsBefore = local.count("SELECT COUNT(*) FROM AddonCollectionRow")
        importer.afterDeleteHookForTest = {
            assertEquals(0, local.count("SELECT COUNT(*) FROM Label"), "rows deleted inside the transaction")
            error("crash between delete and insert")
        }
        assertFailsWith<IllegalStateException> { importer.replace(ref("Manna", file, version = "2.0"), "h2") }
        importer.afterDeleteHookForTest = null
        assertEquals(before, local.rows("SELECT hex(id), name FROM Label ORDER BY 1"))
        assertEquals(rowsBefore, local.count("SELECT COUNT(*) FROM AddonCollectionRow"))
        assertEquals("h1", local.addonCollectionDao().module("Manna")!!.contentHash)
        assertEquals(0, local.count("SELECT COUNT(*) FROM SyncConfiguration WHERE keyName = 'triggersDisabled' AND booleanValue = 1"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    @Test
    fun updateKeepsIdsAndUserLinkToAddonLabel() = withFixture {
        importer.replace(ref("Manna", file), "h1")
        val l = addonId("Manna", Src.comfort)
        // The user's own bookmark carries the add-on label (the per-bookmark copy's link).
        local.sql(
            "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt, primaryLabelId) VALUES (X'0F', 100, 101, 100, 101, 'KJV', 9, ${hex(l)})",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (X'0F', ${hex(l)})",
        )
        local.sql("DELETE FROM LogEntry")
        importer.replace(ref("Manna", file, version = "2.0"), "h2")
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE bookmarkId = X'0F' AND labelId = ${hex(l)}"))
        assertEquals(l.toHex().uppercase(), local.rows("SELECT hex(primaryLabelId) FROM BibleBookmark WHERE id = X'0F'").single()[0])

        // Module removal drops only the link (and the primary label), never the user's bookmark; nothing logged.
        importer.remove("Manna")
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmark WHERE id = X'0F'"))
        assertEquals(0, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel"))
        assertEquals(null, local.rows("SELECT primaryLabelId FROM BibleBookmark WHERE id = X'0F'").single()[0])
        assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
    }

    @Test
    fun oldSchemaFileIsMigrated() = withFixture {
        val old = pathOf(dir, "old.sqlite3")
        createFromExport("net.bible.android.database.BookmarkDatabase", 12, old.toString())
        openSqlite(old.toString()).also { c ->
            try {
                c.exec("INSERT INTO Label (id, name, color, markerStyle) VALUES (${hex(Src.comfort)}, 'Old', 3, 1)")
                c.exec("INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt) VALUES (${hex(Src.b1)}, 5, 5, 5, 5, 'KJV', 1)")
                c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber, indentLevel, expandContent) VALUES (${hex(Src.b1)}, ${hex(Src.comfort)}, 0, 0, 1)")
            } finally { c.close() }
        }
        importer.replace(ref("Old", old), "h")
        assertEquals(listOf(listOf("Old", "2")), local.rows("SELECT name, displayStyle FROM Label WHERE id = X'${d(Src.comfort, "Old")}'"))
        assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel"))
        // The module's own file was not written.
        assertEquals(listOf(listOf("12")), openBookmarkRaw(old))
    }

    private fun openBookmarkRaw(path: Path): List<List<String>> = openSqlite(path.toString(), readOnly = true).let { c ->
        try {
            listOf(listOf(c.prepare("PRAGMA user_version").use { st -> st.step(); st.getLong(0).toString() }))
        } finally { c.close() }
    }
}
