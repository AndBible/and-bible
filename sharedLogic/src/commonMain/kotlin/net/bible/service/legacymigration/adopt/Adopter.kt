package net.bible.service.legacymigration.adopt

import net.bible.service.db.openSqlite
import net.bible.service.legacymigration.LegacyMigrationException
import net.bible.service.legacymigration.LegacyMigrationException.Kind
import net.bible.service.legacymigration.state.MigrationPhase
import net.bible.service.legacymigration.state.MigrationState
import net.bible.service.legacymigration.state.MigrationStateStore
import net.bible.service.legacymigration.state.PlannedMove
import okio.FileSystem
import okio.Path

/**
 * Moves the validated staged databases into the app's databases directory and can undo that after a crash.
 *
 * ## Crash-safety protocol
 *
 * Everything the migration writes before adoption lives under the migration directory (`work/`, `staging/`), so a kill
 * before adoption changes nothing the app can see. Adoption is the only phase that touches the databases directory, and
 * it is made recoverable from a kill at ANY instant:
 *
 * 1. **Plan, then check.** [plan] looks at every target `databasesDir/<name>`. A target that does not exist is fine. A
 *    target that exists is replaced only when it is provably empty ([isEmptyDatabase]: a zero-byte file, or a readable
 *    SQLite file whose tables hold no rows, with no pending `-wal`/`-journal` content); anything else, including a file that
 *    cannot be read, makes the whole adoption fail with `ADOPTION_FAILED` before a single file is touched. The result is
 *    the list of [PlannedMove]s (name plus whether an empty file is replaced).
 * 2. **Intent first.** The caller writes `state.json` with phase `ADOPTING` and the plan BEFORE the first move. From then
 *    on a rollback needs only the plan; it never depends on a per-move record that a kill could have prevented.
 * 3. **Move.** For each planned name: when a file is replaced, it is first moved to `replaced/<name>` (so its bytes are
 *    kept), then `staging/<name>` is `atomicMove`d to the target. After each move the name is added to `state.moved` (a
 *    progress record for diagnosis; not used for the rollback).
 * 4. **Commit.** After the last move and the post-steps (secrets, plan files, search index) the caller writes `done.json`
 *    with a temporary file and `atomicMove`. That single rename is the commit point. A kill before it is rolled back, a kill
 *    after it is final (the next start sees `done.json`, only deletes leftovers and never rolls back).
 * 5. **Rollback** ([rollback], run at the start of every launch that finds phase `ADOPTING`, and in-process when adoption
 *    fails): for each planned name, in this order, idempotently:
 *    - a name that replaced nothing: whatever is at the target (plus `-journal`/`-wal`/`-shm`/`.lck` leftovers) is ours,
 *      because the plan proved the target absent; delete it;
 *    - a name that replaced an empty file: if `replaced/<name>` still exists the target is ours or already absent; delete
 *      the target and `atomicMove` the backup back. If the backup does not exist, the target is the user's original, which
 *      was not touched yet (or was restored already), and is left alone.
 *    A rollback can therefore be interrupted and repeated, and it can never delete a file that existed before adoption
 *    unless a backup of it exists. When it is complete the caller rewrites the state without a plan, so a later launch
 *    (for example after the user chose "start without old data" and the app created new databases) never rolls back again.
 *
 * `staging/` and `work/` are only deleted after a rollback has finished, by the next attempt's start.
 */
