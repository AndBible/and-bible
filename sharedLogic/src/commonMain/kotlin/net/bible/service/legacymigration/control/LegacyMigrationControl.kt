package net.bible.service.legacymigration.control

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.service.legacymigration.state.MigrationPhase
import net.bible.service.legacymigration.state.MigrationStateStore
import net.bible.service.legacymigration.state.atomicWriteFile
import kotlin.time.Clock
import okio.FileSystem
import okio.Path

/** Why the user (or the app) chose not to migrate the legacy data. */
enum class DeclineReason { START_FRESH, RESTORED_BACKUP }

/**
 * The small marker files next to the migration state in `applicationSupport/LegacyMigration/`: `declined.json` (the user
 * chose to start without the old data or restored a backup, so the migration is settled without `done.json`) and
 * `import-request.json` (the user asked for a later import; the runner consumes it). The migrator itself never reads them;
 * the startup runner and the settings screen do. [sync] is `platform::syncFile` for durable writes on iOS (default: none).
 */
class LegacyMigrationControl(private val fs: FileSystem, applicationSupport: Path, private val sync: (Path) -> Unit = {}) {
    val dir: Path = applicationSupport / "LegacyMigration"
    private val support = applicationSupport
    private val doneFile = dir / "done.json"
    private val declinedFile = dir / "declined.json"
    private val importRequestFile = dir / "import-request.json"
    private val states = MigrationStateStore(fs, dir, sync)

    /** True when a store of the old app exists: a cloud store (`AndBible.store`, else `CloudStore.store`, else `AndBible.store.backup`) and `LocalStore.store`. */
    fun sourcesExist(): Boolean =
        listOf("AndBible.store", "CloudStore.store", "AndBible.store.backup").any { fs.exists(support / it) } && fs.exists(support / "LocalStore.store")

    /** The migration needs no further decision: it completed (or found nothing) or the user declined it. */
    fun isSettled(): Boolean = fs.exists(doneFile) || fs.exists(declinedFile)

    fun decline(reason: DeclineReason) {
        fs.createDirectories(dir)
        val name = when (reason) { DeclineReason.START_FRESH -> "startFresh"; DeclineReason.RESTORED_BACKUP -> "restoredBackup" }
        atomicWriteFile(fs, declinedFile, """{"reason": "$name", "at": ${Clock.System.now().toEpochMilliseconds()}}""", sync)
    }

    /** An import can be offered: old stores exist and no real migration has completed (a "nothing to migrate" marker does not count). */
    fun importAvailable(): Boolean = sourcesExist() && !(fs.exists(doneFile) && !nothingToMigrate())

    /** Clears a decline and the attempt counter and records the request for the runner. */
    fun requestImport() {
        fs.createDirectories(dir)
        fs.delete(declinedFile, mustExist = false)
        // A "nothing to migrate" marker would make the migrator answer AlreadyDone; a real migration's marker is never touched.
        if (fs.exists(doneFile) && nothingToMigrate()) fs.delete(doneFile)
        resetAttempts()
        atomicWriteFile(fs, importRequestFile, """{"requestedAt":${Clock.System.now().toEpochMilliseconds()}}""", sync)
    }

    fun importRequested(): Boolean = fs.exists(importRequestFile)

    /** Called by the runner after a settled outcome. */
    fun clearImportRequest() {
        fs.delete(importRequestFile, mustExist = false)
    }

    /**
     * Forgets the attempt counter so the next run starts at attempt 1. Refused (false, nothing changed) while `state.json`
     * is in phase `ADOPTING`: its plan is needed for the rollback.
     */
    fun resetAttempts(): Boolean {
        // A state file that cannot be read counts as not adopting (the store maps it to an exhausted budget).
        if (runCatching { states.read() }.getOrNull()?.phase == MigrationPhase.ADOPTING) return false
        states.delete()
        return true
    }

    /**
     * True when `done.json` says a post-commit leftover (the old search index or the EPUB adoption) is still unflagged, so a
     * migrator run on the `AlreadyDone` path has something to do. False for no marker, a `nothingToMigrate` marker, an
     * unreadable marker, or both flags set. Only reads one small file.
     */
    fun postCommitPending(): Boolean = try {
        val marker = Json.parseToJsonElement(fs.read(doneFile) { readUtf8() }).jsonObject
        marker["nothingToMigrate"]?.jsonPrimitive?.booleanOrNull != true &&
            (marker["searchIndexDeleted"]?.jsonPrimitive?.booleanOrNull != true || marker["filesAdopted"]?.jsonPrimitive?.booleanOrNull != true)
    } catch (e: Exception) {
        false
    }

    private fun nothingToMigrate(): Boolean = try {
        Json.parseToJsonElement(fs.read(doneFile) { readUtf8() }).jsonObject["nothingToMigrate"]?.jsonPrimitive?.booleanOrNull == true
    } catch (e: Exception) {
        false
    }
}
