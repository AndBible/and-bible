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
        // An unattributed skip (table null) is credited to every table of its category; an attributed one only to its table.
        val s = staged()
        sql(File(s.dir.toFile(), "bookmarks.sqlite3"), "DELETE FROM BibleBookmark WHERE id = (SELECT id FROM BibleBookmark LIMIT 1)")
        validate(s, consistentReport(s, skipped = 1)) // one row lost, one skip reported: accepted
        // A skip reported for another category does not excuse it.
        val other = consistentReport(s).also { it.skipped(LegacyCategory.WORKSPACES, "unrelated", 5) }
        assertFailsWith<LegacyMigrationException> { validate(s, other) }
    }

    @Test
    fun aSkipAttributedToAnotherTableDoesNotExcuseMissingRows() {
        val s = staged()
        sql(File(s.dir.toFile(), "bookmarks.sqlite3"), "DELETE FROM BibleBookmarkToLabel WHERE bookmarkId = (SELECT bookmarkId FROM BibleBookmarkToLabel LIMIT 1)")
        val lost = 1
        // A skip of the right table excuses the loss, a skip of a table that lost nothing does not, an unattributed one does.
        validate(s, consistentReport(s).also { it.skipped(LegacyCategory.BOOKMARKS, "links of a hidden bookmark", 5, "BibleBookmarkToLabel") })
        assertFailsWith<LegacyMigrationException> { validate(s, consistentReport(s).also { it.skipped(LegacyCategory.BOOKMARKS, "other table", lost + 5, "Label") }) }
        validate(s, consistentReport(s).also { it.skipped(LegacyCategory.BOOKMARKS, "unattributed", 5) })
    }

    @Test
    fun pageManagersMustEqualWindows() {
        val s = staged()
        sql(File(s.dir.toFile(), "workspaces.sqlite3"), "DELETE FROM PageManager WHERE windowId = (SELECT windowId FROM PageManager LIMIT 1)")
        val e = assertFailsWith<LegacyMigrationException> { validate(s, consistentReport(s)) }
        assertTrue(e.message!!.contains("PageManager"), e.message)
    }

    @Test
    fun aSyntheticPageManagerBeyondTheLegacyCountIsNotCapped() {
        val s = staged()
        // The reader has fewer page managers than windows (here: it claims none); the staged one-per-window layout is accepted.
        val lenient: (String) -> Int = { if (it == "PageManager") 0 else s.readerCount(it) }
        runBlocking { StagingValidator(JvmLegacyDatabaseFactory()).validate(s.dir, s.report, lenient) }
    }

    @Test
    fun aiTablesMayHoldMoreRowsThanTheLegacyStoreBecauseOfThePreservedFile() {
        val s = stagedAi()
        val dir = s.dir
        // Staged AI rows exist while the reader (no preserved rows) reports none: uncapped lower bound, accepted.
        val none: (String) -> Int = { 0 }
        runBlocking { StagingValidator(JvmLegacyDatabaseFactory()).validate(dir, s.report, none) }
        // Whereas a capped table with more staged than legacy rows is rejected.
        val workspacesOnly: (String) -> Int = { 0 }
        val ws = staged()
        assertFailsWith<LegacyMigrationException> { runBlocking { StagingValidator(JvmLegacyDatabaseFactory()).validate(ws.dir, ws.report, workspacesOnly) } }
    }

    private fun stagedAi(): Staged = runBlocking {
        val fx = fixture("1.0.1", "17", "rich")
        val snapshot = LegacySnapshot(
            LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
            LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
        )
        val dir = Files.createTempDirectory("validator-ai-").toFile().also { dirs += it }
        val report = MigrationReport()
        StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory()).use { staging ->
            net.bible.service.legacymigration.map.AiSettingsMapper(null).map(snapshot, staging, report)
        }
        Staged(dir.toOkioPath(), report, snapshot)
    }
}
