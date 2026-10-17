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

package net.bible.service.sword

import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import net.bible.service.sword.backgroundimage.backgroundImageModulesDir
import net.bible.service.sword.csvprompt.csvPromptModulesDir
import net.bible.service.sword.epub.epubModulesDir
import net.bible.service.sword.mysword.mySwordModulesDir
import net.bible.service.sword.ttf.ttfModulesDir
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContextImpl
import java.io.File

/** The backend directory functions resolve through the AppDirectories port but name the same folders as before. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ModuleDirsTest {
    /** Makes `getExternalFilesDir` return null, so the internal "modules" folder is used (the discrete layout). */
    @Implements(className = "android.app.ContextImpl", isInAndroidSdk = false)
    class ShadowNoExternalFilesDir : ShadowContextImpl() {
        @Implementation
        override fun getExternalFilesDir(type: String?): File? = null
    }

    private fun assertAllDirs() {
        fun expected(sub: String) = File(SharedConstants.modulesDir, sub).toOkioPath()
        assertEquals(expected("ttf"), ttfModulesDir())
        assertEquals(expected("prompts"), csvPromptModulesDir())
        assertEquals(expected(BACKGROUND_IMAGE_DIR), backgroundImageModulesDir())
        assertEquals(expected("mysword"), mySwordModulesDir())
        assertEquals(expected("epub"), epubModulesDir())
    }

    @Test fun dirsMatchSharedConstantsInNormalAppearance() = assertAllDirs()

    @Test
    @Config(shadows = [ShadowNoExternalFilesDir::class])
    fun dirsMatchSharedConstantsWhenModulesLiveInInternalStorage() {
        assertEquals(null, BibleApplication.application.getExternalFilesDir(null))
        assertAllDirs()
        assertEquals(File(SharedConstants.internalFilesDir, "modules/ttf").toOkioPath(), ttfModulesDir())
    }
}