internal class Adopter(
    private val fs: FileSystem,
    private val databasesDir: Path,
    private val stagingDir: Path,
    private val replacedDir: Path,
    private val states: MigrationStateStore,
) {
    /** Decides what to move and checks the targets (protocol step 1). Throws `ADOPTION_FAILED` when a target must not be replaced. */
    fun plan(names: List<String>): List<PlannedMove> = names.map { name ->
        val target = databasesDir / name
        if (!fs.exists(target) && !hasSideFiles(target)) return@map PlannedMove(name, replaces = false)
        if (!fs.exists(target)) {
            // Only side files of a database that is not there: not ours to guess about.
            throw LegacyMigrationException(Kind.ADOPTION_FAILED, "$name has leftover side files in the databases directory and no main file")
        }
        if (!isEmptyDatabase(target)) throw LegacyMigrationException(Kind.ADOPTION_FAILED, "$name already exists in the databases directory and holds data")
        PlannedMove(name, replaces = true)
    }

    /**
     * Protocol step 3 for [state] (whose plan has already been written). [afterFirstMove] runs once, after the first
     * move and its state record (the test hook of `Step.ADOPT_FIRST_MOVE`; it also runs when the plan is empty).
     */
    fun move(state: MigrationState, afterFirstMove: () -> Unit): MigrationState {
        fs.createDirectories(databasesDir)
        var current = state
        for ((index, planned) in state.plan.withIndex()) {
            val target = databasesDir / planned.name
            if (planned.replaces) {
                fs.createDirectories(replacedDir)
                fs.atomicMove(target, replacedDir / planned.name)
            }
            deleteSideFiles(target) // stale -shm/.lck of a database that is not there must not meet the new file
            fs.atomicMove(stagingDir / planned.name, target)
            current = current.copy(moved = current.moved + planned.name)
            states.write(current)
            if (index == 0) afterFirstMove()
        }
        if (state.plan.isEmpty()) afterFirstMove()
        return current
    }

    /** Protocol step 5: undoes a (possibly partial) adoption of [plan]. Idempotent. */
    fun rollback(plan: List<PlannedMove>) {
        for (planned in plan) {
            val target = databasesDir / planned.name
            val backup = replacedDir / planned.name
            if (!planned.replaces) {
                deleteWithSideFiles(target)
            } else if (fs.exists(backup)) {
                deleteWithSideFiles(target)
                fs.atomicMove(backup, target)
            }
        }
    }

    /** [MigrationPhase] guard used by the migrator: the plan is only meaningful while adopting. */
    fun needsRollback(state: MigrationState?): Boolean = state != null && state.phase == MigrationPhase.ADOPTING && state.plan.isNotEmpty()

    /**
     * True when [path] cannot hold user data: a zero-byte file, or a SQLite file in which every table (SQLite's own,
     * Room's `room_master_table` and `android_metadata` excluded) has no rows. A non-empty `-wal` or `-journal` next to it
     * means pending content, so the answer is false; so it is for a file that cannot be opened or read.
     */
    private fun isEmptyDatabase(path: Path): Boolean {
        for (suffix in PENDING_SUFFIXES) {
            val sideFile = side(path, suffix)
            if (fs.exists(sideFile) && (fs.metadataOrNull(sideFile)?.size ?: 0L) > 0L) return false
        }
        if ((fs.metadataOrNull(path)?.size ?: return false) == 0L) return true
        return try {
            val c = openSqlite(path.toString(), readOnly = true)
            try {
                val tables = c.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").let { s ->
                    try { buildList { while (s.step()) add(s.getText(0)) } } finally { s.close() }
                }.filter { !it.startsWith("sqlite_") && it != "room_master_table" && it != "android_metadata" }
                tables.all { table ->
                    val s = c.prepare("SELECT EXISTS(SELECT 1 FROM `$table`)")
                    try { s.step(); s.getLong(0) == 0L } finally { s.close() }
                }
            } finally {
                c.close()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasSideFiles(target: Path) = PENDING_SUFFIXES.any { fs.exists(side(target, it)) }

    private fun deleteSideFiles(target: Path) {
        for (suffix in SIDE_SUFFIXES) fs.delete(side(target, suffix), mustExist = false)
    }

    private fun deleteWithSideFiles(target: Path) {
        fs.delete(target, mustExist = false)
        deleteSideFiles(target)
    }

    private companion object {
        val SIDE_SUFFIXES = listOf("-journal", "-wal", "-shm", ".lck")
        val PENDING_SUFFIXES = listOf("-journal", "-wal")

        /** `<path><suffix>` as a sibling path. */
        fun side(path: Path, suffix: String): Path = (path.parent ?: error("path without a parent: $path")) / (path.name + suffix)
    }
}
