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

package net.bible.service.installzip

import kotlinx.coroutines.runBlocking
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Thin-adapter tests for [AndroidInstallCommitter]. Since every `commit*` here delegates to
 * already-tested engine code (see [net.bible.android.control.backup.ModuleBackupRoundTripTest] /
 * `ModuleArchiveExtractionTest` for that coverage), this only exercises: (1) the one genuine
 * bit of logic this file owns -- the SQLite header validity gate -- and (2) one real,
 * self-contained integration-style commit ([commitSwordZip]) that proves the wiring reaches the
 * shared engine and reports progress, without depending on any externally-provisioned test
 * modules (`~/.sword` / `.local/testmods.zip`): a minimal, hand-fabricated RawLD dictionary
 * (same fixture shape as `ModuleBackupRoundTripTest.registerMinimalRawLdDictionary`) is fully
 * sufficient to prove extraction + registration end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidInstallCommitterTest {
    private val committer = AndroidInstallCommitter()

    @Before
    fun setUp() {
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
    }

    @After
    fun tearDown() {
        Books.installed().getBook("TestDict")?.let { Books.installed().removeBook(it) }
        File(SharedConstants.modulesDir, "mods.d/testdict.conf").delete()
        File(SharedConstants.modulesDir, "modules/lexdict/rawld/testdict").deleteRecursively()
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
     * A minimal RawLD dictionary zip (conf + 2 data files) is the simplest genuine SWORD module
     * fixture (mirrors `ModuleBackupRoundTripTest`). Proves [AndroidInstallCommitter.commitSwordZip]
     * really delegates to [net.bible.android.control.backup.BackupControl.extractAndRegisterModuleArchive]
     * end-to-end: the `.conf` lands on disk, the module registers and is readable, and progress
     * reaches 100%.
     */
    @Test
    fun commitSwordZipExtractsAndRegistersRealModule() = runBlocking {
        val conf = """
            [TestDict]
            DataPath=./modules/lexdict/rawld/testdict/test
            ModDrv=RawLD
            SourceType=Plaintext
            Encoding=UTF-8
            Lang=en
            Description=Test Dictionary
            DistributionLicense=Public Domain
        """.trimIndent()
        val datBytes = "strong\nThe test definition body.".toByteArray(Charsets.UTF_8)
        val idxBytes = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putShort(datBytes.size.toShort()).array()

        val zip = buildZip(
            "commit-sword-zip.zip", listOf(
                "mods.d/testdict.conf" to conf.toByteArray(Charsets.UTF_8),
                "modules/lexdict/rawld/testdict/test.dat" to datBytes,
                "modules/lexdict/rawld/testdict/test.idx" to idxBytes,
            )
        )

        val progressValues = mutableListOf<Int>()
        committer.commitSwordZip(zip, totalEntries = 3, onProgress = { progressValues.add(it) })

        val downloadDir = SwordBookPath.getSwordDownloadDir()
        assertTrue("conf must land on disk", File(downloadDir, "mods.d/testdict.conf").exists())
        assertEquals("progress must reach 100%", 100, progressValues.last())

        val book = Books.installed().getBook("TestDict")
        assertNotNull("module should register from the committed zip", book)
        assertTrue(
            "registered module should be readable",
            book!!.getRawText(book.getKey("strong")).contains("The test definition body.")
        )
    }

    @Test
    fun commitSqliteThrowsInvalidInstallFileOnBadHeader() = runBlocking {
        val bogus = File(CommonUtils.tmpDir, "not-a-sqlite-db.mybible")
        bogus.writeBytes("this is not a sqlite database at all".toByteArray(Charsets.UTF_8))

        var thrown: InvalidInstallFile? = null
        try {
            committer.commitSqlite(bogus, SqliteBookType.MYSWORD, "not-a-sqlite-db.mybible")
        } catch (e: InvalidInstallFile) {
            thrown = e
        }

        assertNotNull("a non-SQLite file must be rejected", thrown)
        assertEquals("not-a-sqlite-db.mybible", thrown!!.filename)
        assertFalse(
            "no output file should be written when the header check fails",
            File(File(SharedConstants.modulesDir, "mysword"), "not-a-sqlite-db.mybible").exists()
        )
    }
}
