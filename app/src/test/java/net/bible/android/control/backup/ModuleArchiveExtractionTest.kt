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
import net.bible.android.DurableTestBibleApplication
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.service.common.CommonUtils
import net.bible.service.installzip.myBibleBytes
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.sword.mybible.addManuallyInstalledMyBibleBooks
import net.bible.service.sword.mybible.addMyBibleBook
import net.bible.service.sword.mybible.SqliteBackend
import okio.Path.Companion.toOkioPath
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.sword.SwordBook
import org.junit.Assert.assertEquals
import java.util.zip.ZipFile
import org.crosswire.jsword.book.Books
import okio.source
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
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
 * Characterization tests for [BackupControl.extractAndRegisterModuleArchive] — the shared,
 * headless extraction core used by the sideload `InstallZip` flow, "Backup modules" restore,
 * and cloud document-sync downloads. These feed it hand-built archives to pin behaviour that
 * full round-trip tests don't surface (the backup zips it produces never contain backslashes
 * or a misplaced manifest), so a refactor of the shared core can't silently regress any caller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = DurableTestBibleApplication::class, sdk = [TEST_SDK])
class ModuleArchiveExtractionTest {
    private lateinit var downloadDir: File
    private var booksBefore: Set<org.crosswire.jsword.book.Book> = emptySet()

    /** Books registered by a test (MyBible modules are auto-registered on extraction) must not leak into the one-JVM suite. */
    @After
    fun tearDown() {
        Books.installed().getBooks().filter { it !in booksBefore }.forEach {
            if (it is SwordBook && it.backend is SqliteBackend) closeMyBible(it)
            Books.installed().removeBook(it)
        }
        File(downloadDir, "mybible").deleteRecursively()
    }

    @Before
    fun setUp() {
        booksBefore = Books.installed().getBooks().toSet()
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        downloadDir = SwordBookPathJvm.getSwordDownloadDir()
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

        org.koin.java.KoinJavaComponent.get<net.bible.android.control.backup.BackupControl>(net.bible.android.control.backup.BackupControl::class.java).extractAndRegisterModuleArchive({ zip.source() })

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

        org.koin.java.KoinJavaComponent.get<net.bible.android.control.backup.BackupControl>(net.bible.android.control.backup.BackupControl::class.java).extractAndRegisterModuleArchive({ zip.source() })

        assertTrue(File(downloadDir, "modules/texts/win/path.dat").exists())
        assertFalse(File(downloadDir, "modules\\texts\\win\\path.dat").exists())
    }

    /** Deeply nested entries must have their parent directories created on demand. */
    @Test
    fun nestedDirectoriesAreCreated() = runBlocking {
        val zip = buildZip("nested.zip", listOf(
            "modules/a/b/c/d/deep.dat" to byteArrayOf(42),
        ))

        org.koin.java.KoinJavaComponent.get<net.bible.android.control.backup.BackupControl>(net.bible.android.control.backup.BackupControl::class.java).extractAndRegisterModuleArchive({ zip.source() })

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

        org.koin.java.KoinJavaComponent.get<net.bible.android.control.backup.BackupControl>(net.bible.android.control.backup.BackupControl::class.java).extractAndRegisterModuleArchive({ zip.source() })

        assertTrue(File(downloadDir, "modules/texts/only-data.dat").exists())
    }

    /** DL-15: a MyBible module at the archive root belongs in `mybible/`, auxiliary files alongside. */
    @Test
    fun rootLevelMyBibleModuleInstallsUnderMyBibleDirWithAuxiliaryFiles() = runBlocking {
        File(downloadDir, "mybible").deleteRecursively()
        val bytes = myBibleBytes()
        val zip = buildZip("root-mybible.zip", listOf(
            "KJV.SQLite3" to bytes,
            "KJV.commentaries.SQLite3" to bytes,
        ))

        org.koin.java.KoinJavaComponent.get<net.bible.android.control.backup.BackupControl>(net.bible.android.control.backup.BackupControl::class.java).extractAndRegisterModuleArchive({ zip.source() })

        assertArrayEquals(bytes, File(downloadDir, "mybible/KJV.SQLite3").readBytes())
        assertArrayEquals(bytes, File(downloadDir, "mybible/KJV.commentaries.SQLite3").readBytes())
        assertFalse(File(downloadDir, "KJV.SQLite3").exists())
    }

