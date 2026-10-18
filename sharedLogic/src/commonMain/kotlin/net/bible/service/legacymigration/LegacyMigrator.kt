package net.bible.service.legacymigration

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.adopt.Adopter
import net.bible.service.legacymigration.adopt.StagingValidator
import net.bible.service.legacymigration.map.AiSettingsMapper
import net.bible.service.legacymigration.map.BookmarksMapper
import net.bible.service.legacymigration.map.CategoryMapper
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.map.MyDocumentsMapper
import net.bible.service.legacymigration.map.ProgressMapper
import net.bible.service.legacymigration.map.ReadingPlansMapper
import net.bible.service.legacymigration.map.RepositoriesMapper
import net.bible.service.legacymigration.map.SettingsMapper
import net.bible.service.legacymigration.map.WorkspacesMapper
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.stage.LegacyDatabaseFactory
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.state.MigrationPhase
import net.bible.service.legacymigration.state.MigrationState
import net.bible.service.legacymigration.state.MigrationStateStore
import net.bible.service.legacymigration.state.atomicWriteFile
import net.bible.service.llm.apiKeyName
import okio.FileSystem
import okio.Path

/** The stages of a run at which a test can make it fail (`failAt`), in run order. */
enum class Step {
    /** After the stores were copied into `work/`. */
    COPY,

    /** After the copies were opened and the schema maps were selected. */
    READ,

    /** After all mappers ran into `staging/`. */
    MAP,

    /** After the staging validator passed. */
    VALIDATE,

    /** After the first staged database was moved into the databases directory (and the state records it). */
    ADOPT_FIRST_MOVE,

    /** After every staged database was moved, before the secrets, the plan files, the search index and the marker. */
    ADOPT_DONE,
}

/** What [LegacyMigrator.run] decided or did. */
sealed interface LegacyMigrationOutcome {
    /** `done.json` exists: an earlier run completed. Nothing was done. */
    data object AlreadyDone : LegacyMigrationOutcome

    /** No legacy store exists (a fresh install). A completion marker was written. */
    data object NothingToMigrate : LegacyMigrationOutcome

    /**
     * The migration completed in this run. [extras] (API keys already stored by the adopter, sync settings, device-local
     * preferences, ...) is for the second migration part (I3b); it is in memory only, a later run that finds the marker
     * returns [AlreadyDone] without it.
     */
    data class Migrated(val report: MigrationReport, val extras: LegacyExtras) : LegacyMigrationOutcome

    /**
     * This attempt failed with [kind]. [attempt] counts attempts including this one. [rolledBack] is true when the
     * databases directory is back to what it was before the attempt (always the case when the failure came before the
     * adoption). When it is false a rollback itself failed: the databases directory may hold half-adopted files, the next
     * launch repeats the rollback, and the caller must NOT open the databases after this outcome.
     */
    data class Failed(val kind: Kind, val attempt: Int, val report: MigrationReport, val rolledBack: Boolean = true) : LegacyMigrationOutcome

    /** [attempts] attempts failed or were interrupted; no new attempt was started. [lastKind] is the last recorded failure, if any. */
    data class GaveUp(val attempts: Int, val lastKind: Kind?) : LegacyMigrationOutcome
}

/**
 * The mappers in their required order. `WORKSPACES` and `READING_PLANS` run before `SETTINGS` (the settings mapper
 * resolves the active workspace and the reading-plan settings against the staged databases).
 *
 * Paths follow the Swift app: the preserved Android AI file is `Application Support/AndBible/AndroidDatabaseBackup/
 * PreservedDatabases/ai_settings.sqlite3`, the custom repositories sidecar `Documents/sword_install/CustomRepositories.json`.
 * Each is given to its mapper only when it exists.
 */
fun defaultMappers(platform: LegacyPlatform): List<CategoryMapper> {
    val fs = platform.fileSystem
    val preservedAi = (platform.applicationSupport / "AndBible" / "AndroidDatabaseBackup" / "PreservedDatabases" / "ai_settings.sqlite3").takeIf { fs.exists(it) }
    val repositories = (platform.documents / "sword_install" / "CustomRepositories.json").takeIf { fs.exists(it) }
    return listOf(
        WorkspacesMapper(),
        BookmarksMapper(),
        ReadingPlansMapper(),
        ProgressMapper(),
        MyDocumentsMapper(),
        AiSettingsMapper(preservedAi),
        SettingsMapper { platform.userDefaults() },
        RepositoriesMapper(repositories, fs),
    )
}

