package net.bible.service.common

import okio.Buffer
import okio.buffer
import okio.source
import org.crosswire.common.compress.ZipWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndBibleBackupManifestTest {
    @Test fun toJsonPinsModuleBackupWithoutContains() {
        val manifest = AndBibleBackupManifest(BackupType.MODULE_BACKUP, andBibleVersion = 1234)
        assertEquals(
            """{"backupType":"MODULE_BACKUP","contains":null,"manifestVersion":1,"andBibleVersion":1234}""",
            manifest.toJson()
        )
    }

    @Test fun toJsonPinsStudyPadExportWithContains() {
        val manifest = AndBibleBackupManifest(BackupType.STUDYPAD_EXPORT, setOf(DbType.BOOKMARKS), andBibleVersion = 1234)
        assertEquals(
            """{"backupType":"STUDYPAD_EXPORT","contains":["BOOKMARKS"],"manifestVersion":1,"andBibleVersion":1234}""",
            manifest.toJson()
        )
    }

    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("l1b-g2/$name")) { name }.source().buffer()

    @Test fun fromZipReadsTask1ModuleBackupManifest() {
        val manifest = AndBibleBackupManifest.fromZip(fixture("module-backup.abmd.zip"))
        assertNotNull(manifest)
        assertEquals(BackupType.MODULE_BACKUP, manifest!!.backupType)
        assertNull(manifest.contains)
        assertEquals(1, manifest.manifestVersion)
    }

    @Test fun fromZipReturnsNullWhenFirstEntryIsNotTheManifest() {
        val bytes = Buffer()
        ZipWriter(bytes).use { zip ->
            zip.putEntry("other.txt") { it.writeUtf8("x") }
            AndBibleBackupManifest(BackupType.DB_BACKUP, andBibleVersion = 5).saveToZip(zip)
        }
        assertNull(AndBibleBackupManifest.fromZip(bytes))
    }

    @Test fun saveToZipThenFromZipRoundTrips() {
        val original = AndBibleBackupManifest(
            BackupType.DB_BACKUP, setOf(DbType.BOOKMARKS, DbType.AI_SETTINGS), andBibleVersion = 4321
        )
        val bytes = Buffer()
        ZipWriter(bytes).use { original.saveToZip(it) }
        assertEquals(original, AndBibleBackupManifest.fromZip(bytes))
    }
}
