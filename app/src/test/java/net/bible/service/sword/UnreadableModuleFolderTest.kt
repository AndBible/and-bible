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

import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.backgroundimage.backgroundImageModulesDir
import net.bible.service.sword.mybible.addManuallyInstalledMyBibleBooks
import net.bible.service.sword.mybible.myBibleModulesDir
import net.bible.service.sword.mysword.addManuallyInstalledMySwordBooks
import net.bible.service.sword.mysword.mySwordModulesDir
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import net.bible.service.sword.ttf.ttfModulesDir
import okio.Path
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import java.io.File

/**
 * Startup scans of the manually installed module folders (called from `CommonUtils.initializeApp` without a
 * catch) must stay graceful when a folder exists but cannot be read: `File.canRead` guarded this before okio,
 * whose `listRecursively` throws on an unreadable root.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class UnreadableModuleFolderTest {
    private val unreadable = mutableListOf<File>()

    @Before
    fun setUp() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
    }

    @After
    fun tearDown() {
        unreadable.forEach { it.setReadable(true); it.setExecutable(true); it.deleteRecursively() }
    }

    private fun withUnreadable(dir: Path, scan: () -> Unit) {
        val f = dir.toFile()
        f.mkdirs()
        unreadable.add(f)
        f.setReadable(false)
        f.setExecutable(false)
        assumeFalse("running as a user that bypasses file permissions", f.canRead())
        scan() // must not throw
    }

    @Test fun ttfScanIgnoresUnreadableFolder() = withUnreadable(ttfModulesDir()) { addManuallyInstalledTtfBooks() }

    @Test fun backgroundImageScanIgnoresUnreadableFolder() =
        withUnreadable(backgroundImageModulesDir()) { addManuallyInstalledBackgroundImageBooks() }

    @Test fun myBibleScanIgnoresUnreadableFolder() = withUnreadable(myBibleModulesDir()) { addManuallyInstalledMyBibleBooks() }

    @Test fun mySwordScanIgnoresUnreadableFolder() = withUnreadable(mySwordModulesDir()) { addManuallyInstalledMySwordBooks() }
}