/**
 * Migrates the stores of the legacy native Swift app into the new app's databases, crash-safely.
 *
 * Files live in `applicationSupport/LegacyMigration/`: `state.json` (phase, attempt counter), `done.json` (the completion
 * marker with the source files and their sizes), `report.json` (the report of the last run, also written on a failure; no
 * secrets), `work/` (copies of the stores), `staging/` (the new databases) and, during an adoption that replaces empty
 * databases, `replaced/`. The protocol of the adoption is described at [Adopter].
 *
 * One [run]:
 * 1. `done.json` exists: [LegacyMigrationOutcome.AlreadyDone] (leftovers of an interrupted cleanup are removed).
 * 2. A state file in phase `ADOPTING` means an earlier run died during adoption: its moves are rolled back first.
 * 3. A state file with 2 or more attempts and no marker: [LegacyMigrationOutcome.GaveUp], no automatic attempt.
 * 4. No legacy store: a "nothing to migrate" marker, [LegacyMigrationOutcome.NothingToMigrate].
 * 5. State `IN_PROGRESS` (attempt + 1, so a crashed attempt counts) -> copy the stores with their `-wal`/`-shm` into `work/`
 *    (applying `DataMigration`'s `CloudStore.store` / `AndBible.store.backup` repair to the copy) -> read -> map into
 *    `staging/` -> validate -> state `STAGED` -> adopt -> secrets, user plan files -> `done.json` (the commit point)
 *    -> best-effort: delete the search index, `work/`, `staging/`, `replaced/`, `state.json`.
 *
 * Any [Exception] in step 5 before the commit point becomes [LegacyMigrationOutcome.Failed] after the adoption (if started) was rolled back; after `done.json` was written nothing is ever rolled back and a cleanup error is swallowed. I/O errors in the start of a launch (directory, state file, rollback of an earlier attempt) also become `Failed`, and a failing rollback counts as an attempt. The
 * source files (stores, sidecars, preserved files) are only read; the single exception is the search index, deleted
 * best-effort after the commit point (spec 6.5; a leftover index is harmless and is not retried, because the new app may use the same file name). Sync settings, device-local preferences and the other extras are not applied here; they are
 * returned in [LegacyMigrationOutcome.Migrated.extras].
 *
 * @param mappers run in list order; `SETTINGS` after `WORKSPACES` and `READING_PLANS` is enforced (IllegalArgumentException).
 * @param failAt test hook: the run throws a [LegacyMigrationException] right after that [Step].
 * @param userPlanDir where the user's own reading plan files must be; files of the old app that are not there yet are copied
 *   (by default the old app's own `Documents/jsword/readingplan`, so normally nothing needs copying).
 */
