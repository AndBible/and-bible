package net.bible.service.legacymigration.control

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.bible.service.legacymigration.LegacyMigrationOutcome
import net.bible.service.legacymigration.LegacyMigrator
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.Step
import net.bible.service.legacymigration.state.MigrationPhase
import net.bible.service.legacymigration.state.MigrationState
import net.bible.service.legacymigration.testsupport.FakeLegacyPlatform
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.installLegacyApp

class LegacyMigrationControlTest {
    private fun platform(install: Boolean = true): FakeLegacyPlatform {
        val fx = fixture("1.0.1", "17", "rich")
        val platform = FakeLegacyPlatform.from(fx)
        if (install) installLegacyApp(fx, platform)
        return platform
    }

    private fun control(p: FakeLegacyPlatform) = LegacyMigrationControl(p.fileSystem, p.applicationSupport)

    private fun run(p: FakeLegacyPlatform, failAt: Step? = null) =
        runBlocking { LegacyMigrator(p, JvmLegacyDatabaseFactory(), failAt = failAt).run() }

    private fun file(c: LegacyMigrationControl, name: String) = File(c.dir.toString(), name)

    @Test
    fun declineMakesSettled() {
        val p = platform()
        val c = control(p)
        assertFalse(c.isSettled())
        c.decline(DeclineReason.START_FRESH)
        assertTrue(c.isSettled())
        val json = Json.parseToJsonElement(file(c, "declined.json").readText()).jsonObject
        assertEquals("startFresh", json.getValue("reason").jsonPrimitive.content)
        assertTrue(json.getValue("at").jsonPrimitive.long > 0L)
        c.decline(DeclineReason.RESTORED_BACKUP)
        assertEquals("restoredBackup", Json.parseToJsonElement(file(c, "declined.json").readText()).jsonObject.getValue("reason").jsonPrimitive.content)
    }

    @Test
    fun requestImportClearsDeclineAndAttempts() {
        val p = platform()
        val c = control(p)
        assertEquals(1, assertIs<LegacyMigrationOutcome.Failed>(run(p, Step.MAP)).attempt)
        assertEquals(2, assertIs<LegacyMigrationOutcome.Failed>(run(p, Step.MAP)).attempt)
        assertIs<LegacyMigrationOutcome.GaveUp>(run(p))
        c.decline(DeclineReason.RESTORED_BACKUP)
        assertFalse(c.importRequested())
        c.requestImport()
        assertFalse(file(c, "declined.json").exists())
        assertFalse(c.isSettled())
        assertTrue(c.importRequested())
        assertEquals(1, assertIs<LegacyMigrationOutcome.Failed>(run(p, Step.MAP)).attempt)
        c.clearImportRequest()
        assertFalse(c.importRequested())
    }

    @Test
    fun resetAttemptsRefusedWhileAdopting() {
        val p = platform()
        val c = control(p)
        File(c.dir.toString()).mkdirs()
        val adopting = MigrationState(MigrationPhase.ADOPTING, attempt = 1, startedAt = 5, lastKind = Kind.ADOPTION_FAILED)
        file(c, "state.json").writeText(adopting.toJson())
        val before = file(c, "state.json").readText()
        assertFalse(c.resetAttempts())
        assertEquals(before, file(c, "state.json").readText())
    }

    @Test
    fun resetAttemptsClearsAnExhaustedBudget() {
        val p = platform()
        val c = control(p)
        run(p, Step.MAP); run(p, Step.MAP)
        assertTrue(c.resetAttempts())
        assertFalse(file(c, "state.json").exists())
    }

    @Test
    fun importNotAvailableAfterARealMigration() {
        val p = platform()
        val c = control(p)
        assertTrue(c.sourcesExist())
        assertTrue(c.importAvailable())
        assertIs<LegacyMigrationOutcome.Migrated>(run(p))
        assertTrue(c.isSettled())
        assertFalse(c.importAvailable())
    }

    @Test
    fun importAvailableAfterNothingToMigrateIsFalseWithoutStores() {
        val p = platform(install = false)
        val c = control(p)
        assertFalse(c.sourcesExist())
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, run(p))
        assertTrue(c.isSettled())
        assertFalse(c.importAvailable())
    }

    @Test
    fun importAvailableAfterNothingToMigrateWhenStoresAppearLater() {
        val fx = fixture("1.0.1", "17", "rich")
        val p = FakeLegacyPlatform.from(fx)
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, run(p))
        val c = control(p)
        installLegacyApp(fx, p)
        assertTrue(c.importAvailable())
    }

    @Test
    fun requestImportOnACorruptStateFileDoesNotThrow() {
        val p = platform()
        val c = control(p)
        File(c.dir.toString()).mkdirs()
        file(c, "state.json").writeText("{ not json")
        c.requestImport()
        assertTrue(c.importRequested())
        assertFalse(file(c, "state.json").exists())
    }

    @Test
    fun requestImportAfterNothingToMigrateLetsAReplaceRunMigrate() {
        val fx = fixture("1.0.1", "17", "rich")
        val p = FakeLegacyPlatform.from(fx)
        assertEquals(LegacyMigrationOutcome.NothingToMigrate, run(p))
        installLegacyApp(fx, p)
        val c = control(p)
        c.requestImport()
        assertFalse(file(c, "done.json").exists())
        val outcome = runBlocking { LegacyMigrator(p, JvmLegacyDatabaseFactory(), replaceExisting = true).run() }
        assertIs<LegacyMigrationOutcome.Migrated>(outcome)
    }

    @Test
    fun requestImportKeepsARealMigrationsDoneMarker() {
        val p = platform()
        val c = control(p)
        assertIs<LegacyMigrationOutcome.Migrated>(run(p))
        c.requestImport()
        assertTrue(file(c, "done.json").exists())
    }

    @Test
    fun markerWritesSyncTheTempFileThenTheDirectory() {
        val p = platform()
        val synced = mutableListOf<okio.Path>()
        val c = LegacyMigrationControl(p.fileSystem, p.applicationSupport) { synced += it }
        c.decline(DeclineReason.START_FRESH)
        assertEquals(listOf("declined.json.tmp", "LegacyMigration"), synced.map { it.name })
        synced.clear()
        c.requestImport()
        assertEquals(listOf("import-request.json.tmp", "LegacyMigration"), synced.map { it.name })
    }
}
