package net.bible.service.installzip

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.database.IdType
import net.bible.service.common.safeZipEntryPath
import net.bible.service.sword.mybible.MyBibleVersificationStore
import net.bible.sharedcore.log.Log
import okio.FileSystem
import okio.IOException
import okio.Path

/** Recover interrupted archive installs before the platform registers any JSword search paths. */
fun recoverInterruptedSwordInstalls(modulesDir: Path, internalFilesDir: Path? = null) =
    SwordInstallTransaction.recoverInterrupted(modulesDir, internalFilesDir)

/** Process-kill recovery for archive publication; this does not guarantee power-loss durability. */
internal class SwordInstallTransaction(
    private val root: Path,
    private val internalFilesDir: Path? = null,
    private val files: FileSystem = FileSystem.SYSTEM,
) {
    val staging: Path = root / "$PREFIX${IdType()}"

    init { files.createDirectories(staging / "files") }

    fun stage(target: Path): Path = (staging / "files" / relative(target)).also {
        files.createDirectories(it.parent!!)
    }

    /** Move replacement originals aside before the publishing journal permits deletion of live targets. */
    fun beginPublish(targets: Collection<Path>, replacements: Map<Path, String?> = emptyMap()) {
        val names = targets.distinct().map { relative(it) }
        val originals = replacements.entries.associate { relative(it.key) to it.value }
        require(originals.keys.all { it in names }) { "Replacement is not a publication target" }
        validatePaths(root, staging, names + originals.keys, files)
        val existing = originals.keys.filter { files.exists(root / it) }
        if (originals.isNotEmpty() && internalFilesDir == null) throw IOException("Missing internal files directory")
        if (originals.isNotEmpty()) {
            writeAtomic(files, staging, EXISTING, Json.encodeToString(existing))
            writeAtomic(files, staging, ORIGINALS, Json.encodeToString(originals))
            for (name in existing) {
                val aside = staging / "originals" / name
                files.createDirectories(aside.parent!!)
                files.atomicMove(root / name, aside)
            }
        }
        writeAtomic(files, staging, JOURNAL, Json.encodeToString(names))
    }

    /** Mark success before cleanup so interrupted cleanup cannot undo a completed installation. */
    fun complete() {
        finish(root, staging, files)
    }

    /** A handled failure before the first publication can keep the untouched SWORD generation. */
    fun rollback(removePublished: Boolean = true) = recover(root, staging, internalFilesDir, files, removePublished)

    private fun relative(target: Path): String {
        val relative = target.relativeTo(root).segments.joinToString("/")
        require(safeZipEntryPath(relative) == relative) { "Unsafe module transaction path" }
        return relative
    }

    companion object {
        private const val PREFIX = ".sword-install-"
        private const val JOURNAL = "publishing"
        private const val RESTORING = "restoring"
        private const val COMMITTED_PREFIX = ".sword-committed-"
        private const val ORIGINALS = "originals.json"
        private const val EXISTING = "existing.json"

        private fun finish(root: Path, staging: Path, files: FileSystem) {
            val committed = root / "$COMMITTED_PREFIX${staging.name.removePrefix(PREFIX)}"
            files.atomicMove(staging, committed)
            try {
                files.deleteRecursively(committed)
            } catch (e: IOException) {
                Log.w("SwordInstallTransaction", "Could not clean committed transaction $committed", e)
            }
        }

        fun recoverInterrupted(root: Path, internalFilesDir: Path? = null, files: FileSystem = FileSystem.SYSTEM) {
            for (dir in files.listOrNull(root).orEmpty()) {
                if (files.metadataOrNull(dir)?.isDirectory != true || files.metadataOrNull(dir)?.symlinkTarget != null) continue
                if (dir.name.startsWith(COMMITTED_PREFIX)) files.deleteRecursively(dir)
                else if (dir.name.startsWith(PREFIX)) recover(root, dir, internalFilesDir, files)
            }
        }

        private fun writeAtomic(files: FileSystem, staging: Path, name: String, content: String) {
            files.write(staging / "$name.tmp") { writeUtf8(content) }
            files.atomicMove(staging / "$name.tmp", staging / name)
        }

        private fun validatePaths(root: Path, staging: Path, names: Collection<String>, files: FileSystem) {
            for (name in names) {
                if (safeZipEntryPath(name) != name) throw IOException("Unsafe module transaction path")
                for (base in listOf(root, staging / "originals")) {
                    var path: Path? = base / name
                    // The trusted module root may itself be an OS/platform alias (e.g. /var on iOS).
                    while (path != null && path != base && path != root) {
                        if (files.metadataOrNull(path)?.symlinkTarget != null) throw IOException("Symlink in module transaction path")
                        path = path.parent
                    }
                }
            }
        }

        private fun recover(root: Path, staging: Path, internalFilesDir: Path?, files: FileSystem, removePublished: Boolean = true) {
            val journal = staging / JOURNAL
            val restoring = staging / RESTORING
            val manifest = staging / ORIGINALS
            val names = try {
                val path = if (files.exists(journal)) journal else restoring
                if (files.exists(path)) Json.decodeFromString<List<String>>(files.read(path) { readUtf8() }) else emptyList()
            } catch (e: Exception) { throw IOException("Invalid module transaction journal", e) }
            val originals = try {
                if (files.exists(manifest)) Json.decodeFromString<Map<String, String?>>(files.read(manifest) { readUtf8() }) else emptyMap()
            } catch (e: Exception) { throw IOException("Invalid module transaction originals", e) }
            val existing = try {
                if (files.exists(staging / EXISTING)) Json.decodeFromString<List<String>>(files.read(staging / EXISTING) { readUtf8() }).toSet()
                else originals.keys
            } catch (e: Exception) { throw IOException("Invalid module transaction existing paths", e) }
            validatePaths(root, staging, names + originals.keys + existing, files)
            if (!originals.keys.containsAll(existing)) throw IOException("Invalid module transaction existing paths")
            if (originals.isNotEmpty() && internalFilesDir == null) throw IOException("Missing internal files directory")
            val published = files.exists(journal) || files.exists(restoring)
            if (files.exists(journal)) {
                val targets = names.map { root / it }
                val (confs, data) = targets.partition { it.parent == root / "mods.d" }
                if (removePublished) for (path in confs + data) files.delete(path, mustExist = false)
                files.atomicMove(journal, restoring)
            }
            for ((name, decision) in originals) {
                val aside = staging / "originals" / name
                val hasAside = files.exists(aside)
                if (hasAside || (published && name !in existing)) {
                    val target = root / name
                    MyBibleVersificationStore.restorePrevious(target, decision, root, internalFilesDir!!)
                    if (hasAside) {
                        files.createDirectories(target.parent!!)
                        files.atomicMove(aside, target)
                    }
                }
            }
            finish(root, staging, files)
        }
    }
}
