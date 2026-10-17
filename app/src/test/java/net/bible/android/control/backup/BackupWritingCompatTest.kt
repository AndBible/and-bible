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
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * L1b G2 Task 9: what [BackupControl] writes stays what releases wrote (entry names and order are pinned by the
 * `l1b-g2` fixtures, produced by the `java.util.zip` code), and the archives written now are read by the
 * `java.util.zip` readers that the current release restores with. A failed backup must not leave a
 * complete-looking archive under its final name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupWritingCompatTest {
    private val fixtureDir = File("src/test/resources/l1b-g2")
    private val backup by lazy { net.bible.test.testBackupControl() }

    @Before fun setUp() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    private fun namesOf(file: File): List<String> =
        ZipInputStream(FileInputStream(file)).use { zis -> generateSequence { zis.nextEntry }.map { it.name }.toList() }

    private fun isv() = Books.installed().getBook("ISV") ?: error("test module ISV not installed in ~/.sword")

    private fun newZip(name: String) = File(CommonUtils.tmpDir, name).also { it.delete() }

    @Test fun moduleBackupEntryNamesMatchFixture() = runBlocking {
        val zip = newZip("compat-module.abmd.zip")
        backup.createSingleModuleZip(isv(), zip.toOkioPath())
        val names = namesOf(zip)
        assertEquals(ANDBIBLE_BACKUP_MANIFEST_FILENAME, names.first())
        assertEquals(namesOf(File(fixtureDir, "module-backup.abmd.zip")).sorted(), names.sorted())
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun databaseBackupEntryNamesMatchFixture() = runBlocking {
        // a write creates the bookmark database file, as the fixture generator did
        DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "compat label"))
        val names = namesOf(backup.zipDatabaseFiles()!!.toFile())
        assertEquals(ANDBIBLE_BACKUP_MANIFEST_FILENAME, names.first())
        assertEquals(namesOf(File(fixtureDir, "db-backup.abdb.zip")).sorted(), names.sorted())
    }

    @Test fun moduleBackupIsReadByJdkZipFile() = runBlocking {
        val zip = newZip("compat-jdk.abmd.zip")
        backup.createSingleModuleZip(isv(), zip.toOkioPath())
        ZipFile(zip).use { zf ->
            val fixture = ZipFile(File(fixtureDir, "module-backup.abmd.zip"))
            fixture.use { fx ->
                for (expected in fx.entries().toList().filter { it.name != ANDBIBLE_BACKUP_MANIFEST_FILENAME }) {
                    val actual = zf.getEntry(expected.name)
                    assertNotNull("missing ${expected.name}", actual)
                    assertEquals(expected.name, expected.size, actual.size)
                    assertEquals(expected.name, expected.crc, actual.crc)
                }
            }
            val manifest = zf.getInputStream(zf.getEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME)).use { it.readBytes() }.decodeToString()
            assertTrue(manifest, manifest.contains("MODULE_BACKUP") || manifest.contains("backupType"))
            assertTrue("entries carry a timestamp", zf.entries().toList().all { it.time > 0 })
        }
    }

    @Test fun newModuleBackupRestoresThroughOldExtractionCode() = runBlocking {
        val book = isv()
        val zip = newZip("compat-restore.abmd.zip")
        backup.createSingleModuleZip(book, zip.toOkioPath())
        val sourceRoot = SwordBookMetaDataJvm.getConfigFile(book.bookMetaData as SwordBookMetaData)!!.parentFile!!.parentFile!!
        val original = namesOf(zip).filter { it != ANDBIBLE_BACKUP_MANIFEST_FILENAME }.associateWith { File(sourceRoot, it).readBytes() }
        val target = SwordBookPathJvm.getSwordDownloadDir()
        assertTrue("restore must go to another directory than the source", target.canonicalPath != sourceRoot.canonicalPath)
        Books.installed().removeBook(book)
        try {
            assertTrue(backup.installModuleArchive(zip, "ISV"))

            for ((name, bytes) in original) assertTrue("$name restored identically", bytes.contentEquals(File(target, name).readBytes()))
            assertNotNull(Books.installed().getBook("ISV"))
        } finally {
            Books.installed().getBook("ISV")?.let { Books.installed().removeBook(it) }
            Books.installed().addBook(book)
            File(target, "mods.d/isv.conf").delete()
            File(target, "modules/texts/ztext/isv").deleteRecursively()
        }
    }

    @Test fun fileVanishingDuringBackupFailsWithoutFinalArchive() = runBlocking {
        val book = isv()
        val root = SwordBookMetaDataJvm.getConfigFile(book.bookMetaData as SwordBookMetaData)!!.parentFile!!.parentFile!!
        val victim = File(root, "modules/texts/ztext/isv/nt.bzs")
        val saved = victim.readBytes()
        val zip = File(CommonUtils.tmpDir, MODULE_BACKUP_NAME).also { it.delete() }
        backup.onModuleFilesListed = { victim.delete() }
        try {
            backup.createSingleModuleZip(book, zip.toOkioPath())
            fail("backup must fail when a module file vanishes")
        } catch (e: java.io.IOException) {
            // expected: the file was listed but is gone when zipped
        } finally {
            backup.onModuleFilesListed = {}
            victim.writeBytes(saved)
        }
        assertFalse("no complete-looking archive under the final name", zip.exists())
        assertFalse("no leftover temp archive", File(zip.path + ".tmp").exists())
    }
}
