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

package net.bible.service.sword.epub

import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.test.testCoreStrings
import net.bible.android.platform.AndroidAppEnvironment
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EpubDiscoveryTest {
    private val epubDir get() = File(SharedConstants.modulesDir, "epub")

    @After fun tearDown() { epubDir.delete() }

    /** File.mkdirs() returned false when the folder could not be created; startup must not crash on it. */
    @Test fun uncreatableEpubFolderReturnsFalseWithoutThrowing() {
        epubDir.deleteRecursively()
        SharedConstants.modulesDir.mkdirs()
        epubDir.writeText("not a directory")
        assertFalse(addManuallyInstalledEpubBooks(testCoreStrings(), AndroidAppEnvironment(RuntimeEnvironment.getApplication())))
    }
}
