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

    // Shapes written by releases 822 (03fd5ca88) and 823 (b48191274): no andBibleVersion, "version" key.
    private val json822 = """{"backupType":"STUDYPAD_EXPORT","contains":["BOOKMARKS"],"studyPadNames":["x"],"version":1}"""
    private val json823 = """{"backupType":"STUDYPAD_EXPORT","contains":["BOOKMARKS"],"version":1}"""

    @Test fun fromJsonDecodesRelease822And823Manifests() {
        for (json in listOf(json822, json823)) {
            val m = AndBibleBackupManifest.fromJson(json)
            assertEquals(BackupType.STUDYPAD_EXPORT, m.backupType)
            assertEquals(setOf(DbType.BOOKMARKS), m.contains)
            assertEquals(0, m.andBibleVersion)
        }
    }

    @Test fun fromZipReadsOldReleaseManifestInFirstEntry() {
        for (json in listOf(json822, json823)) {
            val bytes = Buffer()
            ZipWriter(bytes).use { it.putEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME) { sink -> sink.writeUtf8(json) } }
            val m = AndBibleBackupManifest.fromZip(bytes)
            assertEquals(BackupType.STUDYPAD_EXPORT, m?.backupType)
        }
    }
}
