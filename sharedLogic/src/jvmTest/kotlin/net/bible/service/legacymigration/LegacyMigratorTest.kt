package net.bible.service.legacymigration

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.extras.LegacyExtrasFile
import net.bible.service.legacymigration.map.BookmarksMapper
import net.bible.service.legacymigration.map.CategoryMapper
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.map.SettingsMapper
import net.bible.service.legacymigration.map.WorkspacesMapper
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.map.ReportEntry
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.compareWithOracleDetailed
import net.bible.service.legacymigration.testsupport.CountingFileSystem
import net.bible.service.legacymigration.testsupport.CrashingFileSystem
import net.bible.service.legacymigration.testsupport.FailingCleanupFileSystem
import net.bible.service.legacymigration.testsupport.FailingDeleteFileSystem
import net.bible.service.legacymigration.testsupport.ThrowAfterDoneMoveFileSystem
import net.bible.service.legacymigration.testsupport.RecordingSyncPlatform
import net.bible.service.legacymigration.testsupport.ThrowingKeychainPlatform
import net.bible.service.legacymigration.testsupport.execSql
import net.bible.service.legacymigration.testsupport.makeModelUnknown
import net.bible.service.legacymigration.testsupport.readText
import net.bible.service.legacymigration.testsupport.FakeLegacyPlatform
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.PlatformWithFileSystem
import net.bible.service.legacymigration.testsupport.SimulatedKill
import net.bible.service.legacymigration.testsupport.databaseSummary
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.hashTree
import net.bible.service.legacymigration.testsupport.installLegacyApp
import net.bible.service.legacymigration.testsupport.writeSqlite
import net.bible.service.llm.apiKeyName
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

class LegacyMigratorTest {
    private class Env(val fx: FixtureDir, val platform: FakeLegacyPlatform) {
        val databases: Path get() = platform.databasesDir
        val migrationDir: Path get() = platform.applicationSupport / "LegacyMigration"
        fun databaseFiles(): List<String> = File(databases.toString()).list().orEmpty().sorted()
        fun sourceHashes(): Map<String, String> = hashTree(platform.applicationSupport).filterKeys { !it.startsWith("LegacyMigration") }
    }

    private fun env(app: String = "1.0.1", ios: String = "17", scenario: String = "rich", preserved: Boolean = false): Env {
        val fx = fixture(app, ios, scenario)
        val platform = FakeLegacyPlatform.from(fx)
        installLegacyApp(fx, platform, withPreserved = preserved)
        return Env(fx, platform)
    }

    private fun migrator(
        platform: LegacyPlatform,
        mappers: List<CategoryMapper>? = null,
        failAt: Step? = null,
        replaceExisting: Boolean = false,
        stepHook: suspend (Step) -> Unit = {},
    ) = LegacyMigrator(platform, JvmLegacyDatabaseFactory(), mappers ?: defaultMappers(platform), failAt, platform.documents / "jsword" / "readingplan", replaceExisting, stepHook)

    private fun run(platform: LegacyPlatform, failAt: Step? = null, replaceExisting: Boolean = false) =
        runBlocking { migrator(platform, null, failAt, replaceExisting).run() }

    private fun countRows(file: File): Int {
        val c = net.bible.service.db.openSqlite(file.path, readOnly = true)
        try {
            val s = c.prepare("SELECT COUNT(*) FROM t")
            try { s.step(); return s.getLong(0).toInt() } finally { s.close() }
        } finally {
            c.close()
        }
    }

    /** The staged contents of a clean run, the reference of every "same result after a failure" assertion. */
    private val baseline: Map<String, Map<String, Int>> by lazy {
        val e = env()
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        databaseSummary(e.databases)
    }

    // ---- the brief's list -----------------------------------------------------------------------------------

