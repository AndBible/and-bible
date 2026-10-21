@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.service.legacymigration.control.LegacyMigrationControl
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.use
import org.crosswire.common.compress.ZipWriter
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970

/**
 * Builds the "Save old data" zip: copies of the old app's stores, its preferences plist and the migration report, so the
 * user (or support) keeps the data even when the migration cannot proceed. It only READS the sources (I3 section 6.5) and
 * never includes Keychain data; nothing outside the fixed list below is archived.
 */
internal object IosLegacyOldDataArchive {
    private val storeNames = listOf("AndBible.store", "CloudStore.store", "AndBible.store.backup", "LocalStore.store")
    private val sideSuffixes = listOf("-wal", "-shm")

    /**
     * Writes `AndBible-old-data-<nowMs>.zip` into [tempDir] and returns its path. The stores are read from the
     * migration's application-support directory (the parent of `control.dir`); a store mid-write has its `-wal`/`-shm`
     * side files archived too. The plist is `<preferencesDir>/<bundleId>.plist` when it exists. The file work runs off
     * Main. A failed write removes the partial zip and rethrows.
     */
    suspend fun write(
        control: LegacyMigrationControl,
        fs: FileSystem = FileSystem.SYSTEM,
        preferencesDir: Path = (NSHomeDirectory().trimEnd('/') + "/Library/Preferences").toPath(),
        bundleId: String = NSBundle.mainBundle.bundleIdentifier ?: "",
        tempDir: Path = NSTemporaryDirectory().trimEnd('/').toPath(),
        nowMs: Long = (NSDate().timeIntervalSince1970 * 1000).toLong(),
    ): String = withContext(Dispatchers.Default) {
        val support = control.dir.parent ?: error("The migration directory has no parent")
        val entries = linkedMapOf<String, Path>()
        for (name in storeNames) for (suffix in listOf("") + sideSuffixes) {
            entries.putIfFile(fs, name + suffix, support / (name + suffix))
        }
        if (bundleId.isNotEmpty()) entries.putIfFile(fs, "Library/Preferences/$bundleId.plist", preferencesDir / "$bundleId.plist")
        entries.putIfFile(fs, "LegacyMigration/report.json", control.dir / "report.json")

        fs.createDirectories(tempDir)
        val zipPath = tempDir / "AndBible-old-data-$nowMs.zip"
        try {
            ZipWriter(fs.sink(zipPath)).use { zip ->
                for ((name, path) in entries) zip.putEntry(name, fs.source(path))
            }
        } catch (t: Throwable) {
            fs.delete(zipPath, mustExist = false)
            throw t
        }
        zipPath.toString()
    }

    /** The bytes of the last run's `report.json`, or null when there is none or it cannot be read. */
    fun reportBytes(control: LegacyMigrationControl, fs: FileSystem = FileSystem.SYSTEM): ByteArray? =
        runCatching { fs.read(control.dir / "report.json") { readByteArray() } }.getOrNull()

    private fun MutableMap<String, Path>.putIfFile(fs: FileSystem, name: String, path: Path) {
        if (fs.metadataOrNull(path)?.isRegularFile == true) put(name, path)
    }
}
