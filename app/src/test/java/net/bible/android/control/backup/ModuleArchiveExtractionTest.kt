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
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Characterization tests for [BackupControl.extractAndRegisterModuleArchive] — the shared,
 * headless extraction core used by the sideload `InstallZip` flow, "Backup modules" restore,
 * and cloud document-sync downloads. These feed it hand-built archives to pin behaviour that
 * full round-trip tests don't surface (the backup zips it produces never contain backslashes
 * or a misplaced manifest), so a refactor of the shared core can't silently regress any caller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ModuleArchiveExtractionTest {
    private lateinit var downloadDir: File

    @Before
    fun setUp() {
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
        downloadDir = SwordBookPath.getSwordDownloadDir()
    }

    /** Build a zip in [CommonUtils.tmpDir] with the given entries, in order. */
    private fun buildZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val zipFile = File(CommonUtils.tmpDir, name)
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(FileOutputStream(zipFile)).use { out ->
            for ((entryName, bytes) in entries) {
                out.putNextEntry(ZipEntry(entryName))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return zipFile
    }

    /**
     * The manifest entry is metadata, not a module file: it must be skipped, never written into
     * the module tree, while the genuine module files around it are extracted.
     */
    @Test
    fun manifestEntryIsNotExtracted() = runBlocking {
        val zip = buildZip("manifest-skip.zip", listOf(
            ANDBIBLE_BACKUP_MANIFEST_FILENAME to """{"backupType":"MODULE_BACKUP"}""".toByteArray(),
            "modules/texts/data.dat" to byteArrayOf(1, 2, 3),
        ))

        BackupControl.extractAndRegisterModuleArchive({ FileInputStream(zip) })

        assertFalse(
            "Manifest must not be written into the module directory",
            File(downloadDir, ANDBIBLE_BACKUP_MANIFEST_FILENAME).exists()
        )
        assertTrue(File(downloadDir, "modules/texts/data.dat").exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(downloadDir, "modules/texts/data.dat").readBytes())
    }

    /**
     * Archives produced on Windows can carry backslash path separators; the extractor must
     * normalize them so files land in the correct nested directories rather than as a single
     * literal-named file.
     */
    @Test
    fun backslashSeparatorsAreNormalized() = runBlocking {
        val zip = buildZip("backslash.zip", listOf(
            "modules\\texts\\win\\path.dat" to byteArrayOf(9),
        ))

        BackupControl.extractAndRegisterModuleArchive({ FileInputStream(zip) })

        assertTrue(File(downloadDir, "modules/texts/win/path.dat").exists())
        assertFalse(File(downloadDir, "modules\\texts\\win\\path.dat").exists())
    }

    /** Deeply nested entries must have their parent directories created on demand. */
    @Test
    fun nestedDirectoriesAreCreated() = runBlocking {
        val zip = buildZip("nested.zip", listOf(
            "modules/a/b/c/d/deep.dat" to byteArrayOf(42),
        ))

        BackupControl.extractAndRegisterModuleArchive({ FileInputStream(zip) })

        assertTrue(File(downloadDir, "modules/a/b/c/d/deep.dat").exists())
    }

    /**
     * An archive with no SWORD conf and no manually-installed modules must extract its files
     * without throwing (no conf to register is not an error).
     */
    @Test
    fun archiveWithoutConfExtractsWithoutError() = runBlocking {
        val zip = buildZip("noconf.zip", listOf(
            "modules/texts/only-data.dat" to byteArrayOf(7),
        ))

        BackupControl.extractAndRegisterModuleArchive({ FileInputStream(zip) })

        assertTrue(File(downloadDir, "modules/texts/only-data.dat").exists())
    }
}
