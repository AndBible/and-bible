package net.bible.sharedui.ios.startup.legacy

import kotlinx.coroutines.runBlocking
import net.bible.service.legacymigration.control.LegacyMigrationControl
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import org.crosswire.common.compress.ZipReader
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosLegacyOldDataArchiveTest {
    private val fs = FileSystem.SYSTEM
    private val root: Path = (NSTemporaryDirectory().trimEnd('/') + "/andbible-old-data-" + NSUUID().UUIDString).toPath()
    private val support = root / "Application Support"
    private val prefs = root / "Library" / "Preferences"
    private val out = root / "tmp"
    private val bundleId = "org.example.legacy"

    @AfterTest fun cleanUp() { fs.deleteRecursively(root, mustExist = false) }

    private fun put(path: Path, text: String): ByteArray {
        path.parent?.let { fs.createDirectories(it) }
        val bytes = text.encodeToByteArray()
        fs.write(path) { write(bytes) }
        return bytes
    }

    /** Stores trio with side files, a store mid-write, a plist, a report, and files that must stay out of the zip. */
    private fun layout(): Map<String, ByteArray> {
        val expected = linkedMapOf<String, ByteArray>()
        expected["AndBible.store"] = put(support / "AndBible.store", "cloud")
        expected["AndBible.store-wal"] = put(support / "AndBible.store-wal", "cloud-wal")
        expected["AndBible.store-shm"] = put(support / "AndBible.store-shm", "cloud-shm")
        expected["LocalStore.store"] = put(support / "LocalStore.store", "local")
        expected["AndBible.store.backup"] = put(support / "AndBible.store.backup", "backup")
        expected["Library/Preferences/$bundleId.plist"] = put(prefs / "$bundleId.plist", "plist")
        expected["LegacyMigration/report.json"] = put(support / "LegacyMigration" / "report.json", "{\"ok\":false}")
        // Must not be archived.
        put(support / "LegacyMigration" / "state.json", "state")
        put(support / "keychain-secret.txt", "secret")
        put(prefs / "other.plist", "other")
        return expected
    }

    private fun readZip(path: String): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipReader(fs.source(path.toPath())).use { zip ->
            while (true) {
                val entry = zip.nextEntry() ?: break
                result[entry.name] = zip.entry.buffer().readByteArray()
            }
        }
        return result
    }

    private fun write() = runBlocking {
        IosLegacyOldDataArchive.write(
            LegacyMigrationControl(fs, support), fs = fs, preferencesDir = prefs, bundleId = bundleId, tempDir = out, nowMs = 42L,
        )
    }

    @Test fun zipHoldsStoresPlistAndReportOnly() {
        val expected = layout()
        val path = write()
        assertEquals((out / "AndBible-old-data-42.zip").toString(), path)
        val actual = readZip(path)
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (name, bytes) -> assertContentEquals(bytes, actual.getValue(name), name) }
    }

    @Test fun missingOptionalFilesAreSkipped() {
        put(support / "AndBible.store", "cloud")
        put(support / "LocalStore.store", "local")
        val actual = readZip(write())
        assertEquals(setOf("AndBible.store", "LocalStore.store"), actual.keys)
    }

    @Test fun sourcesUnchanged() {
        val expected = layout()
        val paths = listOf(support / "AndBible.store", support / "AndBible.store-wal", support / "AndBible.store-shm",
            support / "LocalStore.store", support / "AndBible.store.backup", prefs / "$bundleId.plist",
            support / "LegacyMigration" / "report.json", support / "LegacyMigration" / "state.json")
        fun hashes() = paths.associateWith { fs.read(it) { readByteString() }.sha256() }
        val before = hashes()
        val modified = paths.associateWith { fs.metadata(it).lastModifiedAtMillis }
        write()
        assertEquals(before, hashes())
        assertEquals(modified, paths.associateWith { fs.metadata(it).lastModifiedAtMillis })
        assertEquals(expected.size + 1, paths.size) // the state file is in the list and stays untouched
        assertTrue(paths.all { fs.exists(it) })
    }

    @Test fun reportBytesAreReadFromTheMigrationDirectory() {
        assertEquals(null, IosLegacyOldDataArchive.reportBytes(LegacyMigrationControl(fs, support), fs))
        val bytes = put(support / "LegacyMigration" / "report.json", "{\"a\":1}")
        assertContentEquals(bytes, IosLegacyOldDataArchive.reportBytes(LegacyMigrationControl(fs, support), fs))
        assertTrue(bytes.toByteString().size > 0)
    }
}
