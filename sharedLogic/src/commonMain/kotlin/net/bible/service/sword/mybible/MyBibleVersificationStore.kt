package net.bible.service.sword.mybible

import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.log.Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.ByteString.Companion.encodeUtf8
import org.crosswire.common.util.KFiles
import org.crosswire.jsword.versification.system.Versifications
import org.koin.mp.KoinPlatform

internal const val MYBIBLE_VERSIFICATION_SUFFIX = ".andbible-versification"

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

    private fun marker(): Path {
        val dirs = KoinPlatform.getKoin().get<AppDirectories>()
        // A temporary Android fallback root must not complete migration for the later external root.
        val root = dirs.modulesDir.normalized().toString().encodeUtf8().sha256().hex()
        return dirs.internalFilesDir / "mybible-versification-v2" / root
    }

    fun resolve(file: Path, allowDetection: Boolean = true, detect: () -> String): String = synchronized(lock) {
        try {
            val saved = record(file)
            readSaved(file)?.let { return@synchronized it }
            if (!allowDetection) return@synchronized "KJVA"
            detect().also { write(saved, it) }
        } catch (e: IOException) {
            // A decision that was not saved must not reinterpret legacy coordinates or prevent startup.
            Log.e(TAG, "Could not persist MyBible numbering for $file; using KJVA", e)
            // Do not let a later successful scan reinterpret an installation first opened with KJVA.
            try { fs.delete(marker(), mustExist = false) } catch (failure: IOException) {
                Log.e(TAG, "Could not reset MyBible migration marker", failure)
            }
            "KJVA"
        }
    }

    /** Pin all existing files before metadata is read. A changed storage root needs its own legacy scan. */
    fun migrateLegacyModules(files: List<Path>): Boolean = synchronized(lock) {
        try {
            val marker = marker()
            if (fs.exists(marker)) return@synchronized true
            files.forEach { file ->
                if (!fs.exists(record(file))) write(record(file), "KJVA")
            }
            write(marker, "complete")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Could not migrate MyBible numbering; retrying on the next scan", e)
            false
        }
    }

    /** Read without creating a decision, so restore can snapshot an installation before replacing it. */
    fun readSaved(file: Path): String? = synchronized(lock) {
        val saved = record(file)
        if (!fs.exists(saved)) return@synchronized null
        val value = fs.read(saved) { readUtf8() }.trim()
        if (Versifications.instance().getVersification(value) != null) value else {
            Log.w(TAG, "Unknown saved MyBible numbering '$value' for $file; using KJVA")
            "KJVA"
        }
    }

    /** Archive metadata is authoritative; old archives explicitly restore KJVA before registration. */
    fun restore(file: Path, versification: String) = synchronized(lock) {
        if (Versifications.instance().getVersification(versification) == null) {
            throw IOException("Unknown MyBible versification: $versification")
        }
        write(record(file), versification)
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
