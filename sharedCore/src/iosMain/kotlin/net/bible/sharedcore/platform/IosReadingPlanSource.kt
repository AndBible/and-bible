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

import net.bible.sharedcore.readingplan.ReadingPlanSource
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle

/**
 * The reading plans bundled with the app, read from [bundleDir] (the app bundle's `readingplan` folder, filled by
 * the Xcode project step from `app/src/main/assets/readingplan`; the folder name is `SharedConstants.READINGPLAN_DIR_NAME`).
 * Same contract as `AndroidReadingPlanSource`: codes are the `*.properties` file names without the suffix and the
 * text is the file's bytes decoded as ISO-8859-1.
 */
class IosReadingPlanSource(
    private val bundleDir: String = NSBundle.mainBundle.resourcePath + "/readingplan",
    private val files: FileSystem = FileSystem.SYSTEM,
) : ReadingPlanSource {
    override fun builtInPlanCodes(): List<String> =
        try {
            files.list(bundleDir.toPath()).map { it.name }.filter { it.endsWith(DOT_PROPERTIES) }.sorted()
                .map { it.replace(DOT_PROPERTIES, "") }
        } catch (e: IOException) {
            emptyList()
        }

    override fun openBuiltInPlan(code: String): String? =
        try {
            // Every byte maps to the char of the same value: lossless ISO-8859-1.
            files.read((bundleDir.toPath() / (code + DOT_PROPERTIES))) { readByteArray() }
                .let { bytes -> CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() }.concatToString() }
        } catch (e: IOException) {
            null
        }

    private companion object {
        const val DOT_PROPERTIES = ".properties"
    }
}
