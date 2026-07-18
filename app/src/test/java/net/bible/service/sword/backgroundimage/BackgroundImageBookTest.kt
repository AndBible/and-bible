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

import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.AndBibleAddons
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackgroundImageBookTest {
    private val dir get() = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR)

    @Before
    fun setUp() {
        // Background-image modules declare AndBibleMinimumVersion=1112, so AndBibleAddonFilter only
        // exposes them on apps whose version is >= 1112. Raise the reported app version well past that
        // threshold so addonsRegistryExposesInstalledImage validates the addon-registry contract
        // regardless of the current manifest versionCode.
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager)
            .getInternalMutablePackageInfo(app.packageName)
            .longVersionCode = 100000L

        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        for (b in Books.installed().books.filter { it.isBackgroundImageModule }) {
            Books.installed().removeBook(b)
        }
        dir.deleteRecursively()
    }

    @Test
    fun registersModuleFromImageFile() {
        File(dir, "sunset.jpg").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        addManuallyInstalledBackgroundImageBooks()

        val book = Books.installed().books.firstOrNull { it.isBackgroundImageModule }
        assertNotNull("A background-image module should register", book)
        assertTrue(book!!.initials.startsWith("BGIMG_"))
        assertEquals("sunset.jpg", book.backgroundImageFile.name)
        assertTrue(book.backgroundImageFile.exists())
    }

    @Test
    fun rescanIsIdempotent() {
        File(dir, "sunset.jpg").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        addManuallyInstalledBackgroundImageBooks()
        addManuallyInstalledBackgroundImageBooks()
        val count = Books.installed().books.count { it.isBackgroundImageModule }
        assertEquals("re-scanning the same image must not register duplicates", 1, count)
    }

    @Test
    fun addonsRegistryExposesInstalledImage() {
        File(dir, "hills.png").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        addManuallyInstalledBackgroundImageBooks()
        AndBibleAddons.clearCaches()

        val book = Books.installed().books.first { it.isBackgroundImageModule }
        val provided = AndBibleAddons.providedBackgroundImages[book.initials]
        assertNotNull("registry should expose the image by module initials", provided)
        assertEquals("hills", provided!!.name)
        assertTrue(AndBibleAddons.backgroundImageModuleNames.contains(book.initials))
    }

    @Test
    fun initialsAreSanitizedAndDeduped() {
        val existing = mutableSetOf("BGIMG_my_photo")
        val a = backgroundImageModuleInitials("my photo") { it in existing }
        assertEquals("BGIMG_my_photo_2", a)
        assertFalse("initials must be URL-safe", Regex("[^A-Za-z0-9_]").containsMatchIn(a))
    }
}
