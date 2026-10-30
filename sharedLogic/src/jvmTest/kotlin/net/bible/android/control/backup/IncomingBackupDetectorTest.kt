/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.control.backup

import kotlinx.coroutines.runBlocking
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import okio.Buffer
import okio.source
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncomingBackupDetectorTest {
    private val fixture = File("../app/src/test/resources/l1b-g2/db-backup.abdb.zip")

    private fun zipOf(vararg names: String, manifest: BackupType? = null): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            if (manifest != null) {
                z.putNextEntry(ZipEntry("AndBibleBackupManifest.json"))
                z.write(AndBibleBackupManifest(manifest).toJson().toByteArray()); z.closeEntry()
            }
            for (n in names) { z.putNextEntry(ZipEntry(n)); z.write("x".toByteArray()); z.closeEntry() }
        }
        return bytes.toByteArray()
    }

    private fun detect(name: String?, bytes: ByteArray) = runBlocking { IncomingBackupDetector.isDatabaseBackup(name) { Buffer().write(bytes) } }

    @Test fun realBackupZipIsDetectedByContentUnderAnyName() {
        assertTrue(fixture.isFile, "fixture missing: ${fixture.absolutePath}")
        assertTrue(runBlocking { IncomingBackupDetector.isDatabaseBackup("copy of backup.zip") { fixture.source() } })
        assertTrue(runBlocking { IncomingBackupDetector.isDatabaseBackup("db$DATABASE_BACKUP_SUFFIX") { fixture.source() } })
    }

    @Test fun studyPadExportNamedAsBackupIsNotARestore() {
        // StudyPad exports are named <pad>.abdb.zip and carry db/bookmarks.sqlite3: InstallZip imports them.
        val pad = zipOf("db/bookmarks.sqlite3", manifest = BackupType.STUDYPAD_EXPORT)
        assertFalse(detect("x$DATABASE_BACKUP_SUFFIX", pad))
        assertFalse(detect("StudyPads$DATABASE_BACKUP_SUFFIX", pad))
    }

    @Test fun moduleBackupIsNotARestore() {
        assertFalse(detect("AndBibleModulesBackup.abmd.zip", zipOf("modules/x", manifest = BackupType.MODULE_BACKUP)))
        assertFalse(detect("m$DATABASE_BACKUP_SUFFIX", zipOf("db/bookmarks.sqlite3", manifest = BackupType.MODULE_BACKUP)))
    }

    @Test fun nameAloneNeverDecides() {
        assertFalse(detect("a$DATABASE_BACKUP_SUFFIX", zipOf("mods.d/kjv.conf")))
    }

    @Test fun manifestedDbBackupIsARestore() {
        assertTrue(detect("x.zip", zipOf("db/bookmarks.sqlite3", manifest = BackupType.DB_BACKUP)))
    }

    @Test fun manifestlessLegacyBackupFallsBackToDbEntries() {
        assertTrue(detect("old.zip", zipOf("db/bookmarks.sqlite3")))
    }

    @Test fun moduleZipIsNotABackup() {
        assertFalse(detect("KJV.zip", zipOf("mods.d/kjv.conf", "modules/texts/ztext/kjv/ot.bzs")))
    }

    @Test fun zipWithOnlyUnknownDbEntryOrManifestlessNonDbIsNotABackup() {
        assertFalse(detect("x.zip", zipOf("db/unrelated.sqlite3")))
    }

    @Test fun nonZipAndBrokenFilesAreNotBackups() {
        assertFalse(detect("font.ttf", zipOf("db/bookmarks.sqlite3")))
        assertFalse(detect("a.sqlite3", "SQLite format 3\u0000".toByteArray()))
        assertFalse(detect("broken.zip", "not a zip at all".toByteArray()))
    }

    @Test fun legacyMonolithicAbdbIsABackupOnlyWithSqliteHeader() {
        assertTrue(detect("old.abdb", "SQLite format 3\u0000rest".toByteArray()))
        assertFalse(detect("old.abdb", "nope".toByteArray()))
    }
}