    /** Control: the existing `mybible/`-prefixed layout is unchanged. */
    @Test
    fun prefixedMyBibleModuleStillInstallsUnderMyBibleDir() = runBlocking {
        File(downloadDir, "mybible").deleteRecursively()
        val bytes = myBibleBytes()
        val zip = buildZip("prefixed-mybible.zip", listOf("mybible/KJV2.SQLite3" to bytes))

        org.koin.java.KoinJavaComponent.get<net.bible.android.control.backup.BackupControl>(net.bible.android.control.backup.BackupControl::class.java).extractAndRegisterModuleArchive({ zip.source() })

        assertArrayEquals(bytes, File(downloadDir, "mybible/KJV2.SQLite3").readBytes())
    }

    @Test
    fun oldMyBibleArchiveAfterEmptyStartupKeepsLegacyNumbering() = runBlocking {
        File(downloadDir, "mybible").deleteRecursively()
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        val bytes = germanMyBibleBytes()
        val zip = buildZip("legacy-numbering.zip", listOf("LegacyNumbering.SQLite3" to bytes))

        org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
            .extractAndRegisterModuleArchive({ zip.source() })

        assertEquals("KJVA", installedVersification("LegacyNumbering"))
    }

    @Test
    fun myBibleBackupRestoresGermanDecisionEvenWhenContentsDetectKjva() = runBlocking {
        File(downloadDir, "mybible").deleteRecursively()
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        val module = File(downloadDir, "mybible/BackupNumbering.SQLite3")
        module.parentFile!!.mkdirs()
        module.writeBytes(germanMyBibleBytes())
        addMyBibleBook(module.toOkioPath())
        assertEquals("German", installedVersification("BackupNumbering"))
        val book = Books.installed().getBook("MyBible-BackupNumbering") as SwordBook

        // The installation's decision must survive a subsequent change in detectable content.
        closeMyBible(book)
        module.writeBytes(myBibleBytes())
        val backup = org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
        val archive = File(CommonUtils.tmpDir, "mybible-numbering.abmd.zip")
        backup.createSingleModuleZip(book, archive.toOkioPath())
        ZipFile(archive).use { zip ->
            val sidecar = zip.getEntry("mybible/BackupNumbering.SQLite3.andbible-versification")
            assertTrue("Backup must contain the numbering decision", sidecar != null)
            assertEquals("German", zip.getInputStream(sidecar).bufferedReader().readText().trim())
        }
        Books.installed().removeBook(book)
        module.delete()
        // A distinct target name has no previously saved decision and detects KJVA for empty verses.
        val entries = ZipFile(archive).use { zip ->
            zip.entries().toList().filterNot { it.isDirectory }.map { entry ->
                entry.name.replace("BackupNumbering", "RestoredNumbering") to zip.getInputStream(entry).readBytes()
            }
        }
        val renamedArchive = buildZip("mybible-renamed.abmd.zip", entries)
        backup.extractAndRegisterModuleArchive({ renamedArchive.source() })
        assertEquals("German", installedVersification("RestoredNumbering"))
    }

    @Test
    fun oldArchiveReplacesRegisteredGermanWithKjva() = runBlocking {
        val module = File(downloadDir, "mybible/ReplaceGerman.SQLite3")
        module.parentFile!!.mkdirs()
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        module.writeBytes(germanMyBibleBytes())
        addMyBibleBook(module.toOkioPath())
        assertEquals("German", installedVersification("ReplaceGerman"))

        val zip = buildZip("replace-german.zip", listOf("mybible/ReplaceGerman.SQLite3" to germanMyBibleBytes()))
        org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
            .extractAndRegisterModuleArchive({ zip.source() })
        assertEquals("KJVA", installedVersification("ReplaceGerman"))
    }

    @Test
    fun explicitGermanArchiveReplacesRegisteredKjva() = runBlocking {
        val module = File(downloadDir, "mybible/ReplaceKjva.SQLite3")
        module.parentFile!!.mkdirs()
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        module.writeBytes(myBibleBytes())
        addMyBibleBook(module.toOkioPath())
        assertEquals("KJVA", installedVersification("ReplaceKjva"))

        val zip = buildZip("replace-kjva.zip", listOf(
            "mybible/ReplaceKjva.SQLite3.andbible-versification" to "German".toByteArray(),
            "mybible/ReplaceKjva.SQLite3" to myBibleBytes(),
        ))
        org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
            .extractAndRegisterModuleArchive({ zip.source() })
        assertEquals("German", installedVersification("ReplaceKjva"))
    }

