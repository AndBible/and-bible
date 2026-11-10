package net.bible.service.sword.mybible

import net.bible.sharedcore.platform.AppDirectories
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.Path
import okio.ByteString.Companion.encodeUtf8
import org.crosswire.common.util.KFiles
import org.koin.mp.KoinPlatform

/**
 * Numbering belongs to an installation, not to today's detection heuristic. App-private records use the module's
 * path relative to modulesDir, not its size/content, so updates and sandbox relocation cannot reinterpret saved
 * coordinates. Files outside modulesDir use their absolute path. No writes touch the user's SQLite or directory.
 */
internal object MyBibleVersificationStore {
    private val fs = FileSystem.SYSTEM
    private val lock = SynchronizedObject()
    private fun record(file: Path): Path {
        val dirs = KoinPlatform.getKoin().get<AppDirectories>()
        val path = file.normalized()
        val root = dirs.modulesDir.normalized()
        val insideModules = path.root == root.root && path.segments.take(root.segments.size) == root.segments
        val identity = if (insideModules) "modules/${path.relativeTo(root)}" else "external/$path"
        return dirs.internalFilesDir / "mybible-versifications" / identity.encodeUtf8().sha256().hex()
    }

    fun resolve(file: Path, detect: () -> String): String = synchronized(lock) {
        val saved = record(file)
        if (fs.exists(saved)) return@synchronized fs.read(saved) { readUtf8() }.trim()
        detect().also { write(saved, it) }
    }

    /** Pin *all* existing files before any metadata/detection is read, including currently unreadable databases. */
    fun migrateLegacyModules(files: List<Path>) = synchronized(lock) {
        val marker = KoinPlatform.getKoin().get<AppDirectories>().internalFilesDir / "mybible-versification-v1"
        if (fs.exists(marker)) return@synchronized
        files.forEach { file ->
            // Preserve a decision made by an import before the first startup scan.
            if (!fs.exists(record(file))) write(record(file), "KJVA")
        }
        write(marker, "complete")
    }

    fun remove(file: Path) = synchronized(lock) {
        fs.delete(record(file), mustExist = false)
    }

    private fun write(path: Path, value: String) {
        fs.createDirectories(path.parent!!)
        val temporary = path.parent!! / "${path.name}.tmp"
        try {
            fs.write(temporary) { writeUtf8(value) }
            fs.atomicMove(temporary, path)
        } finally {
            KFiles.delete(temporary)
        }
    }
}
