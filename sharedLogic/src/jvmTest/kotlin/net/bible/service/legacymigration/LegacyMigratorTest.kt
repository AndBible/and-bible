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
import net.bible.service.legacymigration.map.BookmarksMapper
import net.bible.service.legacymigration.map.CategoryMapper
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.map.SettingsMapper
import net.bible.service.legacymigration.map.WorkspacesMapper
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.CountingFileSystem
import net.bible.service.legacymigration.testsupport.CrashingFileSystem
import net.bible.service.legacymigration.testsupport.FailingCleanupFileSystem
import net.bible.service.legacymigration.testsupport.FailingDeleteFileSystem
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
    ) = if (mappers == null) LegacyMigrator(platform, JvmLegacyDatabaseFactory(), failAt = failAt)
    else LegacyMigrator(platform, JvmLegacyDatabaseFactory(), mappers, failAt)

    private fun run(platform: LegacyPlatform, failAt: Step? = null) = runBlocking { migrator(platform, null, failAt).run() }

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

    // ---- existing databases: what may be replaced ---------------------------------------------------------------

    @Test
    fun existingEmptyDatabasesAreReplacedAndTheirBackupRemoved() {
        val e = env()
        createEmptyRoomDatabase(File(e.databases.toString(), "bookmarks.sqlite3"))
        File(e.databases.toString(), "settings.sqlite3").writeBytes(ByteArray(0)) // an empty file is empty too
        assertIs<LegacyMigrationOutcome.Migrated>(run(e.platform))
        assertEquals(baseline, databaseSummary(e.databases))
        assertFalse(File(e.migrationDir.toString(), "replaced").exists())
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
}
