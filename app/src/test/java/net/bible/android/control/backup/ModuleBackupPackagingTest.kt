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
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * Verifies the per-document-type packaging branches of [BackupControl] produce the correct
 * `modulesDir`-relative zip entry paths — the part most likely to silently break for the
 * non-SWORD types, since each has its own layout (`addModuleFile` for a single sqlite file,
 * `addModuleDir` for a directory tree).
 *
 * These tests stop at packaging: they don't drive the full restore round-trip, because
 * [BackupControl.extractAndRegisterModuleArchive] always finishes by rescanning the
 * manually-installed module dirs, and for MyBible that means opening the sqlite via the
 * requery driver (excluded from the unit-test classpath, see app/build.gradle.kts) while for
 * EPUB the rescan parses and then deletes any dir that isn't a fully-optimized epub. Full
 * register-on-restore for these types is covered by on-device/instrumented testing. The
 * packaging path — the part the backup refactor restructured — is fully covered here.
 *
 * Books are built from byte-array metadata (no on-disk conf, no backend I/O) so construction
 * needs neither a real module nor SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ModuleBackupPackagingTest {
    private fun fakeBook(initials: String, conf: String): Book {
        val bmd = SwordBookMetaData(conf.toByteArray(), initials)
        return SwordBook(bmd, NullBackend())
    }

    private suspend fun entriesOf(book: Book, zipName: String): Set<String> {
        val zipFile = File(CommonUtils.tmpDir, zipName)
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)
        return ZipFile(zipFile).use { zf -> zf.entries().toList().map { it.name }.toSet() }
    }

    /**
     * A MyBible module is a single sqlite file; packaging must archive it via `addModuleFile`
     * at its `mybible/`-relative path so restore drops it back where the rescan looks.
     */
    @Test
    fun myBibleModulePackagesSqliteFile() = runBlocking {
        val dbFile = File(SharedConstants.modulesDir, "mybible/TestModule.sqlite3").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val book = fakeBook("MyBible-TestModule", """
            [MyBible-TestModule]
            Description=Test MyBible
            ModDrv=RawGenBook
            DataPath=./mybible/
            Encoding=UTF-8
            AndBibleMyBibleModule=1
            AndBibleDbFile=${dbFile.path}
        """.trimIndent())

        val entries = entriesOf(book, "mybible-pkg.abmd.zip")

        assertTrue("manifest must be packaged", entries.contains(ANDBIBLE_BACKUP_MANIFEST_FILENAME))
        assertTrue(
            "sqlite file must be packaged at its modulesDir-relative path; entries=$entries",
            entries.contains("mybible/TestModule.sqlite3")
        )
    }

    /**
     * An EPUB is an unpacked directory tree; packaging must walk the whole `epub/<name>/` dir
     * via `addModuleDir`, preserving each file's `modulesDir`-relative path.
     */
    @Test
    fun epubModulePackagesWholeDirectoryTree() = runBlocking {
        val epubRoot = File(SharedConstants.modulesDir, "epub/TestEpub")
        File(epubRoot, "META-INF").mkdirs()
        File(epubRoot, "META-INF/container.xml").writeText("<container/>")
        File(epubRoot, "content.opf").writeText("<package/>")
        File(epubRoot, "optimized").mkdirs()
        File(epubRoot, "optimized/000.xhtml.gz").writeBytes(byteArrayOf(0x1f, 0x8b.toByte()))

        val book = fakeBook("Epub-TestEpub", """
            [Epub-TestEpub]
            Description=Test Epub
            ModDrv=RawGenBook
            DataPath=./epub/
            Encoding=UTF-8
            AndBibleEpubModule=1
            AndBibleEpubDir=epub/TestEpub
        """.trimIndent())

        val entries = entriesOf(book, "epub-pkg.abmd.zip")

        for (expected in listOf(
            "epub/TestEpub/META-INF/container.xml",
            "epub/TestEpub/content.opf",
            "epub/TestEpub/optimized/000.xhtml.gz",
        )) {
            assertTrue("EPUB tree must include $expected; entries=$entries", entries.contains(expected))
        }
    }
}
