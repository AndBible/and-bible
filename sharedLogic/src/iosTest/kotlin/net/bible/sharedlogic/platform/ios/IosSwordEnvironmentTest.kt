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
package net.bible.sharedlogic.platform.ios

import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosSwordEnvironmentTest {
    private val fs = FileSystem.SYSTEM
    private val root = (NSTemporaryDirectory().trimEnd('/') + "/sword-env-" + NSUUID().UUIDString).toPath()

    @AfterTest fun cleanUp() = fs.deleteRecursively(root)

    @Test fun createModuleFoldersCreatesTheWholeLayoutFromAnEmptyDirectory() {
        val modules = root / "Documents" / "sword"
        assertFalse(fs.exists(root), "the temp root starts empty")

        IosSwordEnvironment.createModuleFolders(modules)

        assertEquals(
            setOf("mods.d", "modules", "search"),
            fs.list(modules).map { it.name }.toSet(),
        )
        for (child in fs.list(modules)) assertTrue(fs.metadata(child).isDirectory, "$child is not a directory")
    }

    @Test fun createModuleFoldersKeepsExistingContent() {
        val modules = root / "sword"
        fs.createDirectories(modules / "mods.d")
        fs.write(modules / "mods.d" / "x.conf") { writeUtf8("[X]") }

        IosSwordEnvironment.createModuleFolders(modules)
        IosSwordEnvironment.createModuleFolders(modules) // idempotent

        assertEquals("[X]", fs.read(modules / "mods.d" / "x.conf") { readUtf8() })
    }
}
