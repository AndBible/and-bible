package net.bible.service.common

import net.bible.service.db.newTestDir
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.use
import org.crosswire.common.compress.ZipWriter
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArchiveRoundTripCommonTest {
    private val fs = FileSystem.SYSTEM

    @Test
    fun manifestAndFileSurviveZipAndUnzip() {
        val dir = newTestDir().toPath()
        val zip = dir / "backup.zip"
        ZipWriter(fs.sink(zip)).use { writer ->
            AndBibleBackupManifest(BackupType.MODULE_BACKUP, andBibleVersion = 7).saveToZip(writer)
            writer.putEntry("dir/a.txt") { it.writeUtf8("äö 1") }
        }

        val manifest = assertNotNull(AndBibleBackupManifest.fromZip(fs.source(zip)))
        assertEquals(BackupType.MODULE_BACKUP, manifest.backupType)
        assertEquals(7, manifest.andBibleVersion)

        val out = dir / "out"
        ArchiveUtils.unzip(zip, out)
        assertEquals("äö 1", fs.read(out / "dir" / "a.txt") { readUtf8() })
    }

    @Test
    fun gzipThenGunzipIsIdentity() {
        val dir = newTestDir().toPath()
        val original = Random(42).nextBytes(100_000)
        fs.write(dir / "in.bin") { write(original) }

        ArchiveUtils.gzipFile(dir / "in.bin", dir / "in.bin.gz")
        ArchiveUtils.gunzipFile(dir / "in.bin.gz", dir / "out.bin")

        assertContentEquals(original, fs.read(dir / "out.bin") { readByteArray() })
    }
}