    @Test
    fun freshInstallIsNothingToMigrate() {
        val fx = fixture("1.0.1", "17", "fresh")
        val platform = FakeLegacyPlatform.from(fx) // no store installed: the app was never run
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, run(platform))
        assertEquals(emptyList(), File(platform.databasesDir.toString()).list().orEmpty().toList())
    }

    @Test
    fun richFixtureMigratesAndWritesMarker() {
        val e = env("1.0.1", "17", "rich")
        File(e.platform.documents.toString(), "search_indexes.sqlite").writeText("index")
        File(e.platform.documents.toString(), "search_indexes.sqlite-wal").writeText("wal")
        val before = e.sourceHashes()
        assertTrue(before.keys.any { it.endsWith("-wal") }, "the fixture has sidecars to protect")

        val outcome = run(e.platform)

        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(outcome)
        assertTrue(migrated.report.entries.isNotEmpty())
        // Every adopted database opens through the current migrations and holds what the report says.
        val summary = databaseSummary(e.databases)
        assertTrue(summary.keys.containsAll(listOf("bookmarks.sqlite3", "workspaces.sqlite3", "settings.sqlite3")), summary.keys.toString())
        for (entry in migrated.report.entries.filterIsInstance<net.bible.service.legacymigration.map.ReportEntry.Written>()) {
            assertEquals(entry.rows, summary.getValue(fileOf(entry.category)).getValue(entry.table), "${entry.category}.${entry.table}")
        }
        reopenAll(e)
        assertTrue(File(e.migrationDir.toString(), "done.json").isFile)
        assertTrue(File(e.migrationDir.toString(), "report.json").isFile)
        for (leftover in listOf("state.json", "work", "staging", "replaced")) assertFalse(File(e.migrationDir.toString(), leftover).exists(), leftover)
        assertFalse(File(e.platform.documents.toString(), "search_indexes.sqlite").exists())
        assertFalse(File(e.platform.documents.toString(), "search_indexes.sqlite-wal").exists())
        // The AI key went to the new secret store, never into the files the migration writes.
        assertTrue(migrated.extras.aiKeys.isNotEmpty())
        for ((id, key) in migrated.extras.aiKeys) assertEquals(key, e.platform.secrets.get(apiKeyName(id)))
        val leaks = File(e.migrationDir.toString()).walkTopDown().filter { it.isFile }.filter { it.readText().contains("test-ai-key-not-a-secret") || it.readText().contains("test-gdrive-password") }
        assertEquals(emptyList(), leaks.toList())
        assertEquals(before, e.sourceHashes(), "the source files are never modified")
        // The marker records the source files with their sizes.
        val marker = Json.parseToJsonElement(File(e.migrationDir.toString(), "done.json").readText()).jsonObject
        val sources = marker.getValue("sources").jsonArray.associate { it.jsonObject.getValue("path").jsonPrimitive.content to it.jsonObject.getValue("size").jsonPrimitive.content.toLong() }
        assertTrue(sources.keys.any { it.endsWith("AndBible.store") } && sources.keys.any { it.endsWith("LocalStore.store-wal") })
        for ((path, size) in sources) assertEquals(File(path).length(), size, path)
    }

    @Test
    fun secondRunIsAlreadyDone() {
        val e = env()
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val adopted = databaseSummary(e.databases)
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertEquals(adopted, databaseSummary(e.databases))
    }

    @Test
    fun everyStepFailsThenACleanRunGivesTheSameResult() {
        for (step in Step.entries) {
            val e = env()
            val failed = assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, failAt = step), "step $step")
            assertEquals(1, failed.attempt, "step $step")
            assertEquals(emptyList(), e.databaseFiles(), "a failed run leaves no adopted database ($step)")
            assertFalse(File(e.migrationDir.toString(), "done.json").exists(), "step $step")
            assertTrue(File(e.migrationDir.toString(), "report.json").isFile, "a failure leaves a report for diagnosis ($step)")
            assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform), "retry after $step")
            assertEquals(baseline, databaseSummary(e.databases), "contents after retry of $step")
            assertTrue(File(e.migrationDir.toString(), "done.json").isFile)
        }
    }

    @Test
    fun killAfterFirstMoveRollsBackAndRetries() {
        val e = env()
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, failAt = Step.ADOPT_FIRST_MOVE))
        assertEquals(Kind.ADOPTION_FAILED, failed.kind)
        assertEquals(emptyList(), e.databaseFiles(), "the databases directory is restored to empty")
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals(baseline, databaseSummary(e.databases))
    }

    @Test
    fun twoFailuresThenGiveUp() {
        val e = env()
        assertEquals(1, assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, Step.MAP)).attempt)
        assertEquals(2, assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, Step.MAP)).attempt)
        // The third start tries nothing, whatever the hook says.
        val third = run(e.platform)
        assertEquals(LegacyMigrationOutcome.GaveUp(2, Kind.CORRUPT_STORE), third)
        assertEquals(emptyList(), e.databaseFiles())
        assertFalse(File(e.migrationDir.toString(), "work").exists())
        assertFalse(File(e.migrationDir.toString(), "staging").exists())
        assertEquals(LegacyMigrationOutcome.GaveUp(2, Kind.CORRUPT_STORE), run(e.platform))
    }

    @Test
    fun validatorRejectsRowCountMismatch() {
        val e = env()
        val tamper = object : CategoryMapper {
            override val category = LegacyCategory.REPOSITORIES
            override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) {
                // After the bookmarks mapper: one staged bookmark disappears behind the report's back.
                staging.bookmarks.useWriterConnection2("DELETE FROM BibleBookmark WHERE id = (SELECT id FROM BibleBookmark LIMIT 1)")
            }
        }
        val ordered = defaultMappers(e.platform) + tamper
        val outcome = runBlocking { migrator(e.platform, ordered).run() }
        val failed = assertIs<LegacyMigrationOutcome.Failed>(outcome)
        assertEquals(Kind.VALIDATION_FAILED, failed.kind)
        assertEquals(emptyList(), e.databaseFiles(), "nothing adopted")
    }

    @Test
    fun existingNonEmptyDatabasesBlockAdoption() {
        val e = env()
        val existing = File(e.databases.toString(), "workspaces.sqlite3")
        writeSqlite(existing, rows = 3)
        val bytes = existing.readBytes()
        val outcome = run(e.platform)
        val failed = assertIs<LegacyMigrationOutcome.Failed>(outcome)
        assertEquals(Kind.ADOPTION_FAILED, failed.kind)
        assertTrue(bytes.contentEquals(existing.readBytes()), "the user's database is untouched")
        assertEquals(listOf("workspaces.sqlite3"), e.databaseFiles(), "no other database was adopted either")
        assertFalse(File(e.migrationDir.toString(), "done.json").exists())
    }

    // ---- replace mode ---------------------------------------------------------------------------------------------

    private fun beforeImportDirs(e: Env): List<File> =
        File(e.migrationDir.toString()).listFiles { f -> f.name.startsWith("before-import-") }.orEmpty().toList()

    @Test
    fun replaceModeReplacesNonEmptyAndKeepsTheOldFiles() {
        val e = env()
        writeSqlite(File(e.databases.toString(), "bookmarks.sqlite3"), rows = 3)
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform, replaceExisting = true))
        val kept = beforeImportDirs(e).single()
        assertEquals(3, countRows(File(kept, "bookmarks.sqlite3")))
        assertFalse(File(e.migrationDir.toString(), "replaced").exists(), "replaced/ was renamed, not left behind")
        assertEquals(baseline, databaseSummary(e.databases))
    }

    @Test
    fun replaceModeKeepsTheBackupWhenARelaunchRetriesTheRename() {
        val e = env()
        writeSqlite(File(e.databases.toString(), "bookmarks.sqlite3"), rows = 3)
        var failures = 0
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(source: Path, target: Path) {
                if (source.name == "replaced" && failures++ == 0) throw java.io.IOException("injected one-time rename failure")
                super.atomicMove(source, target)
            }
        }
        assertIs<LegacyMigrationOutcome.Migrated>(runBlocking { migrator(PlatformWithFileSystem(e.platform, fs), replaceExisting = true).run() })
        assertEquals(1, failures)
        assertTrue(File(e.migrationDir.toString(), "replaced").isDirectory, "the failed rename left replaced/")
        assertEquals(emptyList(), beforeImportDirs(e))
        // A later launch (default mode) retries the rename: the marker says the backup belongs to an import.
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertFalse(File(e.migrationDir.toString(), "replaced").exists())
        assertEquals(3, countRows(File(beforeImportDirs(e).single(), "bookmarks.sqlite3")))
    }

    @Test
    fun replaceModeMovesSideFilesAndRollbackRestoresThem() {
        val e = env()
        val target = File(e.databases.toString(), "bookmarks.sqlite3")
        writeSqlite(target, rows = 3)
        val wal = File(e.databases.toString(), "bookmarks.sqlite3-wal")
        wal.writeBytes(ByteArray(64) { it.toByte() })
        val targetBytes = target.readBytes()
        val walBytes = wal.readBytes()
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, Step.ADOPT_DONE, replaceExisting = true))
        assertTrue(failed.rolledBack)
        assertTrue(targetBytes.contentEquals(target.readBytes()), "the database is back byte-identical")
        assertTrue(wal.isFile && walBytes.contentEquals(wal.readBytes()), "the -wal is back byte-identical")
        assertEquals(emptyList(), beforeImportDirs(e))
    }

    @Test
    fun withoutReplaceModeNonEmptyStillFails() {
        val e = env()
        writeSqlite(File(e.databases.toString(), "bookmarks.sqlite3"), rows = 3)
        assertEquals(Kind.ADOPTION_FAILED, assertIs<LegacyMigrationOutcome.Failed>(run(e.platform)).kind)
    }

    @Test
    fun stepHookRunsInStepOrder() {
        val e = env()
        val seen = mutableListOf<Step>()
        assertIs<LegacyMigrationOutcome.Migrated>(runBlocking { migrator(e.platform, stepHook = { seen += it }).run() })
        assertEquals(Step.entries.toList(), seen)
    }

    @Test
    fun extrasFileIsWrittenBeforeTheCommitMove() {
        val e = env()
        val extras = File(e.migrationDir.toString(), "extras.json")
        var existedAtCommit: Boolean? = null
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(source: Path, target: Path) {
                if (target.name == "done.json") existedAtCommit = extras.isFile
                super.atomicMove(source, target)
            }
        }
        assertIs<LegacyMigrationOutcome.Migrated>(run(PlatformWithFileSystem(e.platform, fs)))
        assertEquals(true, existedAtCommit, "extras.json must exist when done.json is committed")
    }

    // ---- existing databases: what may be replaced ---------------------------------------------------------------

    @Test
    fun existingEmptyDatabasesAreReplacedAndTheirBackupRemoved() {
        val e = env()
        createEmptyRoomDatabase(File(e.databases.toString(), "bookmarks.sqlite3"))
        File(e.databases.toString(), "settings.sqlite3").writeBytes(ByteArray(0)) // an empty file is empty too
        var replacedExistedBeforeCleanUp = false
        val outcome = runBlocking {
            migrator(e.platform, stepHook = { if (it == Step.ADOPT_DONE) replacedExistedBeforeCleanUp = File(e.migrationDir.toString(), "replaced").isDirectory }).run()
        }
        assertIs<LegacyMigrationOutcome.Migrated>(outcome)
        assertTrue(replacedExistedBeforeCleanUp, "the backup really existed, so its absence below is cleanUp's work")
        assertEquals(baseline, databaseSummary(e.databases))
        assertFalse(File(e.migrationDir.toString(), "replaced").exists())
        assertEquals(emptyList(), beforeImportDirs(e), "a normal migration keeps no before-import directory")
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertEquals(emptyList(), beforeImportDirs(e))
    }

    @Test
    fun anUnreadableExistingDatabaseBlocksAdoption() {
        val e = env()
        val junk = File(e.databases.toString(), "bookmarks.sqlite3")
        junk.writeText("this is not a database, but it is somebody's file")
        val bytes = junk.readBytes()
        assertEquals(Kind.ADOPTION_FAILED, assertIs<LegacyMigrationOutcome.Failed>(run(e.platform)).kind)
        assertTrue(bytes.contentEquals(junk.readBytes()))
    }

    @Test
    fun anEmptyDatabaseWithAHotJournalBlocksAdoption() {
        val e = env()
        val db = File(e.databases.toString(), "bookmarks.sqlite3")
        createEmptyRoomDatabase(db)
        File(db.path + "-wal").writeText("pending frames")
        assertEquals(Kind.ADOPTION_FAILED, assertIs<LegacyMigrationOutcome.Failed>(run(e.platform)).kind)
        assertTrue(File(db.path + "-wal").exists())
    }

    // ---- extras and secrets before the commit -----------------------------------------------------------------

    private val aiKeyValue = "test-ai-key-not-a-secret"
    private val passwordValue = "test-gdrive-password-not-a-secret"

    private fun richAiKeyNames(): List<String> {
        val e = env()
        return assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform)).extras.aiKeys.keys.map { apiKeyName(it) }
    }

    /** Models a kill right before the `done.json` rename (the commit point). */
    private class KillBeforeDoneFileSystem(delegate: FileSystem) : okio.ForwardingFileSystem(delegate) {
        override fun atomicMove(source: Path, target: Path) {
            if (target.name == "done.json") throw SimulatedKill("killed before the done.json rename")
            super.atomicMove(source, target)
        }
    }

    /** Delegates to [delegate] and notes, at the first `put`, which of [watched] were present. */
    private class ObservingSecrets(private val delegate: net.bible.sharedcore.platform.secrets.SecretStore, private val watched: List<String>) : net.bible.sharedcore.platform.secrets.SecretStore {
        var presentAtFirstPut: List<String>? = null
        override fun get(key: String) = delegate.get(key)
        override fun put(key: String, value: String) {
            if (presentAtFirstPut == null) presentAtFirstPut = watched.filter { delegate.get(it) != null }
            delegate.put(key, value)
        }
        override fun remove(key: String) = delegate.remove(key)
    }

    private class PlatformWithSecrets(platform: LegacyPlatform, override val secrets: net.bible.sharedcore.platform.secrets.SecretStore) : LegacyPlatform by platform

    @Test
    fun extrasFileExistsBeforeCommitAndSurvivesCleanup() {
        val failing = env()
        assertIs<LegacyMigrationOutcome.Failed>(run(failing.platform, Step.ADOPT_DONE))
        assertFalse(File(failing.migrationDir.toString(), "extras.json").exists(), "extras.json is written only after the secrets")

        val e = env()
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val extrasFile = File(e.migrationDir.toString(), "extras.json")
        val done = File(e.migrationDir.toString(), "done.json")
        assertTrue(extrasFile.isFile && done.isFile)
        val text = extrasFile.readText()
        assertFalse(text.contains(aiKeyValue) || text.contains(passwordValue), "no secrets in extras.json")
        assertTrue(LegacyExtrasFile.decode(text).sync?.hadPassword == true)
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertTrue(extrasFile.isFile, "cleanUp keeps extras.json")
    }

    @Test
    fun nextcloudPasswordStoredUnderAndroidKey() {
        val e = env()
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals(passwordValue, e.platform.secrets.get("cloud_sync_password"))
    }

    @Test
    fun existingSecretIsKeptAndReported() {
        val name = richAiKeyNames().first()
        val e = env()
        e.platform.secrets.put(name, "user")
        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals("user", e.platform.secrets.get(name))
        assertTrue(
            migrated.report.entries.any { it is net.bible.service.legacymigration.map.ReportEntry.Skipped && it.category == LegacyCategory.SETTINGS && it.reason == "secret already present: llm_api_key_\u2026" },
            migrated.report.entries.toString(),
        )
    }

    @Test
    fun killAfterSecretsRollsBackSecrets() {
        val names = richAiKeyNames() + "cloud_sync_password"
        val e = env()
        assertFailsWith<SimulatedKill> { run(PlatformWithFileSystem(e.platform, KillBeforeDoneFileSystem(FileSystem.SYSTEM))) }
        assertEquals(aiKeyValue, e.platform.secrets.get(names.first()), "the killed run had written the secrets")
        assertEquals(passwordValue, e.platform.secrets.get("cloud_sync_password"))

        val observing = ObservingSecrets(e.platform.secrets, names)
        assertIs<LegacyMigrationOutcome.Migrated>(run(PlatformWithSecrets(e.platform, observing)))
        assertEquals(emptyList(), observing.presentAtFirstPut, "the rollback removed the earlier secrets before the new ones were written")
        for (name in names) assertNotNull(e.platform.secrets.get(name), name)
    }

    @Test
    fun inProcessFailureAfterSecretsRemovesThem() {
        val names = richAiKeyNames() + "cloud_sync_password"
        val e = env()
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, Step.SECRETS))
        assertTrue(failed.rolledBack)
        for (name in names) assertEquals(null, e.platform.secrets.get(name), name)
        assertEquals(emptyList(), e.databaseFiles())
    }

    // ---- crash windows -----------------------------------------------------------------------------------------

    /** Runs a clean migration and returns how many `atomicMove`s it made (state writes and database moves). */
    private fun cleanMoveCount(withExisting: Boolean): Int {
        val e = env()
        if (withExisting) createEmptyRoomDatabase(File(e.databases.toString(), "bookmarks.sqlite3"))
        val fs = CountingFileSystem(FileSystem.SYSTEM)
        assertIs<LegacyMigrationOutcome.Migrated>(run(PlatformWithFileSystem(e.platform, fs)))
        return fs.moves
    }

    private fun crashEverywhere(withExisting: Boolean) {
        val total = cleanMoveCount(withExisting)
        assertTrue(total >= 15, "expected state writes and database moves, got $total")
        for (n in 1..total) for (after in listOf(false, true)) {
            val label = "move #$n ${if (after) "after" else "before"} (existing=$withExisting)"
            val e = env()
            if (withExisting) createEmptyRoomDatabase(File(e.databases.toString(), "bookmarks.sqlite3"))
            val crashing = PlatformWithFileSystem(e.platform, CrashingFileSystem(FileSystem.SYSTEM, n, after))
            assertFailsWith<SimulatedKill>(label) { run(crashing) }
            // The next launch recovers and finishes (attempt 2).
            val outcome = run(e.platform)
            // A kill right after the commit point (done.json) is final: the next launch only sees the marker.
            assertTrue(outcome is LegacyMigrationOutcome.Migrated || outcome == LegacyMigrationOutcome.AlreadyDone, "$label: $outcome")
            assertEquals(baseline, databaseSummary(e.databases), label)
            reopenAll(e)
            assertFalse(File(e.migrationDir.toString(), "state.json").exists(), label)
        }
    }

    @Test
    fun aKillAtAnyMoveRecoversOnTheNextLaunch() = crashEverywhere(withExisting = false)

    @Test
    fun aKillAtAnyMoveRecoversWhenAnEmptyDatabaseWasReplaced() = crashEverywhere(withExisting = true)

    @Test
    fun rollbackRestoresReplacedEmptyDatabasesAndLeavesNoHalfMovedFiles() {
        val total = cleanMoveCount(withExisting = true)
        for (n in 1..total) for (after in listOf(false, true)) {
            val label = "move #$n ${if (after) "after" else "before"}"
            val e = env()
            val original = File(e.databases.toString(), "bookmarks.sqlite3")
            createEmptyRoomDatabase(original)
            val originalBytes = original.readBytes()
            // First launch dies at the move, the second dies at the same move again: that is two attempts.
            assertFailsWith<SimulatedKill>(label) { run(PlatformWithFileSystem(e.platform, CrashingFileSystem(FileSystem.SYSTEM, n, after))) }
            if (!File(e.migrationDir.toString(), "done.json").exists()) {
                // The second launch rolls the first back, then dies at the same move again (a kill after the commit point is final).
                runCatching { run(PlatformWithFileSystem(e.platform, CrashingFileSystem(FileSystem.SYSTEM, n, after))) }.exceptionOrNull()?.let { if (it !is SimulatedKill) throw it }
            }
            val outcome = run(e.platform)
            if (outcome == LegacyMigrationOutcome.AlreadyDone) {
                assertEquals(baseline, databaseSummary(e.databases), label)
            } else if (outcome is LegacyMigrationOutcome.GaveUp) {
                // Giving up still undoes a half adoption: the user's (empty) file is back, nothing else is there.
                assertEquals(listOf("bookmarks.sqlite3"), e.databaseFiles(), label)
                assertTrue(originalBytes.contentEquals(original.readBytes()), label)
            } else {
                assertIs<LegacyMigrationOutcome.Migrated>(outcome, label)
            }
        }
    }

    @Test
    fun aKillDuringTheRollbackItselfIsRepeatable() {
        for (after in listOf(false, true)) {
            val e = env()
            createEmptyRoomDatabase(File(e.databases.toString(), "bookmarks.sqlite3"))
            // Moves: 1-3 state writes, 4-5 workspaces + its record, 6 bookmarks' original moved aside, 7 bookmarks moved in.
            assertFailsWith<SimulatedKill> { run(PlatformWithFileSystem(e.platform, CrashingFileSystem(FileSystem.SYSTEM, 7, true))) }
            // The recovery's first atomicMove is the restore of the original; the kill hits it.
            assertFailsWith<SimulatedKill>("after=$after") { run(PlatformWithFileSystem(e.platform, CrashingFileSystem(FileSystem.SYSTEM, 1, after))) }
            assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform), "after=$after")
            assertEquals(baseline, databaseSummary(e.databases))
            assertFalse(File(e.migrationDir.toString(), "replaced").exists())
        }
    }

    @Test
    fun aRolledBackStateNeverRollsBackAgain() {
        // After a GaveUp, the user starts without old data and the app creates its own databases; a later launch must not delete them.
        val e = env()
        assertFailsWith<SimulatedKill> { run(PlatformWithFileSystem(e.platform, CrashingFileSystem(FileSystem.SYSTEM, 5, true))) }
        assertIs<LegacyMigrationOutcome.Failed>(run(e.platform, failAt = Step.MAP))
        assertEquals(LegacyMigrationOutcome.GaveUp(2, Kind.CORRUPT_STORE), run(e.platform))
        val own = File(e.databases.toString(), "workspaces.sqlite3")
        writeSqlite(own, rows = 2)
        val bytes = own.readBytes()
        assertEquals(LegacyMigrationOutcome.GaveUp(2, Kind.CORRUPT_STORE), run(e.platform))
        assertTrue(bytes.contentEquals(own.readBytes()), "the app's own database survives later launches")
    }

    // ---- the commit point -------------------------------------------------------------------------------------

    @Test
    fun aCleanupErrorAfterTheCommitPointKeepsTheMigration() {
        val e = env()
        File(e.platform.documents.toString(), "search_indexes.sqlite").writeText("index")
        val outcome = run(PlatformWithFileSystem(e.platform, FailingCleanupFileSystem(FileSystem.SYSTEM)))
        assertIs<LegacyMigrationOutcome.Migrated>(outcome)
        assertEquals(baseline, databaseSummary(e.databases), "the adopted databases are not rolled back")
        assertTrue(File(e.migrationDir.toString(), "done.json").isFile)
        assertTrue(File(e.migrationDir.toString(), "work").exists(), "the failed cleanup left its leftovers")
        assertFalse(File(e.platform.documents.toString(), "search_indexes.sqlite").exists(), "the index is deleted after the commit")
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        for (leftover in listOf("state.json", "work", "staging", "replaced")) assertFalse(File(e.migrationDir.toString(), leftover).exists(), leftover)
        assertEquals(baseline, databaseSummary(e.databases))
    }

    @Test
    fun aFailingRollbackIsReportedCountedAndRepeatedOnTheNextLaunch() {
        val e = env()
        val fs = FailingDeleteFileSystem(FileSystem.SYSTEM, e.databases)
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(PlatformWithFileSystem(e.platform, fs), failAt = Step.ADOPT_DONE))
        assertFalse(failed.rolledBack, "the databases directory still holds adopted files")
        assertTrue(e.databaseFiles().isNotEmpty(), "files: ${e.databaseFiles()}")
        // The next launch cannot roll back either: that is attempt 2, reported as Failed, never an exception.
        val second = assertIs<LegacyMigrationOutcome.Failed>(run(PlatformWithFileSystem(e.platform, fs)))
        assertEquals(Kind.ADOPTION_FAILED, second.kind)
        assertEquals(2, second.attempt)
        assertFalse(second.rolledBack)
        // Once the files can be deleted the rollback completes and the budget is spent: GaveUp, databases directory clean.
        fs.enabled = false
        assertEquals(LegacyMigrationOutcome.GaveUp(2, Kind.ADOPTION_FAILED), run(PlatformWithFileSystem(e.platform, fs)))
        assertEquals(emptyList(), e.databaseFiles())
    }

    @Test
    fun theSourceFilesIncludingDocumentsAndThePreservedAiFileAreNeverModified() {
        val e = env("1.0.2", "26", "preserved-ai", preserved = true)
        val documents = File(e.platform.documents.toString())
        File(documents, "jsword/readingplan").mkdirs()
        File(documents, "jsword/readingplan/mine.properties").writeText("1=Gen.1")
        File(documents, "sword_install").mkdirs()
        File(documents, "sword_install/CustomRepositories.json").writeText("[]")
        File(documents, "search_indexes.sqlite").writeText("index")
        fun documentHashes() = hashTree(e.platform.documents).filterKeys { !it.startsWith("search_indexes") }
        val appSupport = e.sourceHashes()
        val docs = documentHashes()
        assertTrue(appSupport.keys.any { it.endsWith("PreservedDatabases/ai_settings.sqlite3") })
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals(appSupport, e.sourceHashes())
        assertEquals(docs, documentHashes())
    }

    @Test
    fun aFailureAfterTheDoneJsonRenameStillCountsAsCommitted() {
        val e = env()
        val outcome = run(PlatformWithFileSystem(e.platform, ThrowAfterDoneMoveFileSystem(FileSystem.SYSTEM)))
        assertIs<LegacyMigrationOutcome.Migrated>(outcome)
        assertEquals(baseline, databaseSummary(e.databases), "no rollback once done.json exists")
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
    }

    @Test
    fun theSearchIndexDeletionIsRetriedOnceThenStops() {
        val e = env()
        val index = File(e.platform.documents.toString(), "search_indexes.sqlite")
        index.writeText("index")
        // The first deletion fails (best-effort after the commit point): the marker records no deletion.
        val fs = FailingDeleteFileSystem(FileSystem.SYSTEM, e.platform.documents)
        assertIs<LegacyMigrationOutcome.Migrated>(run(PlatformWithFileSystem(e.platform, fs)))
        assertTrue(index.exists())
        val marker = File(e.migrationDir.toString(), "done.json")
        assertTrue(marker.readText().contains("\"searchIndexDeleted\":false"))
        // The next launch retries and succeeds, and the marker says so.
        fs.enabled = false
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertFalse(index.exists())
        assertTrue(marker.readText().contains("\"searchIndexDeleted\":true"))
        // From then on nothing is deleted any more.
        index.writeText("a file that is not the old index any more")
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertTrue(index.exists())
    }

    /** One old-app EPUB in `Documents/epub` (published generation) that the file adoption must copy to `Documents/sword/epub/Book.epub`. */
    private fun oldEpub(platform: FakeLegacyPlatform) {
        val gen = File(platform.documents.toString(), "epub/.epub-generations/Book-1/g1")
        File(gen, "package/OEBPS").mkdirs()
        File(gen, "package/OEBPS/content.opf").writeText("opf")
        val db = net.bible.service.db.openSqlite(File(gen, "index.sqlite3").path)
        try {
            db.prepare("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)").use { it.step() }
            db.prepare("INSERT INTO metadata VALUES ('source_file_name', 'Book.epub')").use { it.step() }
        } finally { db.close() }
        File(platform.documents.toString(), "epub/Book-1.epub-current.json")
            .writeText("""{"schemaVersion":1,"identifier":"Book-1","generationIdentifier":"g1"}""")
    }

    @Test
    fun theEpubLibraryIsAdoptedAfterTheCommitAndRecordedOnce() {
        val e = env()
        oldEpub(e.platform)
        val adopted = File(e.platform.documents.toString(), "sword/epub/Book.epub/OEBPS/content.opf")
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals("opf", adopted.readText())
        assertTrue(File(e.platform.documents.toString(), "epub/.epub-generations/Book-1/g1/package/OEBPS/content.opf").exists(), "the old files stay")
        val marker = File(e.migrationDir.toString(), "done.json")
        assertTrue(marker.readText().contains("\"filesAdopted\":true"))
        // Flagged: a later launch does not look at the library again.
        adopted.delete()
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertFalse(adopted.exists())
    }

    @Test
    fun aFailedEpubAdoptionNeverFailsTheMigrationAndIsRetriedOnTheNextLaunch() {
        val e = env()
        oldEpub(e.platform)
        // `sword/epub` is a regular file, so the copy cannot be placed: the adoption fails, the migration still commits.
        val blocker = File(e.platform.documents.toString(), "sword/epub").also { it.parentFile.mkdirs(); it.writeText("in the way") }
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val marker = File(e.migrationDir.toString(), "done.json")
        assertTrue(marker.readText().contains("\"filesAdopted\":false"))
        blocker.delete()
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertEquals("opf", File(e.platform.documents.toString(), "sword/epub/Book.epub/OEBPS/content.opf").readText())
        assertTrue(marker.readText().contains("\"filesAdopted\":true"))
    }

    @Test
    fun aTransientIndexErrorKeepsFilesAdoptedUnflaggedAndACorruptOneFlagsIt() {
        val e = env()
        oldEpub(e.platform)
        val index = File(e.platform.documents.toString(), "epub/.epub-generations/Book-1/g1/index.sqlite3")
        val good = index.readBytes()
        index.delete(); index.mkdirs() // cannot be opened: transient
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val marker = File(e.migrationDir.toString(), "done.json")
        assertTrue(marker.readText().contains("\"filesAdopted\":false"))
        index.deleteRecursively(); index.writeBytes("not a database at all, just text".toByteArray()) // corrupt: permanent
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        assertTrue(marker.readText().contains("\"filesAdopted\":true"))
        assertFalse(File(e.platform.documents.toString(), "sword/epub/Book.epub").exists())
        assertTrue(good.isNotEmpty())
    }

    @Test
    fun aNothingToMigrateMarkerNeverAdoptsEpubs() {
        val platform = FakeLegacyPlatform.from(fixture("1.0.1", "17", "fresh"))
        oldEpub(platform)
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, run(platform))
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(platform))
        assertFalse(File(platform.documents.toString(), "sword/epub").exists())
    }

    @Test
    fun aNothingToMigrateMarkerNeverDeletesTheSearchIndex() {
        val platform = FakeLegacyPlatform.from(fixture("1.0.1", "17", "fresh"))
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, run(platform))
        val index = File(platform.documents.toString(), "search_indexes.sqlite").also { it.writeText("x") }
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(platform))
        assertTrue(index.exists())
    }

    @Test
    fun unknownFingerprintFailsAndLeavesDatabasesUntouched() {
        val e = env()
        makeModelUnknown(File(e.platform.applicationSupport.toString(), "AndBible.store"))
        val before = e.sourceHashes()
        val first = assertIs<LegacyMigrationOutcome.Failed>(run(e.platform))
        assertEquals(Kind.UNKNOWN_MODEL, first.kind)
        assertEquals(1, first.attempt)
        assertTrue(first.rolledBack)
        assertEquals(emptyList(), e.databaseFiles())
        assertEquals(2, assertIs<LegacyMigrationOutcome.Failed>(run(e.platform)).attempt)
        assertEquals(LegacyMigrationOutcome.GaveUp(2, Kind.UNKNOWN_MODEL), run(e.platform))
        assertEquals(emptyList(), e.databaseFiles())
        assertEquals(before, e.sourceHashes(), "the source is never modified")
    }

    @Test
    fun anUnreadableKeychainFailsTheAttemptInsteadOfMigratingWithoutKeys() {
        val e = env()
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(ThrowingKeychainPlatform(e.platform)))
        assertTrue(failed.rolledBack)
        assertEquals(Kind.KEYCHAIN_UNAVAILABLE, failed.kind)
        assertEquals(emptyList(), e.databaseFiles(), "nothing adopted")
        assertFalse(File(e.migrationDir.toString(), "done.json").exists())
        val report = File(e.migrationDir.toString(), "report.json").readText()
        assertTrue(report.contains("Keychain unavailable"), report)
        assertFalse(report.contains("secret-leak-check-4711"), "the platform's message is not echoed")
        assertFalse(report.contains("test-ai-key-not-a-secret"))
    }

    // ---- rows the mappers drop or synthesize by design must not fail validation ----------------------------------

    private fun store(e: Env) = File(e.platform.applicationSupport.toString(), "AndBible.store")

    @Test
    fun aHiddenBookmarkWithTwoLabelsAndANoteIsMigratedNotRejected() {
        val e = env()
        // The bookmark with the most label links becomes untrusted: the mapper hides it with its links and its note.
        execSql(
            store(e),
            "UPDATE ZBIBLEBOOKMARK SET ZORDINALTRUSTSTATERAW = 'legacyPendingModule' WHERE Z_PK = " +
                "(SELECT ZBOOKMARK FROM ZBIBLEBOOKMARKTOLABEL GROUP BY ZBOOKMARK ORDER BY COUNT(*) DESC LIMIT 1)",
        )
        val outcome = run(e.platform)
        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(outcome)
        val skips = migrated.report.entries.filterIsInstance<net.bible.service.legacymigration.map.ReportEntry.Skipped>()
        assertTrue(skips.any { it.table == "BibleBookmarkToLabel" && it.rows >= 2 }, "the hidden links are counted: $skips")
        assertTrue(skips.any { it.table == "BibleBookmarkNotes" && it.rows >= 1 }, "the hidden note is counted: $skips")
        val summary = databaseSummary(e.databases).getValue("bookmarks.sqlite3")
        assertEquals(2, summary.getValue("BibleBookmark"))
    }

    @Test
    fun aWindowWithoutAPageManagerRowGetsASynthesizedOneAndMigrates() {
        val e = env()
        execSql(store(e), "UPDATE ZWINDOW SET ZPAGEMANAGER = NULL WHERE Z_PK = (SELECT MIN(Z_PK) FROM ZWINDOW)",
            "DELETE FROM ZPAGEMANAGER WHERE Z_PK = (SELECT MIN(Z_PK) FROM ZPAGEMANAGER)")
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val ws = databaseSummary(e.databases).getValue("workspaces.sqlite3")
        assertEquals(ws.getValue("Window"), ws.getValue("PageManager"))
    }

    @Test
    fun twoWindowsSharingOnePageManagerRowMigrateAndTheOrphanIsReported() {
        val e = env()
        execSql(store(e), "UPDATE ZWINDOW SET ZPAGEMANAGER = (SELECT MIN(Z_PK) FROM ZPAGEMANAGER) WHERE Z_PK = (SELECT MIN(Z_PK) FROM ZWINDOW)")
        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val ws = databaseSummary(e.databases).getValue("workspaces.sqlite3")
        assertEquals(ws.getValue("Window"), ws.getValue("PageManager"))
        // The legacy page manager the last window used to own is now used by nobody: reported, not silent.
        val skips = migrated.report.entries.filterIsInstance<net.bible.service.legacymigration.map.ReportEntry.Skipped>()
        assertTrue(skips.any { it.table == "PageManager" && it.rows == 1 && it.reason.startsWith("page manager without") }, skips.toString())
    }

    @Test
    fun twoHistoryRowsWithOneAliasedUuidMigrateAndTheDuplicateIsReported() {
        val e = env()
        val local = File(e.platform.applicationSupport.toString(), "LocalStore.store")
        fun uuidText(pk: String) = "(SELECT lower(substr(hex(ZID),1,8)||'-'||substr(hex(ZID),9,4)||'-'||substr(hex(ZID),13,4)||'-'||substr(hex(ZID),17,4)||'-'||substr(hex(ZID),21,12)) FROM ZHISTORYITEM WHERE Z_PK = $pk)"
        val minPk = "(SELECT MIN(Z_PK) FROM ZHISTORYITEM)"
        val maxPk = "(SELECT MAX(Z_PK) FROM ZHISTORYITEM)"
        // Two history rows now share one id, and a fidelity alias gives that id a fixed Android id: the same Room key twice.
        execSql(store(e), "UPDATE ZHISTORYITEM SET ZID = (SELECT ZID FROM ZHISTORYITEM WHERE Z_PK = $minPk) WHERE Z_PK = $maxPk")
        val alias = readText(store(e), "SELECT ${uuidText(minPk)}")
        execSql(local, "INSERT INTO ZSETTING (Z_ENT, Z_OPT, ZKEY, ZVALUE) VALUES ((SELECT Z_ENT FROM ZSETTING LIMIT 1), 1, 'remote_sync.workspaces.fidelity.history_alias.500', '$alias')")
        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        val skips = migrated.report.entries.filterIsInstance<net.bible.service.legacymigration.map.ReportEntry.Skipped>()
        assertTrue(skips.any { it.table == "HistoryItem" && it.reason == "duplicate history item id" && it.rows == 1 }, skips.toString())
        val written = migrated.report.entries.filterIsInstance<net.bible.service.legacymigration.map.ReportEntry.Written>().first { it.table == "HistoryItem" }.rows
        assertEquals(written, databaseSummary(e.databases).getValue("workspaces.sqlite3").getValue("HistoryItem"))
    }

    // ---- mapper order ---------------------------------------------------------------------------------------

    @Test
    fun defaultMappersRunWorkspacesAndReadingPlansBeforeSettings() {
        val e = env()
        val order = defaultMappers(e.platform).map { it.category }
        assertEquals(LegacyCategory.entries.toSet(), order.toSet())
        assertEquals(order.size, order.toSet().size)
        assertTrue(order.indexOf(LegacyCategory.WORKSPACES) < order.indexOf(LegacyCategory.SETTINGS))
        assertTrue(order.indexOf(LegacyCategory.READING_PLANS) < order.indexOf(LegacyCategory.SETTINGS))
    }

    @Test
    fun aReversedMapperListFailsLoudly() {
        val e = env()
        val reversed = defaultMappers(e.platform).reversed()
        assertFailsWith<IllegalArgumentException> { migrator(e.platform, reversed) }
        // And the settings mapper itself refuses to run before the workspaces mapper.
        val settingsFirst = listOf(SettingsMapper { emptyMap() }, WorkspacesMapper())
        assertFailsWith<IllegalArgumentException> { migrator(e.platform, settingsFirst) }
    }

    // ---- several apps and iOS versions --------------------------------------------------------------------------

    @Test
    fun severalFixturesMigrateAndEveryAdoptedDatabaseOpens() {
        val cases = listOf(
            Triple("1.0.1", "17", "rich"),
            Triple("1.0.2", "26", "preserved-ai"),
            Triple("1.0.2", "18", "restored-android"),
            Triple("1.0.1", "26", "fresh"),
            Triple("1.0.2", "17", "preserved-ai"),
        )
        for ((app, ios, scenario) in cases) {
            val e = env(app, ios, scenario, preserved = scenario == "preserved-ai")
            val outcome = run(e.platform)
            assertIs<LegacyMigrationOutcome.Migrated>(outcome, "$app/$ios/$scenario")
            assertTrue(e.databaseFiles().isNotEmpty(), "$app/$ios/$scenario")
            reopenAll(e)
            assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform))
        }
    }

    @Test
    fun everyFixtureMigratesAndItsAdoptedDatabasesOpen() {
        for (fx in net.bible.service.legacymigration.testsupport.allFixtures()) {
            // allFixtures() are shared per JVM: work on a private copy of the fixture directory.
            val copy = fixture(fx.app, fx.ios, fx.scenario)
            val platform = FakeLegacyPlatform.from(copy)
            installLegacyApp(copy, platform, withPreserved = File(copy.root.toString(), "PreservedDatabases/ai_settings.sqlite3").isFile)
            val e = Env(copy, platform)
            val label = "${fx.app}/${fx.ios}/${fx.scenario}"
            val outcome = run(platform)
            assertIs<LegacyMigrationOutcome.Migrated>(outcome, label)
            reopenAll(e)
        }
    }

    @Test
    fun theCloudStoreRenameRuleIsAppliedToTheCopy() {
        val e = env()
        val support = File(e.platform.applicationSupport.toString())
        // A device where only the old `CloudStore.store` name exists (DataMigration.migrateIfNeeded renames it back).
        for (f in support.listFiles { f -> f.name.startsWith("AndBible.store") }.orEmpty()) f.renameTo(File(support, f.name.replace("AndBible.store", "CloudStore.store")))
        val before = e.sourceHashes()
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals(baseline, databaseSummary(e.databases))
        assertEquals(before, e.sourceHashes(), "the source is not renamed")
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private fun fileOf(category: LegacyCategory) = when (category) {
        LegacyCategory.WORKSPACES -> "workspaces.sqlite3"
        LegacyCategory.BOOKMARKS -> "bookmarks.sqlite3"
        LegacyCategory.READING_PLANS -> "readingplans.sqlite3"
        LegacyCategory.PROGRESS -> "progress.sqlite3"
        LegacyCategory.MY_DOCUMENTS -> "mydocuments.sqlite3"
        LegacyCategory.AI_SETTINGS -> "ai_settings.sqlite3"
        LegacyCategory.SETTINGS -> "settings.sqlite3"
        LegacyCategory.REPOSITORIES -> "repositories.sqlite3"
    }

    /** Opens every adopted database through the current Room schema and migrations (a copy, so nothing in place changes). */
    private fun reopenAll(e: Env) = runBlocking {
        val copy = java.nio.file.Files.createTempDirectory("reopen-").toFile()
        try {
            for (name in e.databaseFiles().filter { it.endsWith(".sqlite3") }) File(e.databases.toString(), name).copyTo(File(copy, name))
            val staging = StagingDatabases(copy.toOkioPath(), JvmLegacyDatabaseFactory())
            try {
                for (name in e.databaseFiles().filter { it.endsWith(".sqlite3") }) {
                    val db = when (name) {
                        "bookmarks.sqlite3" -> staging.bookmarks
                        "workspaces.sqlite3" -> staging.workspaces
                        "readingplans.sqlite3" -> staging.readingPlans
                        "mydocuments.sqlite3" -> staging.myDocuments
                        "progress.sqlite3" -> staging.progress
                        "ai_settings.sqlite3" -> staging.aiSettings
                        "settings.sqlite3" -> staging.settings
                        "repositories.sqlite3" -> staging.repositories
                        else -> error("unexpected database $name")
                    }
                    db.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM sqlite_master") { it.step() } }
                }
            } finally {
                staging.close()
            }
        } finally {
            copy.deleteRecursively()
        }
    }

    private fun createEmptyRoomDatabase(file: File) = runBlocking {
        val db = JvmLegacyDatabaseFactory().bookmarks(file.path)
        try {
            db.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM sqlite_master") { it.step() } }
        } finally {
            db.close()
        }
        for (suffix in listOf("-journal", "-wal", "-shm", ".lck")) File(file.path + suffix).delete()
    }

    private suspend fun net.bible.android.database.BookmarkDatabase.useWriterConnection2(sql: String) {
        useWriterConnection { c -> c.usePrepared(sql) { it.step() } }
    }

    // ---- durable writes, Keychain kind, W10, H6 ---------------------------------------------------------------

    @Test
    fun keychainFailureIsItsOwnKind() {
        val e = env()
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(ThrowingKeychainPlatform(e.platform)))
        assertEquals(Kind.KEYCHAIN_UNAVAILABLE, failed.kind)
        assertEquals(1, failed.attempt)
    }

    @Test
    fun durableWritesSyncTempAndDirectory() {
        val e = env()
        val recording = RecordingSyncPlatform(e.platform)
        assertIs<LegacyMigrationOutcome.Migrated>(run(recording))
        val names = recording.synced.map { if (it == e.migrationDir) "<dir>" else it.name }
        fun followedByDirectory(temp: String) = names.withIndex().filter { it.value == temp }.map { names.getOrNull(it.index + 1) }
        val stateWrites = followedByDirectory("state.json.tmp")
        assertTrue(stateWrites.size >= 3, "every state write syncs its temp file: $names")
        assertTrue(stateWrites.all { it == "<dir>" }, "the directory is synced right after each rename: $names")
        // Ordering, from the directory listing at each call: the temp file exists when it is synced, and when the directory
        // is synced right after, the rename is done (temp gone, target present). Syncing the parent before the rename fails this.
        for ((i, call) in recording.calls.withIndex()) {
            if (!call.path.name.endsWith(".tmp")) continue
            val target = call.path.name.removeSuffix(".tmp")
            assertTrue(call.path.name in call.children, "${call.path.name} exists when its content is synced")
            val dirCall = recording.calls[i + 1]
            assertEquals(e.migrationDir, dirCall.path, "the directory follows ${call.path.name}")
            assertTrue(call.path.name !in dirCall.children && target in dirCall.children, "renamed before the directory sync of $target: ${dirCall.children}")
        }
        for (marker in listOf("done.json.tmp", "report.json.tmp")) {
            val writes = followedByDirectory(marker)
            assertTrue(writes.isNotEmpty() && writes.all { it == "<dir>" }, "$marker is synced, then the directory: $names")
        }
    }

    @Test
    fun adoptedDatabaseMovesAreMadeDurableByOneDirectorySyncBeforeTheDoneMarker() {
        val e = env()
        val recording = RecordingSyncPlatform(e.platform)
        assertIs<LegacyMigrationOutcome.Migrated>(run(recording))
        val databaseSyncs = recording.calls.withIndex().filter { it.value.path == e.platform.databasesDir }
        assertEquals(1, databaseSyncs.size, "the databases directory is synced once after the moves: ${recording.synced}")
        val doneSync = recording.synced.indexOfFirst { it.name == "done.json.tmp" }
        assertTrue(doneSync > databaseSyncs.single().index, "databases directory synced before the done marker is written")
        val children = databaseSyncs.single().value.children
        assertTrue(children.isNotEmpty(), "the moves already happened at the sync: $children")
    }

    @Test
    fun httpCustomRepositoryKeptVerbatim() {
        // W10: a repository whose source URL is plain http is migrated as it is; it is neither upgraded nor reported as skipped.
        val e = env()
        val sword = File(e.platform.documents.toString(), "sword_install").also { it.mkdirs() }
        File(sword, "CustomRepositories.json").writeText(
            """{"version":1,"repositories":[{"name":"Plain Http","description":"http://example.org/sword","type":"sword-https","host":"example.org",""" +
                """"catalogDirectory":"/sword","packageDirectory":"/sword/zip","manifestURL":"","sourceURL":"http://example.org/sword"}]}""",
        )
        // A sidecar record only counts for a source of InstallMgr.conf (the Swift loadSources), so the conf names it too.
        File(sword, "InstallMgr.conf").writeText("[Sources]\nHTTPSource=Plain Http|example.org|/sword\n")
        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals(emptyList(), migrated.report.entries.filterIsInstance<net.bible.service.legacymigration.map.ReportEntry.Skipped>()
            .filter { it.category == LegacyCategory.REPOSITORIES })
        val c = net.bible.service.db.openSqlite(File(e.databases.toString(), "repositories.sqlite3").path, readOnly = true)
        try {
            val s = c.prepare("SELECT name, description, host, catalogDirectory FROM CustomRepository")
            try {
                assertTrue(s.step())
                assertEquals(listOf("Plain Http", "http://example.org/sword", "example.org", "/sword"), (0..3).map { s.getText(it) })
                assertFalse(s.step(), "exactly one repository")
            } finally { s.close() }
        } finally { c.close() }
    }

    // ---- extended fixture (Task 13): fidelity rows, Boolean sentinels, custom repositories -------------------------

    private val extendedCells = listOf("1.0.2" to "26", "1.0.1" to "18")

    /** Migrates the `extended` cell and returns the adopted databases opened as staging (a copy), plus the report. */
    private fun <T> withMigratedExtended(app: String, ios: String, block: (StagingDatabases, MigrationReport, FixtureDir) -> T): T {
        val e = env(app, ios, "extended")
        val migrated = assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform), "$app/$ios")
        val copy = java.nio.file.Files.createTempDirectory("extended-").toFile()
        try {
            for (name in e.databaseFiles().filter { it.endsWith(".sqlite3") }) File(e.databases.toString(), name).copyTo(File(copy, name))
            val staging = StagingDatabases(copy.toOkioPath(), JvmLegacyDatabaseFactory())
            // The oracle comparison reads `openedDatabase`, so open what the tests look at.
            listOf(staging.repositories, staging.workspaces, staging.settings)
            try { return block(staging, migrated.report, e.fx) } finally { staging.close() }
        } finally {
            copy.deleteRecursively()
        }
    }

    @Test
    fun extendedFixtureMatchesOracle() {
        for ((app, ios) in extendedCells) {
            withMigratedExtended(app, ios) { staging, report, fx ->
                val label = "$app/$ios/extended"
                val repositories = runBlocking { compareWithOracleDetailed(fx, LegacyCategory.REPOSITORIES, staging) }
                assertEquals(emptyList(), repositories.differences, label)
                assertEquals(2, repositories.comparedRows["CustomRepository"], "$label: the oracle holds both custom repositories")
                val workspaces = runBlocking { compareWithOracleDetailed(fx, LegacyCategory.WORKSPACES, staging) }
                assertEquals(emptyList(), workspaces.differences, label)
                assertEquals(1, workspaces.comparedRows["GlobalTextDisplaySettings"], "$label: the global text display row is compared")
                assertEquals(emptyList(), report.entries.filterIsInstance<ReportEntry.UnknownKey>(), label)
            }
        }
    }

    @Test
    fun extendedBooleansLandAsSentinels() {
        val keys = SettingsMapper.DEFINITIONS.filter { it.storage == SettingsMapper.Storage.SWIFT_DATA && it.type == SettingsMapper.ValueType.BOOL }
        assertTrue(keys.size >= 26, "the ledger maps ${keys.size} Boolean settings")
        for ((app, ios) in extendedCells) {
            withMigratedExtended(app, ios) { staging, _, _ ->
                val stored = runBlocking { staging.settings.booleanSettingDao().all().associate { it.key to it.value } }
                for (def in keys) {
                    val nonDefault = def.default != "true"
                    assertEquals(nonDefault, stored[def.key], "$app/$ios ${def.key} (default ${def.default})")
                }
            }
        }
    }

    @Test
    fun extendedHttpRepositoryKeptVerbatim() {
        // W10: the repository whose source is plain http is migrated as stored; the schema has no source-URL column,
        // so what survives is name, description, host and catalog directory. Nothing is reported as skipped.
        for ((app, ios) in extendedCells) {
            withMigratedExtended(app, ios) { staging, report, _ ->
                val rows = runBlocking { staging.repositories.customRepositoryDao().all() }.associateBy { it.name }
                assertEquals(setOf("Fixture Https", "Plain Http"), rows.keys, "$app/$ios")
                val http = rows.getValue("Plain Http")
                assertEquals(listOf("http://example.org/sword", "example.org", "/sword"), listOf(http.description, http.host, http.catalogDirectory), "$app/$ios")
                assertEquals("https://repo.example.org/sword", rows.getValue("Fixture Https").description)
                assertEquals("repo.example.org", rows.getValue("Fixture Https").host)
                assertEquals(emptyList(), report.entries.filterIsInstance<ReportEntry.Skipped>().filter { it.category == LegacyCategory.REPOSITORIES }, "$app/$ios")
            }
        }
    }

    @Test
    fun userPlanFilesGoToTheGivenDir() {
        // H6: the files of Documents/jsword/readingplan are copied to the directory the caller names; the source stays.
        val e = env()
        val documents = File(e.platform.documents.toString())
        File(documents, "jsword/readingplan").mkdirs()
        File(documents, "jsword/readingplan/mine.properties").writeText("1=Gen.1")
        File(documents, "jsword/readingplan/other.properties").writeText("1=Exod.1")
        val before = hashTree(e.platform.documents)
        val migrator = LegacyMigrator(e.platform, JvmLegacyDatabaseFactory(), userPlanDir = e.platform.documents.parent!! / "manual" / "readingplan")
        assertIs<LegacyMigrationOutcome.Migrated>(runBlocking { migrator.run() })
        val target = File(documents.parentFile, "manual/readingplan")
        assertEquals("1=Gen.1", File(target, "mine.properties").readText())
        assertEquals("1=Exod.1", File(target, "other.properties").readText())
        assertEquals(listOf("mine.properties", "other.properties"), target.list().orEmpty().sorted(), "no temporary file is left")
        assertEquals(before, hashTree(e.platform.documents), "the source directory is unchanged")
    }

    @Test
    fun copiedUserPlanFilesAreSyncedThenTheirDirectory() {
        val e = env()
        val documents = File(e.platform.documents.toString())
        File(documents, "jsword/readingplan").mkdirs()
        File(documents, "jsword/readingplan/mine.properties").writeText("1=Gen.1")
        val planDir = e.platform.documents.parent!! / "manual" / "readingplan"
        val recording = RecordingSyncPlatform(e.platform)
        assertIs<LegacyMigrationOutcome.Migrated>(runBlocking { LegacyMigrator(recording, JvmLegacyDatabaseFactory(), userPlanDir = planDir).run() })
        val i = recording.synced.indexOfFirst { it.name == "mine.properties.tmp" }
        assertTrue(i >= 0, "the temp copy is synced")
        assertTrue("mine.properties.tmp" in recording.calls[i].children, "temp exists at its sync")
        assertEquals(planDir, recording.calls[i + 1].path)
        assertTrue("mine.properties.tmp" !in recording.calls[i + 1].children && "mine.properties" in recording.calls[i + 1].children, "renamed before the directory sync")
    }

    @Test
    fun aFailingSyncOfTheDoneMarkerFailsTheAttemptAndRollsBack() {
        val e = env()
        val failed = assertIs<LegacyMigrationOutcome.Failed>(run(RecordingSyncPlatform(e.platform) { it.name == "done.json.tmp" }))
        assertTrue(failed.rolledBack)
        assertFalse(File(e.migrationDir.toString(), "done.json").exists(), "no commit marker")
        assertEquals(emptyList(), e.databaseFiles(), "nothing stays adopted")
    }

    @Test
    fun aFailingDirectorySyncAfterTheDoneRenameCountsAsCommitted() {
        val e = env()
        val fs = e.platform.fileSystem
        val done = e.migrationDir / "done.json"
        // Fails only the directory sync that follows the done.json rename (the marker exists then).
        val platform = RecordingSyncPlatform(e.platform) { it == e.migrationDir && fs.exists(done) }
        assertIs<LegacyMigrationOutcome.Migrated>(run(platform))
        assertTrue(File(e.migrationDir.toString(), "done.json").exists(), "committed: the marker is there")
        assertEquals(baseline, databaseSummary(e.databases), "the adoption stays")
        assertEquals(LegacyMigrationOutcome.AlreadyDone, run(e.platform), "a later start sees the same committed state")
    }
}
