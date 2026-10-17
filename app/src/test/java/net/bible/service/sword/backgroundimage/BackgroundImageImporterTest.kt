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
package net.bible.service.sword.backgroundimage

import net.bible.service.sword.ContentSource
import org.junit.Assert.assertEquals
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.common.AndBibleAddons
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.source
import org.junit.Assert.assertNull

/**
 * A tiny placeholder byte array standing in for a 1x1 PNG. `copyAndRegister` never decodes the
 * image (only [addManuallyInstalledBackgroundImageBooks] scans by file extension), so the exact
 * bytes don't matter -- just the PNG signature prefix, mirroring `BackgroundImageBookTest`'s
 * plain `byteArrayOf(0x00, 0x01, 0x02, 0x03)` placeholder content.
 */
private fun onePixelPng(): ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackgroundImageImporterTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val dir get() = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR)
    private lateinit var tmp: File
    private var previousVersion = 0L
    private var previousDownloadDir: File? = null
    private lateinit var previousBooks: List<org.crosswire.jsword.book.Book>
    private val impl = TextDisplaySettingsServiceImpl()

    @Before
    fun setUp() {
        // Background-image modules declare AndBibleMinimumVersion=1112, so AndBibleAddonFilter only
        // exposes them on apps whose version is >= 1112 (mirrors BackgroundImageBookTest).
        previousVersion = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
        previousDownloadDir = SwordBookPathJvm.getDownloadDir()
        previousBooks = Books.installed().getBooks().toList()
        shadowOf(app.packageManager)
            .getInternalMutablePackageInfo(app.packageName)
            .longVersionCode = 100000L

        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        dir.mkdirs()
        tmp = File(SharedConstants.modulesDir, "importer-test-src").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        for (b in Books.installed().getBooks().filter { it.isBackgroundImageModule && it !in previousBooks }) {
            Books.installed().removeBook(b)
        }
        shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName).longVersionCode = previousVersion
        previousDownloadDir?.let { SwordBookPathJvm.setDownloadDir(it) }
        AndBibleAddons.clearCaches()
        dir.deleteRecursively()
        tmp.deleteRecursively()
    }

    private fun source(file: File) = object : ContentSource {
        override fun openSource(uri: String) = file.source()
        override fun displayName(uri: String) = "bg.png"
        override fun mimeType(uri: String) = "image/png"
    }

    @Test fun copyAndRegisterAddsModule() {
        val src = File(tmp, "hills.png").apply { writeBytes(onePixelPng()) }
        val out = BackgroundImageImporter.copyAndRegister(source(src), "content://test/image")
        assertNotNull(out)
        assertEquals("bg.png", out!!.name)
        assertTrue(src.readBytes().contentEquals(out.readBytes()))
        assertTrue(out!!.parentFile!!.name == BACKGROUND_IMAGE_DIR)
        AndBibleAddons.clearCaches()
        assertTrue(Books.installed().getBooks().any { it.isBackgroundImageModule && it.backgroundImageFile == out })
    }

    @Test fun deleteRemovesModuleAndFile() {
        val src = File(tmp, "sky.png").apply { writeBytes(onePixelPng()) }
        val out = BackgroundImageImporter.copyAndRegister(source(src), "content://test/image")!!
        AndBibleAddons.clearCaches()
        val initials = Books.installed().getBooks().first { it.isBackgroundImageModule && it.backgroundImageFile == out }.initials
        impl.deleteBackgroundImage(initials)
        assertFalse(out.exists())
        assertFalse(AndBibleAddons.providedBackgroundImages.containsKey(initials))
    }
    @Test fun missingExtensionUsesMimeAndDuplicateNamesDoNotOverwrite() {
        val bytes = onePixelPng()
        var closed = 0
        val source = object : ContentSource {
            override fun openSource(uri: String) = object : ForwardingSource(Buffer().write(bytes)) {
                override fun close() { closed++; super.close() }
            }
            override fun displayName(uri: String) = "bg"
            override fun mimeType(uri: String) = "image/png"
        }
        val first = BackgroundImageImporter.copyAndRegister(source, "content://test/image")!!
        val second = BackgroundImageImporter.copyAndRegister(source, "content://test/image")!!
        assertEquals("bg.png", first.name)
        assertEquals("bg_2.png", second.name)
        assertEquals(2, closed)
        assertTrue(bytes.contentEquals(first.readBytes()))
        assertTrue(bytes.contentEquals(second.readBytes()))
    }

    @Test fun absentInputDoesNotRegisterOrCreateAFile() {
        val before = dir.listFiles()!!.toSet()
        val source = object : ContentSource {
            override fun openSource(uri: String): Source? = null
            override fun displayName(uri: String) = "missing.png"
            override fun mimeType(uri: String) = "image/png"
        }
        assertNull(BackgroundImageImporter.copyAndRegister(source, "content://test/missing"))
        assertEquals(before, dir.listFiles()!!.toSet())
    }

}
