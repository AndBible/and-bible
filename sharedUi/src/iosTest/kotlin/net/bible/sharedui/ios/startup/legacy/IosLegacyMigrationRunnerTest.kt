package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.service.cloudsync.ios.SECRET_CREDENTIAL_KEYS
import net.bible.service.legacymigration.DeviceLocalPref
import net.bible.service.legacymigration.LegacyExtras
import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationOutcome
import net.bible.service.legacymigration.LegacyPlatform
import net.bible.service.legacymigration.NEXTCLOUD_PASSWORD_SECRET
import net.bible.service.legacymigration.PrefType
import net.bible.service.legacymigration.control.LegacyGateDecision
import net.bible.service.legacymigration.control.LegacyMigrationControl
import net.bible.service.legacymigration.extras.LegacyExtrasFile
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.sharedcore.platform.secrets.SecretStore
import net.bible.sharedcore.settings.DeviceLocalPrefs
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosLegacyMigrationRunnerTest {
    private val root: Path = (NSTemporaryDirectory() + "legacy-runner-" + NSUUID().UUIDString).toPath()
    private val events = mutableListOf<String>()

    private class Flag { @kotlin.concurrent.Volatile var on = false }

    private class MemorySecrets : SecretStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private class RecordingPrefs(val events: MutableList<String>) : DeviceLocalPrefs {
        override fun getString(key: String, default: String?): String? = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun setString(key: String, value: String) { events += "prefs:$key" }
        override fun setBoolean(key: String, value: Boolean) { events += "prefs:$key" }
        override fun remove(key: String) = Unit
    }

    private fun platform(fileSystem: FileSystem): LegacyPlatform {
        val support = root / "Support"
        val documents = root / "Docs"
        FileSystem.SYSTEM.createDirectories(support)
        FileSystem.SYSTEM.createDirectories(documents)
        return object : LegacyPlatform {
            override val fileSystem = fileSystem
            override val applicationSupport = support
            override val documents = documents
            override val databasesDir = root / "databases"
            override val secrets: SecretStore = MemorySecrets()
            override fun userDefaults(): Map<String, Any?> = emptyMap()
            override fun legacyKeychain(service: String, account: String): String? = null
        }
    }

    private fun hooks(
        fileSystem: FileSystem = FileSystem.SYSTEM,
        available: () -> Boolean = { true },
        awaitChange: suspend () -> Unit = {},
        run: suspend (net.bible.service.legacymigration.LegacyMigrator) -> LegacyMigrationOutcome,
    ): RunnerHooks {
        val platform = platform(fileSystem)
        return RunnerHooks(
            platform = platform,
            control = LegacyMigrationControl(fileSystem, platform.applicationSupport),
            devicePrefs = RecordingPrefs(events),
            protectedDataAvailable = available,
            awaitProtectedDataChange = awaitChange,
            runMigrator = run,
            markSettled = { events += "settled" },
        )
    }

    /** A bug fails the test instead of hanging the native test binary. */
    private fun blocking(body: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        runBlocking { withTimeout(5_000) { body() } }

    @AfterTest fun tearDown() {
        IosLegacyMigrationRunner.testHooks = null
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    @Test fun concurrentCallersShareOneAttempt() = blocking {
        var runs = 0
        IosLegacyMigrationRunner.testHooks = hooks { runs++; delay(100); LegacyMigrationOutcome.NothingToMigrate }
        val a = async { IosLegacyMigrationRunner.run() }
        val b = async { IosLegacyMigrationRunner.run() }
        assertEquals(LegacyGateDecision.Proceed, a.await())
        assertEquals(LegacyGateDecision.Proceed, b.await())
        // A finished attempt is not repeated in this process.
        assertEquals(LegacyGateDecision.Proceed, IosLegacyMigrationRunner.run())
        assertEquals(1, runs)
    }

    @Test fun protectedDataWaitBlocksUntilAvailable() = blocking {
        val flag = Flag()
        var runs = 0
        val changes = Channel<Unit>(Channel.UNLIMITED)
        IosLegacyMigrationRunner.testHooks = hooks(available = { flag.on }, awaitChange = { changes.receive() }) {
            runs++; LegacyMigrationOutcome.NothingToMigrate
        }
        val decision = async { IosLegacyMigrationRunner.run() }
        delay(200)
        assertEquals(0, runs, "the migrator must not start while protected data is unavailable")
        // A notification while still unavailable does not release the run either.
        changes.send(Unit)
        delay(100)
        assertEquals(0, runs)
        flag.on = true
        changes.send(Unit)
        assertEquals(LegacyGateDecision.Proceed, decision.await())
        assertEquals(1, runs)
    }

    @Test fun proceedSetsSettledAfterPrefsApplied() = blocking {
        val recording = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun delete(path: Path, mustExist: Boolean) {
                if (path.name == "import-request.json") events += "clearRequest"
                super.delete(path, mustExist)
            }
        }
        val h = hooks(fileSystem = recording) { LegacyMigrationOutcome.NothingToMigrate }
        // extras.json makes the applier write a device-local pref; a pending import request must be cleared.
        recording.createDirectories(h.control.dir)
        val extras = LegacyExtras(emptyMap(), null, false, false, emptyList(), listOf(DeviceLocalPref("some_pref", PrefType.STRING, "v")))
        recording.write(h.control.dir / LegacyExtrasFile.NAME) { writeUtf8(LegacyExtrasFile.encode(extras, MigrationReport())) }
        h.control.requestImport()
        IosLegacyMigrationRunner.testHooks = h

        assertEquals(LegacyGateDecision.Proceed, IosLegacyMigrationRunner.run())
        val prefs = events.indexOf("prefs:some_pref")
        val cleared = events.indexOf("clearRequest")
        val settled = events.indexOf("settled")
        assertTrue(prefs >= 0 && cleared > prefs && settled > cleared, "order was $events")
        assertFalse(h.control.importRequested())
    }

    @Test fun recoverLeavesSettledUnset() = blocking {
        var runs = 0
        IosLegacyMigrationRunner.testHooks = hooks { runs++; LegacyMigrationOutcome.GaveUp(2, LegacyMigrationException.Kind.CORRUPT_STORE) }
        assertIs<LegacyGateDecision.Recover>(IosLegacyMigrationRunner.run())
        assertFalse("settled" in events, "events: $events")
        assertTrue(events.none { it.startsWith("prefs:") })
        // The finished Recover attempt is not repeated in this process.
        assertIs<LegacyGateDecision.Recover>(IosLegacyMigrationRunner.run())
        assertEquals(1, runs)
    }

    @Test fun applyFailureThrowsLeavesNothingSettledAndNextRunRepeats() = blocking {
        var failWrites = true
        var runs = 0
        val failing = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun sink(file: Path, mustCreate: Boolean): okio.Sink {
                if (failWrites && file.name.startsWith("applied.json")) throw okio.IOException("disk full")
                return super.sink(file, mustCreate)
            }
            override fun atomicMove(source: Path, target: Path) {
                if (failWrites && target.name.startsWith("applied.json")) throw okio.IOException("disk full")
                super.atomicMove(source, target)
            }
            override fun delete(path: Path, mustExist: Boolean) {
                if (path.name == "import-request.json") events += "clearRequest"
                super.delete(path, mustExist)
            }
        }
        val h = hooks(fileSystem = failing) { runs++; LegacyMigrationOutcome.NothingToMigrate }
        failing.createDirectories(h.control.dir)
        val extras = LegacyExtras(emptyMap(), null, false, false, emptyList(), listOf(DeviceLocalPref("some_pref", PrefType.STRING, "v")))
        failing.write(h.control.dir / LegacyExtrasFile.NAME) { writeUtf8(LegacyExtrasFile.encode(extras, MigrationReport())) }
        h.control.requestImport()
        IosLegacyMigrationRunner.testHooks = h

        val failure = runCatching { IosLegacyMigrationRunner.run() }.exceptionOrNull()
        assertTrue(failure != null, "run() must throw when the extras cannot be recorded")
        assertFalse("settled" in events, "events: $events")
        assertFalse("clearRequest" in events, "events: $events")
        assertTrue(h.control.importRequested())

        failWrites = false
        assertEquals(LegacyGateDecision.Proceed, IosLegacyMigrationRunner.run())
        assertEquals(2, runs)
        assertTrue("settled" in events)
    }

    @Test fun lostFlagWithDeclinedJsonRestoresTheFlagWithoutRunningTheMigrator() = blocking {
        var runs = 0
        val h = hooks { runs++; LegacyMigrationOutcome.NothingToMigrate }
        h.control.decline(net.bible.service.legacymigration.control.DeclineReason.START_FRESH)
        IosLegacyMigrationRunner.testHooks = h

        assertEquals(LegacyGateDecision.Proceed, IosLegacyMigrationRunner.run())

        assertEquals(0, runs, "a declined migration must not run again")
        assertEquals(listOf("settled"), events)
    }

    @Test fun anImportRequestBeatsAStaleDeclineMarker() = blocking {
        var runs = 0
        val h = hooks { runs++; LegacyMigrationOutcome.NothingToMigrate }
        h.control.decline(net.bible.service.legacymigration.control.DeclineReason.START_FRESH)
        h.control.requestImport()
        FileSystem.SYSTEM.write(h.control.dir / "declined.json") { writeUtf8("{}") } // a stale marker next to the request
        IosLegacyMigrationRunner.testHooks = h

        assertEquals(LegacyGateDecision.Proceed, IosLegacyMigrationRunner.run())
        assertEquals(1, runs)
    }

    @Test fun applyFailureAfterTheCommitProceedsWithoutTheFlagAndKeepsTheRequest() = blocking {
        val failing = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun sink(file: Path, mustCreate: Boolean): okio.Sink {
                if (file.name.startsWith("applied.json")) throw okio.IOException("disk full")
                return super.sink(file, mustCreate)
            }
            override fun atomicMove(source: Path, target: Path) {
                if (target.name.startsWith("applied.json")) throw okio.IOException("disk full")
                super.atomicMove(source, target)
            }
        }
        val h = hooks(fileSystem = failing) {
            // The real migrator has written done.json when it answers.
            failing.createDirectories(root / "Support" / "LegacyMigration")
            failing.write(root / "Support" / "LegacyMigration" / "done.json") { writeUtf8("""{"nothingToMigrate":true}""") }
            LegacyMigrationOutcome.NothingToMigrate
        }
        failing.createDirectories(h.control.dir)
        val extras = LegacyExtras(emptyMap(), null, false, false, emptyList(), listOf(DeviceLocalPref("some_pref", PrefType.STRING, "v")))
        failing.write(h.control.dir / LegacyExtrasFile.NAME) { writeUtf8(LegacyExtrasFile.encode(extras, MigrationReport())) }
        h.control.requestImport()
        IosLegacyMigrationRunner.testHooks = h

        assertEquals(LegacyGateDecision.Proceed, IosLegacyMigrationRunner.run(), "no no-exit error screen for a committed migration")
        assertFalse("settled" in events, "the flag stays unset so the next launch retries: $events")
        assertTrue(h.control.importRequested(), "the request stays until the extras are applied")
    }

    @Test fun nextcloudSecretKeyMatchesSyncPlatform() {
        assertTrue(NEXTCLOUD_PASSWORD_SECRET in SECRET_CREDENTIAL_KEYS)
    }

    private fun writeDone(h: RunnerHooks, json: String) {
        FileSystem.SYSTEM.createDirectories(h.control.dir)
        FileSystem.SYSTEM.write(h.control.dir / "done.json") { writeUtf8(json) }
    }

    @Test fun retryPostCommitRunsTheMigratorOnceWhenAFlagIsMissing() = blocking {
        var runs = 0
        val h = hooks { runs++; LegacyMigrationOutcome.AlreadyDone }
        writeDone(h, """{"nothingToMigrate":false,"searchIndexDeleted":true,"filesAdopted":false}""")
        IosLegacyMigrationRunner.testHooks = h

        IosLegacyMigrationRunner.retryPostCommit()

        assertEquals(1, runs)
    }

    @Test fun retryPostCommitDoesNothingWhenEverythingIsFlaggedOrThereIsNothingToRetry() = blocking {
        var runs = 0
        val h = hooks { runs++; LegacyMigrationOutcome.AlreadyDone }
        IosLegacyMigrationRunner.testHooks = h
        writeDone(h, """{"nothingToMigrate":false,"searchIndexDeleted":true,"filesAdopted":true}""")
        IosLegacyMigrationRunner.retryPostCommit()
        writeDone(h, """{"nothingToMigrate":true,"searchIndexDeleted":false,"filesAdopted":false}""")
        IosLegacyMigrationRunner.retryPostCommit()
        FileSystem.SYSTEM.delete(h.control.dir / "done.json")
        IosLegacyMigrationRunner.retryPostCommit()
        assertEquals(0, runs)
    }

    @Test fun retryPostCommitNeverThrows() = blocking {
        val h = hooks { throw IllegalStateException("boom") }
        writeDone(h, """{"nothingToMigrate":false,"searchIndexDeleted":false}""")
        IosLegacyMigrationRunner.testHooks = h
        IosLegacyMigrationRunner.retryPostCommit()
    }
}
