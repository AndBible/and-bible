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
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.backgroundimage.isBackgroundImageModule
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import net.bible.service.sword.ttf.isManuallyInstalledTtf
import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Round-trip tests for the shared module backup/restore core
 * ([BackupControl.createSingleModuleZip] → [BackupControl.installModuleArchive]).
 *
 * These guard against regressions in the packaging/extraction logic that is now shared
 * between three callers — the sideload `InstallZip` flow, the "Backup modules" feature,
 * and cloud document-sync — so a change to one path can't silently break the others.
 *
 * The extraction target ([SwordBookPath.getSwordDownloadDir]) is pinned to
 * [SharedConstants.modulesDir] in [setUp] so that restored files land where the
 * manually-installed rescans (TTF/MyBible/EPUB) look for them; on a real device the JSword
 * home is configured to the same directory, but the download dir is otherwise computed once
 * at class-load and may capture a stale path under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ModuleBackupRoundTripTest {
    @Before
    fun setUp() {
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
    }

    @After
    fun tearDown() {
        // Books.installed() is a process-global singleton; drop anything we registered so it
        // can't leak into other tests sharing the same JVM.
        for (initials in listOf("TTF_TestFont", "TestDict")) {
            Books.installed().getBook(initials)?.let { Books.installed().removeBook(it) }
        }
        File(SharedConstants.modulesDir, "ttf").deleteRecursively()
        File(SharedConstants.modulesDir, "mods.d/FontPack.conf").delete()
        for (b in Books.installed().books.filter { it.isBackgroundImageModule }) {
            Books.installed().removeBook(b)
        }
        File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR).deleteRecursively()
    }

    /**
     * A TTF font is a real (non-pseudo) [org.crosswire.jsword.book.Book] backed by
     * byte-array-constructed metadata, so it reaches [BackupControl.createSingleModuleZip].
     * Regression guard for the NPE that occurred when such books fell into the generic SWORD
     * branch (whose `bmd.configFile` is null for byte-array metadata). Packaging must instead
     * archive the underlying `.ttf` file, and restore must re-register it.
     */
    @Test
    fun ttfFontRoundTrips() = runBlocking {
        val ttfDir = File(SharedConstants.modulesDir, "ttf").apply { mkdirs() }
        File(ttfDir, "TestFont.ttf").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        addManuallyInstalledTtfBooks()
        val book = Books.installed().getBook("TTF_TestFont")
        assertNotNull("TTF font should register from modulesDir/ttf", book)

        val zipFile = File(CommonUtils.tmpDir, "ttf-roundtrip.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)

        ZipFile(zipFile).use { zf ->
            assertNotNull(
                "Packaged zip must contain the font file at its modulesDir-relative path",
                zf.getEntry("ttf/TestFont.ttf")
            )
        }

        // Simulate restoring onto a device that doesn't have the font yet.
        ttfDir.deleteRecursively()
        Books.installed().removeBook(book)
        assertNull(Books.installed().getBook("TTF_TestFont"))

        val installed = BackupControl.installModuleArchive(zipFile, "TTF_TestFont")
        assertTrue("TTF font should reinstall from the backup archive", installed)
        assertNotNull(Books.installed().getBook("TTF_TestFont"))
    }

    /**
     * A background-image module is, like a manually-installed TTF, a real (non-pseudo) [Book]
     * with byte-array-constructed metadata (no on-disk `.conf`), so it reaches
     * [BackupControl.createSingleModuleZip] but has a null `configFile`. Packaging must archive the
     * underlying image file rather than falling into the generic SWORD branch, and restore must
     * re-register it. This exercises the exact backup/sync path document-sync uses
     * ([BackupControl.createSingleModuleZip] → [BackupControl.installModuleArchive]).
     */
    @Test
    fun backgroundImageRoundTrips() = runBlocking {
        val imgDir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR).apply { mkdirs() }
        File(imgDir, "sunset.jpg").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        addManuallyInstalledBackgroundImageBooks()
        val book = Books.installed().books.first { it.isBackgroundImageModule }
        val initials = book.initials

        val zipFile = File(CommonUtils.tmpDir, "bgimg-roundtrip.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)

        ZipFile(zipFile).use { zf ->
            assertNotNull(
                "packaged zip must contain the image at its modulesDir-relative path",
                zf.getEntry("$BACKGROUND_IMAGE_DIR/sunset.jpg")
            )
        }

        imgDir.deleteRecursively()
        Books.installed().removeBook(book)
        assertNull(Books.installed().getBook(initials))

        val installed = BackupControl.installModuleArchive(zipFile, initials)
        assertTrue("background image should reinstall from the archive", installed)
        assertNotNull(Books.installed().getBook(initials))
    }

    /**
     * A downloaded font add-on module (e.g. "FontPack") carries the same `AndBibleProvidesFont`
     * property as a manually-installed `.ttf`, but is a real SWORD module with an on-disk `.conf`
     * and font files under its DataPath dir. It must be packaged via the generic SWORD branch
     * (conf + whole data dir), NOT the single-file TTF branch — which would package only the first
     * font and crash with FileNotFoundException when its hard-coded `ttfFile` path doesn't exist
     * (OSTicket #3351: "Document sync op failed: FontPack").
     *
     * Regression guard: such a module must (a) not be classified as a manually-installed TTF, and
     * (b) package its conf plus every font file without throwing — even when one referenced font
     * file is missing on disk.
     */
    @Test
    fun fontPackAddonPackagesViaSwordBranchNotTtfFile() = runBlocking {
        val downloadDir = SwordBookPath.getSwordDownloadDir()

        // Font files live under the DataPath dir (./ttf/), in an `and-bible/` subdir as real
        // FontPack modules ship them. One font exists; a second is referenced by the conf's
        // AndBibleProvidesFont but intentionally absent on disk.
        val fontDir = File(downloadDir, "ttf/and-bible").apply { mkdirs() }
        File(fontDir, "SILEOTSR.ttf").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        val conf = """
            [FontPack]
            Description=And Bible Font Pack
            Category=And Bible
            ModDrv=RawGenBook
            DataPath=./ttf/
            Encoding=UTF-8
            AndBibleProvidesFont=SIL Ezra SR;and-bible/SILEOTSR.ttf
            AndBibleProvidesFont=Missing Font;and-bible/MISSING.ttf
        """.trimIndent()
        val confFile = File(downloadDir, "mods.d/FontPack.conf").apply {
            parentFile!!.mkdirs()
            writeText(conf)
        }

        // File-based metadata → configFile != null, the distinguisher from a manual TTF.
        val bmd = SwordBookMetaData(confFile, NetUtil.getURI(downloadDir))
        val book: Book = SwordBook(bmd, NullBackend())

        assertFalse(
            "A FontPack add-on with an on-disk .conf must NOT be treated as a manually-installed TTF",
            book.isManuallyInstalledTtf
        )

        val zipFile = File(CommonUtils.tmpDir, "fontpack-pkg.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)

        ZipFile(zipFile).use { zf ->
            val entries = zf.entries().toList().map { it.name }.toSet()
            assertTrue("conf must be packaged; entries=$entries", entries.contains("mods.d/FontPack.conf"))
            assertTrue(
                "existing font file must be packaged via the data dir; entries=$entries",
                entries.contains("ttf/and-bible/SILEOTSR.ttf")
            )
        }
    }

    /**
     * Full round-trip of a regular SWORD module. Uses a minimal RawLD dictionary because it is
     * the simplest valid module to fabricate (two uncompressed data files, no versification
     * index). A dictionary also exercises the DICTIONARY/GENERAL_BOOK/MAPS special-casing in
     * `addBookToZip`, where the data dir walked is the parent of the DataPath file prefix.
     *
     * Asserts: registration works, the packaged zip leads with the manifest entry and contains
     * the conf plus both data files, and reinstalling from the archive re-registers the module
     * with its content intact.
     */
    @Test
    fun rawLdDictionaryRoundTrips() = runBlocking {
        val book = registerMinimalRawLdDictionary()
        assertNotNull("Fabricated RawLD dictionary should register", book)
        assertTrue(
            "Dictionary entry should be readable",
            book.getRawText(book.getKey("strong")).contains("The test definition body.")
        )

        val zipFile = File(CommonUtils.tmpDir, "rawld-roundtrip.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            assertEquals(
                "Manifest must be the first zip entry (AndBibleBackupManifest.fromUri reads it first)",
                ANDBIBLE_BACKUP_MANIFEST_FILENAME, zis.nextEntry?.name
            )
        }
        ZipFile(zipFile).use { zf ->
            assertNotNull("conf must be packaged", zf.getEntry("mods.d/testdict.conf"))
            assertNotNull("index data must be packaged", zf.getEntry("modules/lexdict/rawld/testdict/test.idx"))
            assertNotNull("entry data must be packaged", zf.getEntry("modules/lexdict/rawld/testdict/test.dat"))
        }

        // Wipe the on-disk module + registration, then restore purely from the archive.
        val downloadDir = SwordBookPath.getSwordDownloadDir()
        File(downloadDir, "mods.d/testdict.conf").delete()
        File(downloadDir, "modules/lexdict/rawld/testdict").deleteRecursively()
        Books.installed().removeBook(book)
        assertNull(Books.installed().getBook("TestDict"))

        val installed = BackupControl.installModuleArchive(zipFile, "TestDict")
        assertTrue("Dictionary should reinstall from the backup archive", installed)
        val restored = Books.installed().getBook("TestDict")
        assertNotNull(restored)
        assertTrue(
            "Restored module content must be intact",
            restored.getRawText(restored.getKey("strong")).contains("The test definition body.")
        )
    }

    /**
     * Writes a minimal single-entry RawLD (2-byte) dictionary into the SWORD download dir and
     * registers it the same way restore does (file-based [SwordBookMetaData] +
     * [SwordBookDriver.registerNewBook]), returning the registered book.
     *
     * RawLD layout: `.dat` holds `key\nbody`; `.idx` holds one 6-byte record =
     * uint32-LE offset + uint16-LE length, both little-endian.
     */
    private fun registerMinimalRawLdDictionary(): Book {
        val downloadDir = SwordBookPath.getSwordDownloadDir()
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

        val confFile = File(downloadDir, "mods.d/testdict.conf")
        confFile.parentFile!!.mkdirs()
        confFile.writeText(conf)

        val dataDir = File(downloadDir, "modules/lexdict/rawld/testdict").apply { mkdirs() }
        val datBytes = "strong\nThe test definition body.".toByteArray(Charsets.UTF_8)
        File(dataDir, "test.dat").writeBytes(datBytes)
        val idxBytes = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putShort(datBytes.size.toShort()).array()
        File(dataDir, "test.idx").writeBytes(idxBytes)

        val bmd = SwordBookMetaData(confFile, NetUtil.getURI(downloadDir))
        bmd.driver = SwordBookDriver.instance()
        SwordBookDriver.registerNewBook(bmd)
        return Books.installed().getBook("TestDict")
    }
}