class LegacyMigrator(
    private val platform: LegacyPlatform,
    private val databases: LegacyDatabaseFactory,
    private val mappers: List<CategoryMapper> = defaultMappers(platform),
    private val failAt: Step? = null,
    private val userPlanDir: Path = platform.documents / "jsword" / "readingplan",
) {
    init {
        val order = mappers.map { it.category }
        val settings = order.indexOf(LegacyCategory.SETTINGS)
        if (settings >= 0) {
            for (before in listOf(LegacyCategory.WORKSPACES, LegacyCategory.READING_PLANS)) {
                val i = order.indexOf(before)
                require(i < 0 || i < settings) { "the $before mapper must run before the SETTINGS mapper" }
            }
        }
    }

    private val fs: FileSystem = platform.fileSystem
    private val dir: Path = platform.applicationSupport / "LegacyMigration"
    private val workDir = dir / "work"
    private val stagingDir = dir / "staging"
    private val replacedDir = dir / "replaced"
    private val doneFile = dir / "done.json"
    private val reportFile = dir / "report.json"
    private val states = MigrationStateStore(fs, dir)
    private val adopter = Adopter(fs, platform.databasesDir, stagingDir, replacedDir, states)

    private val mutex = Mutex()

    /**
     * Runs one migration attempt, or reports why none was needed or allowed. See the class description. Concurrent calls on
     * the same migrator are serialised (a second call waits, then sees the first one's result on disk); two migrators over
     * the same directories must not run at the same time, which the caller (one startup path) guarantees.
     */
    suspend fun run(): LegacyMigrationOutcome = mutex.withLock { runLocked() }

    private suspend fun runLocked(): LegacyMigrationOutcome {
        val report = MigrationReport()
        var state: MigrationState?
        try {
            fs.createDirectories(dir)
            if (fs.exists(doneFile)) {
                // Idempotent leftovers of the commit: the search index (a crash or error between done.json and its deletion).
                bestEffort { deleteSearchIndex() }
                bestEffortCleanUp()
                return LegacyMigrationOutcome.AlreadyDone
            }
            state = states.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The directory cannot even be inspected: a persistent I/O problem. A pending rollback is unknown, so do not claim one was done.
            return LegacyMigrationOutcome.Failed(Kind.CORRUPT_STORE, 1, report, rolledBack = false)
        }

        if (adopter.needsRollback(state)) {
            // A previous run died between "plan written" and "done.json": undo whatever it moved.
            val pending = state!!
            try {
                adopter.rollback(pending.plan)
                state = pending.copy(phase = MigrationPhase.IN_PROGRESS, plan = emptyList(), moved = emptyList())
                states.write(state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A rollback that keeps failing must count as an attempt, or every launch would throw here forever. The
                // state keeps ADOPTING and its plan, so the next launch repeats the rollback.
                val counted = pending.copy(attempt = pending.attempt + 1, lastKind = Kind.ADOPTION_FAILED)
                runCatching { states.write(counted) }
                return LegacyMigrationOutcome.Failed(Kind.ADOPTION_FAILED, counted.attempt, report, rolledBack = false)
            }
        }
        if (state != null && state.attempt >= MAX_ATTEMPTS) return LegacyMigrationOutcome.GaveUp(state.attempt, state.lastKind)

        val attempt = (state?.attempt ?: 0) + 1
        val sources = try {
            discoverSources()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return LegacyMigrationOutcome.Failed(Kind.CORRUPT_STORE, attempt, report)
        }
        if (sources.isEmpty()) {
            try {
                writeDone(sources, nothingToMigrate = true, attempt = 0, adopted = emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return LegacyMigrationOutcome.Failed(Kind.CORRUPT_STORE, attempt, report)
            }
            bestEffortCleanUp()
            return LegacyMigrationOutcome.NothingToMigrate
        }

        var current = MigrationState(MigrationPhase.IN_PROGRESS, attempt, Clock.System.now().toEpochMilliseconds(), state?.lastKind)
        var stage = Stage.COPY
        var committed = false
        var finished: LegacyExtras? = null
        try {
            states.write(current)
            for (leftover in listOf(workDir, stagingDir, replacedDir)) fs.deleteRecursively(leftover, mustExist = false)

            copyStores()
            hook(Step.COPY)

            var staged: List<String> = emptyList()
            val extras = openSnapshot().use { snapshot ->
                stage = Stage.READ
                hook(Step.READ)

                stage = Stage.MAP
                val extras = StagingDatabases(stagingDir, databases).use { staging ->
                    for (mapper in mappers) mapper.map(snapshot, staging, report)
                    buildExtras(snapshot, platform, report)
                }
                removeStrayStagingFiles()
                hook(Step.MAP)

                stage = Stage.VALIDATE
                staged = StagingValidator(databases, fs).validate(stagingDir, report) { entity -> snapshot.cloud.count(entity) }
                hook(Step.VALIDATE)
                extras
            }

            stage = Stage.ADOPT
            current = current.copy(phase = MigrationPhase.STAGED)
            states.write(current)
            val plan = adopter.plan(staged)
            current = current.copy(phase = MigrationPhase.ADOPTING, plan = plan, moved = emptyList())
            states.write(current) // the intent is on disk before the first move
            current = adopter.move(current) { hook(Step.ADOPT_FIRST_MOVE) }
            hook(Step.ADOPT_DONE)

            finished = extras
            for ((id, key) in extras.aiKeys) platform.secrets.put(apiKeyName(id), key)
            copyUserPlanFiles(extras)
            bestEffort { writeReport(report, "migrated", attempt, null) }
            writeDone(discoverSources(), nothingToMigrate = false, attempt = attempt, adopted = staged)
            committed = true
            // COMMITTED: from here on nothing may undo the adoption. Everything below is best-effort and idempotent.
            bestEffort { deleteSearchIndex() }
            bestEffortCleanUp()
            return LegacyMigrationOutcome.Migrated(report, extras)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // done.json may exist although writeDone threw (the rename can succeed before the call fails): that is committed.
            if (committed || (finished != null && fs.exists(doneFile))) {
                bestEffort { deleteSearchIndex() }
                bestEffortCleanUp()
                return LegacyMigrationOutcome.Migrated(report, finished!!)
            }
            val kind = (e as? LegacyMigrationException)?.kind ?: stage.defaultKind
            // Undo a started adoption before anything else; the state keeps the attempt count and the failure kind.
            var rolledBack = true
            val failed = try {
                if (current.phase == MigrationPhase.ADOPTING) adopter.rollback(current.plan)
                // The rollback is complete: the copies and staged files are of no further use (the next attempt starts over).
                for (leftover in listOf(workDir, stagingDir, replacedDir)) fs.deleteRecursively(leftover, mustExist = false)
                current.copy(phase = MigrationPhase.IN_PROGRESS, plan = emptyList(), moved = emptyList(), lastKind = kind)
            } catch (rollbackFailure: Exception) {
                // Keep ADOPTING with its plan: the next start repeats the rollback.
                rolledBack = false
                current.copy(lastKind = kind)
            }
            runCatching { states.write(failed) }
            runCatching { writeReport(report, "failed", attempt, "$kind: ${e::class.simpleName}: ${e.message?.take(500)}") }
            return LegacyMigrationOutcome.Failed(kind, attempt, report, rolledBack)
        }
    }

    private inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Diagnostic or cleanup work after the point of no return; a leftover is removed by a later AlreadyDone run.
        }
    }

    private fun bestEffortCleanUp() = bestEffort { cleanUp() }

    /** Opens both copies; the first reader is closed if the second one cannot be opened. */
    private fun openSnapshot(): LegacySnapshot {
        val cloud = LegacyStoreReader((workDir / "AndBible.store").toString())
        val local = try {
            LegacyStoreReader((workDir / "LocalStore.store").toString())
        } catch (e: Throwable) {
            runCatching { cloud.close() }
            throw e
        }
        return LegacySnapshot(cloud, local)
    }

    private fun hook(step: Step) {
        if (failAt == step) throw LegacyMigrationException(step.injectedKind, "injected failure after $step")
    }

    private enum class Stage(val defaultKind: Kind) {
        COPY(Kind.CORRUPT_STORE), READ(Kind.CORRUPT_STORE), MAP(Kind.CORRUPT_STORE), VALIDATE(Kind.VALIDATION_FAILED), ADOPT(Kind.ADOPTION_FAILED),
    }

    private val Step.injectedKind: Kind
        get() = when (this) {
            Step.COPY, Step.READ, Step.MAP -> Kind.CORRUPT_STORE
            Step.VALIDATE -> Kind.VALIDATION_FAILED
            Step.ADOPT_FIRST_MOVE, Step.ADOPT_DONE -> Kind.ADOPTION_FAILED
        }

    // ---- sources ------------------------------------------------------------------------------------------------

    private class Sources(val cloud: Path?, val local: Path?)

    /**
     * The store files of the old app: `AndBible.store` (else, as `DataMigration.migrateIfNeeded` repairs it, `CloudStore.store`,
     * else `AndBible.store.backup`) and `LocalStore.store`.
     */
    private fun stores(): Sources {
        val support = platform.applicationSupport
        val cloud = listOf("AndBible.store", "CloudStore.store", "AndBible.store.backup").map { support / it }.firstOrNull { fs.exists(it) }
        val local = (support / "LocalStore.store").takeIf { fs.exists(it) }
        return Sources(cloud, local)
    }

    /** Every file of the old app that the migration reads, for the completion marker. */
    private fun discoverSources(): List<Path> {
        val stores = stores()
        val files = mutableListOf<Path>()
        for (store in listOfNotNull(stores.cloud, stores.local)) {
            files += store
            for (suffix in listOf("-wal", "-shm")) (store.parent!! / (store.name + suffix)).takeIf { fs.exists(it) }?.let { files += it }
        }
        if (files.isEmpty()) return emptyList()
        platform.applicationSupport.let { support ->
            (support / "AndBible" / "AndroidDatabaseBackup" / "PreservedDatabases" / "ai_settings.sqlite3").takeIf { fs.exists(it) }?.let { files += it }
        }
        (platform.documents / "sword_install" / "CustomRepositories.json").takeIf { fs.exists(it) }?.let { files += it }
        return files
    }

    /** Copies both stores with their sidecars into `work/` under the canonical names (the file-rename repair applied to the copy). */
    private fun copyStores() {
        fs.createDirectories(workDir)
        val stores = stores()
        copyWithSidecars(stores.cloud ?: throw LegacyMigrationException(Kind.MISSING_STORE, "AndBible.store not found"), workDir / "AndBible.store")
        copyWithSidecars(stores.local ?: throw LegacyMigrationException(Kind.MISSING_STORE, "LocalStore.store not found"), workDir / "LocalStore.store")
    }

    private fun copyWithSidecars(source: Path, target: Path) {
        fs.copy(source, target)
        for (suffix in listOf("-wal", "-shm")) {
            val side = source.parent!! / (source.name + suffix)
            if (fs.exists(side)) fs.copy(side, target.parent!! / (target.name + suffix))
        }
    }

    // ---- adoption extras ------------------------------------------------------------------------------------------

    /** Copies the user's own plan files to [userPlanDir] when they are not there yet (never overwrites). */
    private fun copyUserPlanFiles(extras: LegacyExtras) {
        for (file in extras.userPlanFiles) {
            val target = userPlanDir / file.name
            if (target == file || fs.exists(target)) continue
            fs.createDirectories(userPlanDir)
            val temp = userPlanDir / (file.name + ".tmp")
            fs.copy(file, temp)
            fs.atomicMove(temp, target)
        }
    }

    private fun deleteSearchIndex() {
        val index = platform.documents / "search_indexes.sqlite"
        for (suffix in listOf("", "-wal", "-shm", "-journal")) fs.delete(platform.documents / ("search_indexes.sqlite$suffix"), mustExist = false)
        check(!fs.exists(index)) { "search index not deleted" }
    }

    /** Mappers may leave scratch files in `staging/`; only the databases and their names are adopted, so drop the rest. */
    private fun removeStrayStagingFiles() {
        val known = net.bible.service.legacymigration.adopt.STAGED_DATABASES.map { it.second }.toSet()
        for (file in fs.listOrNull(stagingDir).orEmpty()) if (file.name !in known) fs.deleteRecursively(file, mustExist = false)
    }

    // ---- files of the migration directory --------------------------------------------------------------------------

    private fun writeDone(sources: List<Path>, nothingToMigrate: Boolean, attempt: Int, adopted: List<String>) {
        val json = buildJsonObject {
            put("completedAt", Clock.System.now().toEpochMilliseconds())
            put("nothingToMigrate", nothingToMigrate)
            put("attempt", attempt)
            put("sources", JsonArray(sources.map { buildJsonObject { put("path", it.toString()); put("size", fs.metadata(it).size ?: 0L) } }))
            put("adopted", JsonArray(adopted.map { JsonPrimitive(it) }))
        }.toString()
        atomicWrite(doneFile, json)
    }

    private fun writeReport(report: MigrationReport, outcome: String, attempt: Int, failure: String?) {
        val base: JsonObject = Json.parseToJsonElement(report.toJson()).jsonObject
        val json = JsonObject(base + mapOf("outcome" to JsonPrimitive(outcome), "attempt" to JsonPrimitive(attempt)) + (failure?.let { mapOf("failure" to JsonPrimitive(it)) } ?: emptyMap()))
        atomicWrite(reportFile, json.toString())
    }

    private fun atomicWrite(target: Path, text: String) {
        atomicWriteFile(fs, target, text)
    }

    /** Deletes `work/`, `staging/`, `replaced/` and `state.json`. Only called when `done.json` exists or the run is complete. */
    private fun cleanUp() {
        for (leftover in listOf(workDir, stagingDir, replacedDir)) fs.deleteRecursively(leftover, mustExist = false)
        for (name in listOf("done.json.tmp", "report.json.tmp")) fs.delete(dir / name, mustExist = false)
        states.delete()
    }

    private companion object {
        /** An attempt count at which no further automatic attempt is made. */
        const val MAX_ATTEMPTS = 2
    }
}
