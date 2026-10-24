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
import okio.Path

/**
 * [AppDirectories] in the iOS sandbox. [root] is `Application Support/AndBible` (or a test directory) and
 * [documents] the Documents directory. Sword modules live in `documents/sword`, the same place as
 * `IosAppEnvironment.modulesDir`. iOS has no external storage, so the "manual install" directories are plain
 * folders under [root]; every directory is created when it is read.
 */
class IosAppDirectories(private val root: Path, private val documents: Path) : AppDirectories {
    private val files = FileSystem.SYSTEM

    private fun ensured(path: Path): Path = path.also { files.createDirectories(it) }

    override val internalFilesDir: Path get() = ensured(root)
    override val modulesDir: Path get() = ensured(documents / "sword")
    override val manualInstallDir: Path get() = ensured(root / "manual")
    override val manualInstallDir2: Path get() = ensured(root / "manual2")
    override val manualReadingPlanDir: Path get() = ensured(root / "manual" / "readingplan")
}
