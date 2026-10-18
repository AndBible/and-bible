package net.bible.service.legacymigration.adopt

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.service.db.openSqlite
import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.map.BookmarksMapper
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.map.ReportEntry
import net.bible.service.legacymigration.map.WorkspacesMapper
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.fixture
import okio.Path
import okio.Path.Companion.toOkioPath

class StagingValidatorTest {
    private val stagedEntities: Set<String> = ROW_EXPECTATIONS
        .filter { it.category == LegacyCategory.WORKSPACES || it.category == LegacyCategory.BOOKMARKS }.flatMap { it.readerEntities }.toSet()
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() { dirs.forEach { it.deleteRecursively() }; dirs.clear() }

    /** A staging directory filled by the real workspace and bookmark mappers from the rich fixture, plus its report and the reader. */
    private inner class Staged(val dir: Path, val report: MigrationReport, val snapshot: LegacySnapshot) {
        /** Only the entities of the two staged categories count; the others are not part of this test's staging. */
        val readerCount: (String) -> Int = { if (it in stagedEntities) snapshot.cloud.count(it) else 0 }
    }

    private fun staged(): Staged = runBlocking {
        val fx = fixture("1.0.1", "17", "rich")
        val snapshot = LegacySnapshot(
            LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
            LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
        )
        val dir = Files.createTempDirectory("validator-").toFile().also { dirs += it }
        val report = MigrationReport()
        StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory()).use { staging ->
            WorkspacesMapper().map(snapshot, staging, report)
            BookmarksMapper().map(snapshot, staging, report)
        }
        Staged(dir.toOkioPath(), report, snapshot)
    }

    private fun validate(s: Staged, report: MigrationReport = s.report) =
        runBlocking { StagingValidator(JvmLegacyDatabaseFactory()).validate(s.dir, report, s.readerCount) }

    private fun sql(file: File, vararg statements: String) {
        val c = openSqlite(file.path)
        try {
            for (sql in statements) c.prepare(sql).let { try { it.step() } finally { it.close() } }
        } finally { c.close() }
    }

    private fun count(file: File, table: String): Int {
        val c = openSqlite(file.path, readOnly = true)
        try {
            return c.prepare("SELECT COUNT(*) FROM `$table`").let { try { it.step(); it.getLong(0).toInt() } finally { it.close() } }
        } finally { c.close() }
    }

    /** A report that agrees with what the staged database really holds: every Written entry of the original is recounted. */
    private fun consistentReport(s: Staged, skipped: Int = 0): MigrationReport = MigrationReport().also { r ->
        for (entry in s.report.entries) {
            if (entry is ReportEntry.Written) {
                val file = File(s.dir.toFile(), if (entry.category == LegacyCategory.BOOKMARKS) "bookmarks.sqlite3" else "workspaces.sqlite3")
                if (file.exists()) r.written(entry.category, entry.table, count(file, entry.table))
            }
        }
        if (skipped > 0) r.skipped(LegacyCategory.BOOKMARKS, "test skip", skipped)
    }

    @Test
    fun aValidStagingPasses() {
        val s = staged()
        assertTrue(s.readerCount("BibleBookmark") > 0)
        assertEquals(listOf("workspaces.sqlite3", "bookmarks.sqlite3"), validate(s))
    }

    @Test
    fun aMapperThatWritesFewerRowsAndReportsThemConsistentlyFails() {
        val s = staged()
        val before = count(File(s.dir.toFile(), "bookmarks.sqlite3"), "BibleBookmark")
        sql(File(s.dir.toFile(), "bookmarks.sqlite3"), "DELETE FROM BibleBookmark WHERE id = (SELECT id FROM BibleBookmark LIMIT 1)")
        assertTrue(count(File(s.dir.toFile(), "bookmarks.sqlite3"), "BibleBookmark") < before)
        val report = consistentReport(s)
        val e = assertFailsWith<LegacyMigrationException> { validate(s, report) }
        assertEquals(Kind.VALIDATION_FAILED, e.kind)
        assertTrue(e.message!!.contains("BibleBookmark"), e.message)
    }

    @Test
    fun aMapperThatReportsNothingForATableThatMustHoldRowsFails() {
        val s = staged()
        sql(File(s.dir.toFile(), "bookmarks.sqlite3"), "DELETE FROM BibleBookmarkToLabel", "DELETE FROM BibleBookmarkNotes", "DELETE FROM BibleBookmark")
        // The report no longer mentions any bookmark table at all.
        val report = MigrationReport().also { r -> s.report.entries.filterIsInstance<ReportEntry.Written>().filter { it.category != LegacyCategory.BOOKMARKS }.forEach { r.written(it.category, it.table, it.rows) } }
        val e = assertFailsWith<LegacyMigrationException> { validate(s, report) }
        assertEquals(Kind.VALIDATION_FAILED, e.kind)
    }

    @Test
    fun aCategoryThatWasNotStagedWhileTheReaderHasRowsFails() {
        val s = staged()
        File(s.dir.toFile(), "bookmarks.sqlite3").delete()
        val report = MigrationReport().also { r -> s.report.entries.filterIsInstance<ReportEntry.Written>().filter { it.category != LegacyCategory.BOOKMARKS }.forEach { r.written(it.category, it.table, it.rows) } }
        val e = assertFailsWith<LegacyMigrationException> { validate(s, report) }
        assertEquals(Kind.VALIDATION_FAILED, e.kind)
        assertTrue(e.message!!.contains("BOOKMARKS"), e.message)
    }

    @Test
    fun reportedSkipsExcuseMissingRowsOfTheirCategoryOnly() {
        // Known limitation, documented on ROW_EXPECTATIONS: a skip entry has a reason but no table, so it is credited per category.
        val s = staged()
        sql(File(s.dir.toFile(), "bookmarks.sqlite3"), "DELETE FROM BibleBookmark WHERE id = (SELECT id FROM BibleBookmark LIMIT 1)")
        validate(s, consistentReport(s, skipped = 1)) // one row lost, one skip reported: accepted
        // A skip reported for another category does not excuse it.
        val other = consistentReport(s).also { it.skipped(LegacyCategory.WORKSPACES, "unrelated", 5) }
        assertFailsWith<LegacyMigrationException> { validate(s, other) }
    }
}
