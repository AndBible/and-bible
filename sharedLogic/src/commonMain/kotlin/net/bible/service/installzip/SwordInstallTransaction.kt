package net.bible.service.installzip

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.database.IdType
import net.bible.service.common.safeZipEntryPath
import okio.FileSystem
import okio.IOException
import okio.Path

/** Recover interrupted archive installs before the platform registers any JSword search paths. */
fun recoverInterruptedSwordInstalls(modulesDir: Path) = SwordInstallTransaction.recoverInterrupted(modulesDir)

/**
 * Process-kill recovery for archive publication. Extraction stays outside the scanned module tree.
 * Before the first live mutation, a journal lists the files to remove if publication is interrupted.
 * Recovery deliberately chooses absence rather than trying to reconstruct a mixed old/new module.
 * This is not a power-loss durability guarantee (no fsync).
 */
internal class SwordInstallTransaction(private val root: Path) {
    private val files = FileSystem.SYSTEM
    val staging: Path = root / "$PREFIX${IdType()}"

    init { files.createDirectories(staging / "files") }

    fun stage(target: Path): Path = (staging / "files" / relative(target)).also {
        files.createDirectories(it.parent!!)
    }

    /** Persist ownership before moving any old conf or replacing any live data. */
    fun beginPublish(targets: Collection<Path>) {
        val journal = staging / JOURNAL
        files.write(staging / "$JOURNAL.tmp") {
            writeUtf8(Json.encodeToString(targets.distinct().map { relative(it) }))
        }
        files.atomicMove(staging / "$JOURNAL.tmp", journal)
    }

    /** Once all confs landed, a kill during staging cleanup must preserve the complete install. */
    fun complete() {
        files.delete(staging / JOURNAL, mustExist = false)
        files.deleteRecursively(staging)
    }

    fun rollback() = recover(root, staging)

    private fun relative(target: Path): String {
        val relative = target.relativeTo(root).segments.joinToString("/")
        require(safeZipEntryPath(relative) == relative) { "Unsafe module transaction path" }
        return relative
    }

    companion object {
        private const val PREFIX = ".sword-install-"
        private const val JOURNAL = "publishing"

        /** Call only at cold startup, before JSword can discover books or another installer can run. */
        fun recoverInterrupted(root: Path) {
            val files = FileSystem.SYSTEM
            for (dir in files.listOrNull(root).orEmpty()) {
                if (dir.name.startsWith(PREFIX) && files.metadataOrNull(dir)?.isDirectory == true &&
                    files.metadataOrNull(dir)?.symlinkTarget == null) recover(root, dir)
            }
        }

        private fun recover(root: Path, staging: Path) {
            val files = FileSystem.SYSTEM
            val journal = staging / JOURNAL
            if (files.exists(journal)) {
                val names = try { Json.decodeFromString<List<String>>(files.read(journal) { readUtf8() }) }
                catch (e: Exception) { throw IOException("Invalid module transaction journal", e) }
                val targets = names.map { name ->
                    if (safeZipEntryPath(name) != name) throw IOException("Unsafe module transaction path")
                    val target = root / name
                    // Never follow a symlink outside the module store during recovery.
                    var parent = target.parent
                    while (parent != null && parent != root) {
                        if (files.metadataOrNull(parent)?.symlinkTarget != null) throw IOException("Symlink in module transaction path")
                        parent = parent.parent
                    }
                    target
                }.toList()
                // Hide every affected conf first. A kill during recovery must not expose partial data.
                val (confs, data) = targets.partition { it.parent == root / "mods.d" }
                for (path in confs + data) files.delete(path, mustExist = false)
            }
            // Keep the journal until all live deletions succeed so recovery itself is restartable.
            files.deleteRecursively(staging)
        }
    }
}