    @Test
    fun decisionWriteFailureRollsBackModuleBeforeRegistration() = runBlocking {
        File(downloadDir, "mybible").deleteRecursively()
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        val records = File(SharedConstants.internalFilesDir, "mybible-versifications")
        val saved = File(SharedConstants.internalFilesDir, "mybible-versifications-test-aside")
        check(!saved.exists())
        val hadRecords = records.exists()
        if (hadRecords) check(records.renameTo(saved))
        try {
            records.writeText("block directory creation")
            val zip = buildZip("numbering-write-failure.zip", listOf(
                "mybible/WriteFailure.SQLite3" to myBibleBytes(),
                "mybible/WriteFailure.SQLite3.andbible-versification" to "German".toByteArray(),
            ))
            val backup = org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
            try {
                backup.extractAndRegisterModuleArchive({ zip.source() })
                org.junit.Assert.fail("Failed decision write must reject restore")
            } catch (expected: java.io.IOException) {
                assertFalse(File(downloadDir, "mybible/WriteFailure.SQLite3").exists())
                assertTrue(Books.installed().getBook("MyBible-WriteFailure") == null)
            }
        } finally {
            records.delete()
            if (hadRecords) check(saved.renameTo(records))
        }
    }

    @Test
    fun invalidMyBibleVersificationRejectsArchiveBeforeRegistration() = runBlocking {
        val zip = buildZip("invalid-numbering.zip", listOf(
            "mybible/InvalidNumbering.SQLite3" to myBibleBytes(),
            "mybible/InvalidNumbering.SQLite3.andbible-versification" to "not-a-versification".toByteArray(),
        ))
        val backup = org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
        try {
            backup.extractAndRegisterModuleArchive({ zip.source() })
            org.junit.Assert.fail("Invalid versification must reject the archive")
        } catch (expected: java.io.IOException) {
            assertFalse(File(downloadDir, "mybible/InvalidNumbering.SQLite3").exists())
            assertTrue(Books.installed().getBook("MyBible-InvalidNumbering") == null)
        }
    }

    private fun installedVersification(name: String): String =
        (Books.installed().getBook("MyBible-$name") as SwordBook).versification.name

    private fun closeMyBible(book: SwordBook) {
        Activator.deactivate(book)
        (book.backend as SqliteBackend).state.close()
    }

    private fun germanMyBibleBytes(): ByteArray {
        val file = File.createTempFile("backup-german", ".SQLite3")
        try {
            openSqlite(file.path).use { db ->
                db.exec("CREATE TABLE info (name TEXT, value TEXT)")
                db.exec("INSERT INTO info VALUES ('language', 'de')")
                db.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
                listOf(14, 17, 24).forEachIndexed { chapter, verse ->
                    db.exec("INSERT INTO verses VALUES (460, ${chapter + 1}, $verse, 'Chapter end')")
                }
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    /** Zip-slip: an entry escaping the module directory fails the whole archive and writes nothing outside it. */
    @Test
    fun traversalEntryFailsTheArchiveAndWritesNothingOutside() = runBlocking {
        val zip = buildZip("traversal.zip", listOf(
            "modules/texts/ok.dat" to byteArrayOf(1),
            "../../evil.conf" to byteArrayOf(2),
        ))
        val escaped = File(downloadDir, "../../evil.conf").canonicalFile
        escaped.delete()

        val backup = org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
        try {
            backup.extractAndRegisterModuleArchive({ zip.source() })
            org.junit.Assert.fail("expected IOException")
        } catch (e: java.io.IOException) {
            // expected
        }
        assertFalse("Traversal entry must not be written", escaped.exists())
    }

    /** The same through backslashes and an absolute name: both fail the archive, nothing lands outside. */
    @Test
    fun backslashAndAbsoluteTraversalEntriesFailTheArchive() = runBlocking {
        val backup = org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
        val escaped = File(downloadDir, "../evil-backslash.dat").canonicalFile.also { it.delete() }
        for ((name, entry) in listOf("backslash-traversal.zip" to "..\\evil-backslash.dat", "absolute.zip" to "/tmp/evil-absolute.dat")) {
            val zip = buildZip(name, listOf("modules/texts/ok.dat" to byteArrayOf(1), entry to byteArrayOf(2)))
            try {
                backup.extractAndRegisterModuleArchive({ zip.source() })
                org.junit.Assert.fail("expected IOException for $entry")
            } catch (e: java.io.IOException) {
                // expected
            }
        }
        assertFalse("Traversal entry must not be written", escaped.exists())
        assertFalse(File("/tmp/evil-absolute.dat").exists())
    }
}
