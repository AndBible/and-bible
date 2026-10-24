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
package net.bible.sharedcore.platform

import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosAppDirectoriesTest {
    private val base = (NSTemporaryDirectory().trimEnd('/') + "/dirs-" + NSUUID().UUIDString).toPath()
    private val directories = IosAppDirectories(base / "support", base / "documents")

    @AfterTest
    fun tearDown() = FileSystem.SYSTEM.deleteRecursively(base)

    @Test
    fun everyDirectoryIsCreatedWhenReadInAFreshSandbox() {
        val fs = FileSystem.SYSTEM
        assertTrue(!fs.exists(base))
        listOf(directories.internalFilesDir, directories.modulesDir, directories.manualInstallDir,
            directories.manualInstallDir2, directories.manualReadingPlanDir).forEach {
            assertTrue(fs.metadata(it).isDirectory, "$it is not a directory")
        }
    }

    @Test
    fun layoutMatchesTheSpec() {
        assertEquals(base / "support", directories.internalFilesDir)
        assertEquals(base / "documents" / "sword", directories.modulesDir)
        assertEquals(base / "support" / "manual", directories.manualInstallDir)
        assertEquals(base / "support" / "manual2", directories.manualInstallDir2)
        assertEquals(base / "support" / "manual" / "readingplan", directories.manualReadingPlanDir)
    }
}
